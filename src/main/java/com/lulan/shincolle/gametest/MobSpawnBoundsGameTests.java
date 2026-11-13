package com.lulan.shincolle.gametest;

import com.lulan.shincolle.capability.CapaTeitoku;
import com.lulan.shincolle.entity.BasicEntityShipHostile;
import com.lulan.shincolle.handler.ConfigHandler;
import com.lulan.shincolle.reference.Reference;
import com.lulan.shincolle.utility.EntityHelper;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MobSpawnBoundsGameTests {
    private MobSpawnBoundsGameTests() { }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "mob_spawn_bounds")
    public static void hostileSpawnProbabilityMatchesPercent(GameTestHelper helper) {
        verify(helper, fixture -> {
            fixture.check(new int[]{50, 0, 1, 1, 1}, 0, 0);
            fixture.check(new int[]{50, 100, 1, 1, 1}, 99, 1);
            fixture.clearSpawned();
            for (int roll = 0; roll < 100; roll++) {
                fixture.check(new int[]{50, 10, 1, 1, 1}, roll, roll < 10 ? 1 : 0);
                fixture.clearSpawned();
            }
            fixture.check(new int[]{50, -1, 1, 1, 1}, 0, 0);
            fixture.check(new int[]{50, Integer.MIN_VALUE, 1, 1, 1}, 0, 0);
            fixture.check(new int[]{50, Integer.MAX_VALUE, 1, 1, 1}, 99, 1);
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "mob_spawn_bounds")
    public static void hostileGroupsRespectRemainingCapacity(GameTestHelper helper) {
        verify(helper, fixture -> {
            fixture.check(new int[]{1, 100, 1, 2, 2}, 0, 1);
            fixture.check(new int[]{1, 100, 1, 2, 2}, 0, 0);
            fixture.clearSpawned();
            fixture.check(new int[]{3, 100, 2, 2, 2}, 0, 3);
            fixture.check(new int[]{2, 100, 2, 2, 2}, 0, 0);
            fixture.clearSpawned();
            fixture.check(new int[]{0, 100, 1, 2, 2}, 0, 0);
            fixture.check(new int[]{-1, 100, 1, 2, 2}, 0, 0);
            fixture.check(new int[]{Integer.MIN_VALUE, 100, 1, 2, 2}, 0, 0);
            fixture.check(new int[]{Integer.MAX_VALUE, 100, 1, 2, 2}, 0, 2);
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "mob_spawn_bounds")
    public static void failedHostileSpawnDoesNotConsumeCapacity(GameTestHelper helper) {
        verify(helper, fixture -> {
            int[] cancelled = {0};
            Consumer<EntityJoinLevelEvent> rejectFirst = event -> {
                if (event.getLevel() == helper.getLevel() && event.getEntity() instanceof BasicEntityShipHostile
                        && cancelled[0] == 0) {
                    event.setCanceled(true);
                    cancelled[0]++;
                }
            };
            MinecraftForge.EVENT_BUS.addListener(rejectFirst);
            try {
                fixture.check(new int[]{1, 100, 2, 1, 1}, 0, 1);
                helper.assertTrue(cancelled[0] == 1, "Fixture must reject the first hostile insertion");
            } finally {
                MinecraftForge.EVENT_BUS.unregister(rejectFirst);
            }
        });
    }

    private static void verify(GameTestHelper helper, Consumer<SpawnFixture> verification) {
        BlockPos origin = helper.absolutePos(new BlockPos(1, 2, 1));
        BlockPos playerPos = new BlockPos((origin.getX() & ~15) + 8, 72, (origin.getZ() & ~15) + 8);
        BlockPos water = new BlockPos(playerPos.getX() + 20, helper.getLevel().getSeaLevel() - 2,
                playerPos.getZ() + 20);
        Vec3 base = helper.absoluteVec(Vec3.ZERO);
        GameTestEntities.whenPositionsTicking(helper, () -> {
            ServerLevel level = helper.getLevel();
            FixedRollPlayer player = new FixedRollPlayer(level);
            player.moveTo(playerPos.getX() + 0.5, playerPos.getY() + 0.5, playerPos.getZ() + 0.5, 0, 0);
            CapaTeitoku capa = new CapaTeitoku();
            capa.setHasRing(true);
            var section = level.getChunkAt(playerPos).getSection(level.getSectionIndex(playerPos.getY()));
            var originalBiomes = section.getBiomes();
            var sampler = level.getChunkSource().randomState().sampler();
            var ocean = level.registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(Biomes.OCEAN);
            int[] originalConfig = ConfigHandler.mobSpawn;
            Difficulty originalDifficulty = level.getDifficulty();
            var oldWater = level.getBlockState(water);
            var oldAbove = level.getBlockState(water.above());
            List<Entity> existing = new ArrayList<>();
            level.getAllEntities().forEach(existing::add);
            SpawnFixture fixture = new SpawnFixture(helper, player, capa, existing);
            try {
                section.fillBiomesFromNoise((x, y, z, s) -> ocean, sampler, 0, 0, 0);
                level.getServer().setDifficulty(Difficulty.NORMAL, true);
                level.setBlock(water, Blocks.WATER.defaultBlockState(), 2);
                level.setBlock(water.above(), Blocks.AIR.defaultBlockState(), 2);
                helper.assertTrue(level.getDifficulty() == Difficulty.NORMAL && capa.hasRing()
                                && level.getBiome(player.blockPosition()).is(BiomeTags.IS_OCEAN)
                                && level.getFluidState(water).is(FluidTags.WATER)
                                && level.getBlockState(water.above()).isAir(),
                        "Spawn fixture must satisfy difficulty, ring, ocean and water conditions");
                helper.assertTrue(existing.stream().noneMatch(e -> EntityHelper.isInHostileCountBox(level, e)),
                        "Dedicated spawn batch must start without counted hostile ships");
                verification.accept(fixture);
            } finally {
                fixture.clearSpawned();
                ConfigHandler.mobSpawn = originalConfig;
                level.getServer().setDifficulty(originalDifficulty, true);
                section.fillBiomesFromNoise((x, y, z, s) -> originalBiomes.get(x & 3, y & 3, z & 3), sampler, 0, 0, 0);
                level.setBlock(water, oldWater, 2);
                level.setBlock(water.above(), oldAbove, 2);
                player.discard();
            }
            helper.succeed();
        }, Vec3.atCenterOf(playerPos).subtract(base), Vec3.atCenterOf(water).subtract(base));
    }

    private record SpawnFixture(GameTestHelper helper, FixedRollPlayer player, CapaTeitoku capa,
                                List<Entity> existing) {
        private List<Entity> spawned() {
            List<Entity> result = new ArrayList<>();
            helper.getLevel().getAllEntities().forEach(e -> {
                if (!existing.contains(e) && e instanceof BasicEntityShipHostile) result.add(e);
            });
            return result;
        }

        private void clearSpawned() {
            spawned().forEach(Entity::discard);
        }

        private void check(int[] config, int roll, int expectedAdded) {
            ConfigHandler.mobSpawn = config.clone();
            player.roll = roll;
            helper.assertTrue(Arrays.equals(ConfigHandler.mobSpawn, config), "Spawn config fixture must apply");
            helper.assertTrue(player.getRandom().nextInt(100) == roll, "Probability roll fixture must apply");
            List<Entity> before = spawned();
            EntityHelper.spawnMobShip(player, capa);
            List<Entity> after = spawned();
            helper.assertTrue(after.size() - before.size() == expectedAdded,
                    "Spawn config " + Arrays.toString(config) + " roll " + roll + " must add " + expectedAdded
                            + " ships, got " + (after.size() - before.size()));
            after.forEach(e -> GameTestEntities.assertRegistered(helper, e));
        }
    }

    private static final class FixedRollPlayer extends FakePlayer {
        private int roll;
        private final RandomSource fixtureRandom = new LegacyRandomSource(493) {
            @Override
            public int nextInt(int bound) {
                return bound == 100 ? roll : 0;
            }
        };

        private FixedRollPlayer(ServerLevel level) {
            super(level, new GameProfile(UUID.randomUUID(), "spawn_bounds"));
        }

        @Override
        public RandomSource getRandom() {
            return fixtureRandom;
        }
    }
}
