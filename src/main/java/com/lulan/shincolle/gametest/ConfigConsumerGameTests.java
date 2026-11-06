package com.lulan.shincolle.gametest;

import com.lulan.shincolle.capability.CapaTeitoku;
import com.lulan.shincolle.config.ConfigMining;
import com.lulan.shincolle.entity.BasicEntityShipHostile;
import com.lulan.shincolle.handler.ConfigHandler;
import com.lulan.shincolle.init.ModBlocks;
import com.lulan.shincolle.reference.Reference;
import com.lulan.shincolle.tileentity.TileEntitySmallShipyard;
import com.lulan.shincolle.tileentity.TileEntityVolCore;
import com.lulan.shincolle.tileentity.TileMultiGrudgeHeavy;
import com.lulan.shincolle.utility.EntityHelper;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.configurations.DiskConfiguration;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.config.ConfigTracker;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ConfigConsumerGameTests {
    private ConfigConsumerGameTests() { }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "config_consumers")
    public static void orePlacementReadsLiveCount(GameTestHelper helper) {
        verifyCount(helper, false, 20);
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "config_consumers")
    public static void gravelPlacementReadsLiveCount(GameTestHelper helper) {
        verifyCount(helper, true, 4);
    }

    private static void verifyCount(GameTestHelper helper, boolean gravel, int defaultCount) {
        String name = gravel ? "polymetal_gravel" : "polymetal_ore";
        String key = gravel ? "polymetalGravel" : "polymetalOre";
        ForgeConfigSpec.IntValue value = ConfigHandler.COMMON_SPEC.getValues().get("world." + key + "Clusters");
        int original = value.get();
        var placed = helper.getLevel().registryAccess().registryOrThrow(Registries.PLACED_FEATURE)
                .get(new ResourceLocation(Reference.MOD_ID, name));
        helper.assertTrue(placed != null, "Production placed feature must load: " + name);
        var count = placed.placement().get(0);
        try {
            for (int expected : new int[]{0, 3, defaultCount}) {
                value.set(expected);
                helper.assertTrue(value.get() == expected, "Worldgen count fixture must apply");
                long actual = count.getPositions(null, RandomSource.create(19), BlockPos.ZERO).count();
                helper.assertTrue(actual == expected, name + " live count expected " + expected + ", got " + actual);
            }
            helper.assertTrue(value.getDefault() == defaultCount, "Worldgen effective default must be preserved");
        } finally {
            value.set(original);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "config_consumers")
    public static void gravelTargetReadsEveryReplacementFlag(GameTestHelper helper) {
        var configured = helper.getLevel().registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE)
                .get(new ResourceLocation(Reference.MOD_ID, "polymetal_gravel"));
        var config = (DiskConfiguration) configured.config();
        boolean[] original = ConfigHandler.polyGravelBaseBlock.clone();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
        BlockState previous = helper.getLevel().getBlockState(pos);
        Block[] blocks = {Blocks.STONE, Blocks.GRAVEL, Blocks.SAND, Blocks.DIRT, Blocks.DEEPSLATE};
        try {
            for (int enabled = -1; enabled < 4; enabled++) {
                Arrays.fill(ConfigHandler.polyGravelBaseBlock, false);
                if (enabled >= 0) ConfigHandler.polyGravelBaseBlock[enabled] = true;
                for (int i = 0; i < blocks.length; i++) {
                    helper.getLevel().setBlock(pos, blocks[i].defaultBlockState(), 2);
                    helper.assertTrue(helper.getLevel().getBlockState(pos).is(blocks[i]), "Target fixture must apply");
                    helper.assertTrue(config.target().test(helper.getLevel(), pos) == (i == enabled),
                            "Gravel replacement flag " + enabled + " must select only its block, tested " + blocks[i]);
                }
            }
        } finally {
            System.arraycopy(original, 0, ConfigHandler.polyGravelBaseBlock, 0, original.length);
            helper.getLevel().setBlock(pos, previous, 2);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "config_consumers")
    public static void newMiningDefaultsYieldPolymetal(GameTestHelper helper) throws Exception {
        File original = miningFile();
        Path directory = Files.createTempDirectory(Path.of("build"), "mining-default-");
        Path file = directory.resolve("mining.cfg");
        try {
            ConfigMining.load(file.toFile());
            for (String biome : List.of("plains", "ocean", "deep_ocean")) {
                var entries = ConfigMining.getEntries("overworld", biome);
                long count = entries.stream().filter(e -> e.itemName().equals("shincolle_kai:polymetal_nodule")).count();
                helper.assertTrue(count == (biome.equals("plains") ? 1 : 2),
                        "Mining defaults must supply polymetal in " + biome + ", got " + count);
                helper.assertTrue(entries.stream().noneMatch(e -> e.itemName().equals("shincolle_kai:abyss_metal")),
                        "No upstream meta0 defaults exist to retain as abyss metal");
            }
        } finally {
            if (original != null) ConfigMining.load(original);
            Files.deleteIfExists(file);
            Files.delete(directory);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "config_consumers")
    public static void miningMigrationChangesOnlyExactDefaults(GameTestHelper helper) throws Exception {
        File original = miningFile();
        Path directory = Files.createTempDirectory(Path.of("build"), "mining-migration-");
        Path file = directory.resolve("mining.cfg");
        String general = "overworld,*,shincolle_kai:abyss_metal,350,1,3,1,64,2,100";
        String ocean = "overworld,ocean,shincolle_kai:abyss_metal,500,1,3,1,64,2,100";
        String deep = "overworld,deep_ocean,shincolle_kai:abyss_metal,500,1,3,1,64,2,100";
        String unchanged = "# custom 日本語\r\n" + general.replace(",350,", ",351,") + "\n " + ocean
                + "\r\n" + deep.replace("deep_ocean", "river");
        String before = general + "\r\n" + ocean + "\n" + deep + "\r\n" + unchanged;
        String expected = general.replace("abyss_metal", "polymetal_nodule") + "\r\n"
                + ocean.replace("abyss_metal", "polymetal_nodule") + "\n"
                + deep.replace("abyss_metal", "polymetal_nodule") + "\r\n" + unchanged;
        try {
            Files.writeString(file, before, StandardCharsets.UTF_8);
            ConfigMining.load(file.toFile());
            helper.assertTrue(Files.readString(file, StandardCharsets.UTF_8).equals(expected),
                    "Migration must replace only exact legacy defaults and preserve other bytes and line endings");
            helper.assertTrue(ConfigMining.getEntries("overworld", "plains").stream()
                            .anyMatch(e -> e.itemName().equals("shincolle_kai:polymetal_nodule") && e.weight() == 350),
                    "Migrated entries must be effective in the same load");
            ConfigMining.load(file.toFile());
            helper.assertTrue(Files.readString(file, StandardCharsets.UTF_8).equals(expected),
                    "Migration must be idempotent");
        } finally {
            if (original != null) ConfigMining.load(original);
            Files.deleteIfExists(file);
            Files.delete(directory);
        }
        helper.succeed();
    }

    private static File miningFile() throws Exception {
        Field field = ConfigMining.class.getDeclaredField("configFile");
        field.setAccessible(true);
        return (File) field.get(null);
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "config_consumers")
    public static void zeroHostileGroupsDoNotSpawn(GameTestHelper helper) {
        verifySpawn(helper, 0);
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "config_consumers")
    public static void negativeHostileGroupsDoNotSpawn(GameTestHelper helper) {
        verifySpawn(helper, -1);
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "config_consumers")
    public static void defaultHostileGroupStillSpawns(GameTestHelper helper) {
        verifySpawn(helper, 1);
    }

    private static void verifySpawn(GameTestHelper helper, int groups) {
        BlockPos origin = helper.absolutePos(new BlockPos(1, 2, 1));
        BlockPos playerPos = new BlockPos((origin.getX() & ~15) + 8, 72, (origin.getZ() & ~15) + 8);
        RandomSource predicted = RandomSource.create(493);
        predicted.nextInt(100);
        int offX = predicted.nextInt(30) + 20;
        int offZ = predicted.nextInt(30) + 20;
        int quadrant = predicted.nextInt(4);
        BlockPos water = new BlockPos(playerPos.getX() + (quadrant == 1 || quadrant == 3 ? -offX : offX),
                helper.getLevel().getSeaLevel() - 2, playerPos.getZ() + (quadrant == 1 || quadrant == 2 ? -offZ : offZ));
        Vec3 base = helper.absoluteVec(Vec3.ZERO);
        GameTestEntities.whenPositionsTicking(helper, () -> verifySpawnNow(helper, groups, playerPos, water),
                Vec3.atCenterOf(playerPos).subtract(base), Vec3.atCenterOf(water).subtract(base));
    }

    private static void verifySpawnNow(GameTestHelper helper, int groups, BlockPos playerPos, BlockPos water) {
        var level = helper.getLevel();
        FakePlayer player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "config_spawn"));
        player.moveTo(playerPos.getX() + 0.5, playerPos.getY() + 0.5, playerPos.getZ() + 0.5, 0, 0);
        CapaTeitoku capa = new CapaTeitoku();
        capa.setHasRing(true);
        var section = level.getChunkAt(playerPos).getSection(level.getSectionIndex(playerPos.getY()));
        var originalBiomes = section.getBiomes();
        var sampler = level.getChunkSource().randomState().sampler();
        var ocean = level.registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(Biomes.OCEAN);
        int[] original = ConfigHandler.mobSpawn.clone();
        Difficulty difficulty = level.getDifficulty();
        List<Entity> existing = new ArrayList<>();
        level.getAllEntities().forEach(existing::add);
        BlockState oldWater = level.getBlockState(water);
        BlockState oldAbove = level.getBlockState(water.above());
        try {
            section.fillBiomesFromNoise((x, y, z, s) -> ocean, sampler, 0, 0, 0);
            level.getServer().setDifficulty(Difficulty.NORMAL, true);
            ConfigHandler.mobSpawn = new int[]{Integer.MAX_VALUE, 100, groups, 1, 1};
            level.setBlock(water, Blocks.WATER.defaultBlockState(), 2);
            level.setBlock(water.above(), Blocks.AIR.defaultBlockState(), 2);
            helper.assertTrue(level.getBiome(player.blockPosition()).is(BiomeTags.IS_OCEAN)
                            && capa.hasRing() && ConfigHandler.mobSpawn[2] == groups,
                    "Spawn fixture must satisfy sea, ring and configured group count");
            player.getRandom().setSeed(493);
            EntityHelper.spawnMobShip(player, capa);
            List<Entity> spawned = new ArrayList<>();
            level.getAllEntities().forEach(e -> {
                if (!existing.contains(e) && e instanceof BasicEntityShipHostile) spawned.add(e);
            });
            helper.assertTrue(spawned.size() == (groups > 0 ? 1 : 0),
                    "Configured groups " + groups + " must spawn " + (groups > 0 ? 1 : 0) + ", got " + spawned.size());
        } finally {
            List<Entity> cleanup = new ArrayList<>();
            level.getAllEntities().forEach(e -> {
                if (!existing.contains(e) && e instanceof BasicEntityShipHostile) cleanup.add(e);
            });
            cleanup.forEach(Entity::discard);
            ConfigHandler.mobSpawn = original;
            level.getServer().setDifficulty(difficulty, true);
            section.fillBiomesFromNoise((x, y, z, s) -> originalBiomes.get(x & 3, y & 3, z & 3), sampler, 0, 0, 0);
            level.setBlock(water, oldWater, 2);
            level.setBlock(water.above(), oldAbove, 2);
            player.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "config_consumers")
    public static void smallShipyardReloadChangesExistingAndNewBuildTicks(GameTestHelper helper) {
        var value = ConfigHandler.COMMON.tileShipyardSmallConfig;
        var original = value.get();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
        var state = ModBlocks.SMALL_SHIPYARD.get().defaultBlockState();
        var existing = new TileEntitySmallShipyard(pos, state);
        existing.setLevel(helper.getLevel());
        try {
            value.set(List.of(600000D, 96D, 2D));
            fireReload();
            helper.assertTrue(ConfigHandler.tileShipyardSmall[1] == 96, "Reload must sync the config array");
            var fresh = new TileEntitySmallShipyard(pos, state);
            fresh.setLevel(helper.getLevel());
            for (var tile : List.of(existing, fresh)) {
                tile.setBuildType(1);
                for (int i = 0; i < 4; i++) {
                    tile.setMatBuild(i, 100);
                    tile.setMatStock(i, 1000);
                }
                tile.setPowerRemained(10000);
                TileEntitySmallShipyard.serverTick(helper.getLevel(), pos, state, tile);
                helper.assertTrue(tile.getPowerConsumed() == 96 && tile.getPowerRemained() == 9904,
                        "Existing/new shipyard must use reloaded BUILD_SPEED 96, got " + tile.getPowerConsumed());
                helper.assertTrue(tile.getPowerMax() == 600000 && tile.getFuelMagni() == 2,
                        "Shipyard capacity and fuel multiplier must also refresh");
            }
        } finally {
            value.set(original);
            fireReload();
            TileEntitySmallShipyard.reloadConfig();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "config_consumers")
    public static void heavyShipyardReloadRefreshesAllEffectiveValues(GameTestHelper helper) throws Exception {
        var value = ConfigHandler.COMMON.tileShipyardLargeConfig;
        var original = value.get();
        // Force class initialization before the reload under test.
        TileMultiGrudgeHeavy.reloadConfig();
        try {
            value.set(List.of(1600000D, 97D, 3D));
            fireReload();
            helper.assertTrue(ConfigHandler.tileShipyardLarge[1] == 97, "Heavy config fixture must sync");
            assertStatic(helper, TileMultiGrudgeHeavy.class, "POWER_MAX", 1600000);
            assertStatic(helper, TileMultiGrudgeHeavy.class, "BUILD_SPEED", 97);
            assertStatic(helper, TileMultiGrudgeHeavy.class, "FUEL_MAGN", 3);
        } finally {
            value.set(original);
            fireReload();
            TileMultiGrudgeHeavy.reloadConfig();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "config_consumers")
    public static void volcanoReloadRefreshesAllEffectiveValues(GameTestHelper helper) throws Exception {
        var value = ConfigHandler.COMMON.tileVolCoreConfig;
        var original = value.get();
        TileEntityVolCore.reloadConfig();
        try {
            value.set(List.of(700000D, 17D, 2000D));
            fireReload();
            helper.assertTrue(ConfigHandler.tileVolCore[1] == 17, "Volcano config fixture must sync");
            assertStatic(helper, TileEntityVolCore.class, "POWER_MAX", 700000);
            assertStatic(helper, TileEntityVolCore.class, "CONSUMED_SPEED", 17);
            assertStatic(helper, TileEntityVolCore.class, "FUEL_VALUE", 2000);
        } finally {
            value.set(original);
            fireReload();
            TileEntityVolCore.reloadConfig();
        }
        helper.succeed();
    }

    private static void assertStatic(GameTestHelper helper, Class<?> type, String name, double expected) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        double actual = ((Number) field.get(null)).doubleValue();
        helper.assertTrue(actual == expected, type.getSimpleName() + "." + name + " expected " + expected + ", got " + actual);
    }

    private static void fireReload() {
        ModConfig config = ConfigTracker.INSTANCE.configSets().get(ModConfig.Type.COMMON).stream()
                .filter(c -> c.getSpec() == ConfigHandler.COMMON_SPEC).findFirst().orElseThrow();
        ModList.get().getModContainerById(Reference.MOD_ID).orElseThrow()
                .dispatchConfigEvent(new ModConfigEvent.Reloading(config));
    }
}
