package com.lulan.shincolle.shipdata;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lulan.shincolle.crafting.LargeRecipes;
import com.lulan.shincolle.crafting.ShipCalc;
import com.lulan.shincolle.crafting.SmallRecipes;
import com.lulan.shincolle.entity.BasicEntityShipHostile;
import com.lulan.shincolle.entity.GenericHostileShipEntity;
import com.lulan.shincolle.entity.GenericShipEntity;
import com.lulan.shincolle.gametest.GameTestEntities;
import com.lulan.shincolle.handler.ConfigHandler;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.init.ModItems;
import com.lulan.shincolle.item.ShipSpawnEgg;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;
import com.lulan.shincolle.utility.CalcHelper;
import com.lulan.shincolle.utility.EntityHelper;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ShipAcquisitionGameTests {
    private static final ShipDefinitionId SAMPLE = ShipDefinitionId.parse("shincolle_test:acquisition");
    private static final ShipDefinitionId KASHIMA = ShipDefinitionId.parse("shincolle_kai:kashima");
    private static final Vec3 POSITION = new Vec3(1.5, 2, 1.5);
    private static final int[] SMALL_MATS = {16, 20, 30, 40};
    private static final int[] LARGE_MATS = {100, 150, 200, 250};
    private static final int[][] SMALL_TABLE = {
        {ID.ShipClass.DDI, 80, 0}, {ID.ShipClass.DDRO, 90, 0}, {ID.ShipClass.DDHA, 100, 0},
        {ID.ShipClass.DDNI, 110, 0}, {ID.ShipClass.APWA, 120, 1}, {ID.ShipClass.SSKA, 140, 2},
        {ID.ShipClass.SSYO, 160, 2}, {ID.ShipClass.SSSO, 180, 2}, {ID.ShipClass.CARI, 200, 2}, {ID.ShipClass.CANE, 256, 2}
    };
    private static final int[][] LARGE_TABLE = {
        {ID.ShipClass.DDHime, 500, 0}, {ID.ShipClass.CVWO, 650, 3}, {ID.ShipClass.BBTA, 800, 2},
        {ID.ShipClass.BBRU, 800, 2}, {ID.ShipClass.CAHime, 2000, 2}, {ID.ShipClass.NorthernHime, 2600, 1},
        {ID.ShipClass.SSNH, 2600, 2}, {ID.ShipClass.IsolatedHime, 2700, 1}, {ID.ShipClass.HarbourHime, 2800, 1},
        {ID.ShipClass.AirfieldHime, 3000, 1}, {ID.ShipClass.CVHime, 3000, 3}, {ID.ShipClass.SSHime, 3500, 2},
        {ID.ShipClass.BBRE, 3800, 2}, {ID.ShipClass.BBHime, 4600, 2}, {ID.ShipClass.MidwayHime, 4800, 1},
        {ID.ShipClass.CVWD, 5000, 3}
    };

    private ShipAcquisitionGameTests() {
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void constructionWithoutEnabledDefinitionsMatchesOriginalTablesAndSeededRolls(GameTestHelper helper) {
        withDefinitions(Map.of(), () -> {
            for (int tier = 0; tier < 2; tier++) {
                RandomSource expected = RandomSource.create(731);
                RandomSource actual = RandomSource.create(731);
                for (int i = 0; i < 128; i++) {
                    int[] mats = {i + 16, i * 2 + 20, i * 3 + 30, i * 4 + 40};
                    Map<ShipChoice, Float> table = originalConstruction(tier, mats);
                    helper.assertTrue(table.equals(materialTable(tier, mats, List.of())), "built-in material weights changed");
                    float total = 0;
                    for (float p : table.values()) total += p;
                    float roll = expected.nextFloat() * total;
                    float sum = 0.0125F;
                    ShipChoice choice = new ShipChoice.BuiltIn(ID.ShipClass.DDI);
                    for (var entry : table.entrySet()) {
                        sum += entry.getValue();
                        if (sum > roll) {
                            choice = entry.getKey();
                            break;
                        }
                    }
                    helper.assertTrue(choice.equals(ShipCalc.rollShipType(tier, mats, actual)), "built-in seeded roll changed");
                }
                ItemStack egg = ShipCalc.rollShip(tier == 0 ? SMALL_MATS : LARGE_MATS, tier, RandomSource.create(731));
                helper.assertTrue(egg.is(ModItems.SHIP_SPAWN_EGG.get()) && egg.getTag().contains("ShipClass")
                        && egg.getTag().getByte("BuildType") == tier, "built-in completion egg changed");
            }
            helper.succeed();
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void materialConstructionCompletesAsFixedEggAndSpawnsOwnedAlly(GameTestHelper helper) {
        constructionEgg(helper, "material");
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void weightedConstructionCompletesAsFixedEggAndSpawnsOwnedAlly(GameTestHelper helper) {
        constructionEgg(helper, "weighted");
    }

    private static void constructionEgg(GameTestHelper helper, String mode) {
        fixture(helper, entities -> {
            FakePlayer player = player(helper);
            ItemStack previous = player.getMainHandItem();
            try {
                for (int tier = 0; tier < 2; tier++) {
                    int[] mats = tier == 0 ? SMALL_MATS : LARGE_MATS;
                    ShipDefinition definition = definition(SAMPLE, "{\"enabled\":true,\"mode\":\"" + mode
                            + "\",\"weight\":2147483647,\"tier\":\"" + (tier == 0 ? "small" : "large")
                            + "\",\"material_mean\":" + (tier == 0 ? 106 : 700) + "}", "{}");
                    withDefinitions(Map.of(SAMPLE, definition), () -> {
                        ItemStack egg = mats == SMALL_MATS
                                ? SmallRecipes.calculateResult(mats[0], mats[1], mats[2], mats[3], true, new FixedRandom(0.1))
                                : LargeRecipes.calculateResult(mats[0], mats[1], mats[2], mats[3], true, new FixedRandom(0.1));
                        helper.assertTrue(egg.is(ModItems.GENERIC_SHIP_SPAWN_EGG.get()), "construction did not output a generic egg");
                        helper.assertTrue(egg.getTag().getString("ShipId").equals(SAMPLE.toString())
                                && !egg.getTag().contains("ShipClass") && !egg.getTag().getBoolean("Hostile"), "wrong completion identity");
                        helper.assertTrue(egg.getTag().getInt("Grudge") == mats[0] && egg.getTag().getInt("Ammo") == mats[2],
                                "construction material tags lost");
                        // Disabling acquisition after completion must not re-roll an already fixed egg.
                        ShipDefinition disabled = definition(SAMPLE, "{\"enabled\":false}", "{}");
                        withDefinitions(Map.of(SAMPLE, disabled), () -> useFriendlyEgg(helper, entities, player, egg, SAMPLE));
                    });
                }
            } finally {
                player.setItemInHand(InteractionHand.MAIN_HAND, previous);
            }
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void materialModeUsesMeanBiasAndHundredBasedMultiplier(GameTestHelper helper) {
        for (int tier = 0; tier < 2; tier++) {
            for (int bias = -1; bias < 4; bias++) {
                String[] names = {"grudge", "abyssium", "ammo", "polymetal"};
                String settings = "{\"enabled\":true,\"tier\":\"" + (tier == 0 ? "small" : "large")
                        + "\",\"material_mean\":150,\"weight\":250"
                        + (bias < 0 ? "" : ",\"material_bias\":\"" + names[bias] + "\"") + "}";
                ShipDefinition ship = definition(SAMPLE, settings, "{}");
                int mean = 150 - (bias < 0 ? 0 : SMALL_MATS[bias]);
                int distance = Math.abs(106 - mean);
                if (tier == 0) distance = (int) (distance * 15.625F);
                float expected = CalcHelper.getNormDist(distance) * 2.5F;
                helper.assertTrue(materialTable(tier, SMALL_MATS, List.of(ship)).get(new ShipChoice.Defined(SAMPLE)) == expected,
                        "material formula changed for tier=" + tier + " bias=" + bias);
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void weightedPoolHonorsAbsoluteProbabilityCapRatiosTierAndDisabledEntries(GameTestHelper helper) {
        ShipDefinitionId second = ShipDefinitionId.parse("shincolle_test:second");
        ShipDefinition a = definition(SAMPLE, "{\"enabled\":true,\"mode\":\"weighted\",\"weight\":2500}", "{}");
        ShipDefinition b = definition(second, "{\"enabled\":true,\"mode\":\"weighted\",\"weight\":7500}", "{}");
        withDefinitions(Map.of(SAMPLE, a, second, b), () -> {
            for (int tier = 0; tier < 2; tier++) {
                helper.assertTrue(ShipCalc.rollShipType(tier, SMALL_MATS, new FixedRandom(0.499, 0.249))
                        .equals(new ShipChoice.Defined(SAMPLE)), "weighted proportional share lost below boundary");
                helper.assertTrue(ShipCalc.rollShipType(tier, LARGE_MATS, new FixedRandom(0.499, 0.25))
                        .equals(new ShipChoice.Defined(second)), "weighted proportional share lost at boundary");
                helper.assertTrue(ShipCalc.rollShipType(tier, SMALL_MATS, new FixedRandom(0.5)) instanceof ShipChoice.BuiltIn,
                        "weighted pool exceeds 50% cap");
            }
        });
        ShipDefinition low = definition(SAMPLE, "{\"enabled\":true,\"mode\":\"weighted\",\"weight\":35,\"tier\":\"large\"}", "{}");
        ShipDefinition disabled = definition(second, "{\"enabled\":false,\"mode\":\"weighted\",\"weight\":2147483647}", "{}");
        withDefinitions(Map.of(SAMPLE, low, second, disabled), () -> {
            helper.assertTrue(ShipCalc.rollShipType(1, LARGE_MATS, new FixedRandom(0.00349))
                    .equals(new ShipChoice.Defined(SAMPLE)), "weighted probability is not weight/10000");
            helper.assertTrue(ShipCalc.rollShipType(1, LARGE_MATS, new FixedRandom(0.0035)) instanceof ShipChoice.BuiltIn,
                    "weighted absolute boundary changed or disabled entry included");
            helper.assertTrue(ShipCalc.rollShipType(0, SMALL_MATS, new FixedRandom(0)) instanceof ShipChoice.BuiltIn,
                    "tier restriction or disabled flag ignored");
        });
        withDefinitions(Map.of(SAMPLE, definition(SAMPLE, "{\"enabled\":false,\"weight\":2147483647}", "{}")), () -> {
            helper.assertTrue(ShipCalc.rollShipType(0, SMALL_MATS, new FixedRandom(0.99)) instanceof ShipChoice.BuiltIn,
                    "disabled material definition included");
        });
        withDefinitions(Map.of(SAMPLE, definition(SAMPLE,
                "{\"enabled\":true,\"tier\":\"small\",\"material_mean\":106,\"weight\":0}", "{}")), () -> {
            helper.assertTrue(ShipCalc.rollShipType(0, SMALL_MATS, new FixedRandom(0.99)) instanceof ShipChoice.BuiltIn,
                    "zero-weight material definition included");
        });
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void unclassifiedConstructionEggFallbackCanSpawnDefinedAlly(GameTestHelper helper) {
        fixture(helper, entities -> {
            ShipDefinition ship = definition(SAMPLE,
                    "{\"enabled\":true,\"tier\":\"small\",\"material_mean\":106,\"weight\":2147483647}", "{}");
            withDefinitions(Map.of(SAMPLE, ship), () -> {
                FakePlayer player = player(helper);
                ItemStack previous = player.getMainHandItem();
                try {
                    ItemStack egg = ((ShipSpawnEgg) ModItems.SHIP_SPAWN_EGG.get()).createConstructionStack(0);
                    String[] keys = {"Grudge", "Abyssium", "Ammo", "Polymetal"};
                    for (int i = 0; i < keys.length; i++) egg.getOrCreateTag().putInt(keys[i], SMALL_MATS[i]);
                    helper.assertTrue(!egg.getTag().contains("ShipClass"), "fixture already has a fixed class");
                    helper.getLevel().random.setSeed(731);
                    useFriendlyEgg(helper, entities, player, egg, SAMPLE);
                } finally {
                    player.setItemInHand(InteractionHand.MAIN_HAND, previous);
                }
            });
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void enabledMaterialWithoutMeanOrTierRejectsOnlyItsDefinition(GameTestHelper helper) {
        Map<net.minecraft.resources.ResourceLocation, com.google.gson.JsonElement> inputs = new HashMap<>();
        inputs.put(SAMPLE.location(), json("{\"enabled\":true,\"tier\":\"small\",\"material_mean\":100}", "{}"));
        inputs.put(new net.minecraft.resources.ResourceLocation("shincolle_test", "missing_mean"),
                json("{\"enabled\":true,\"tier\":\"small\"}", "{}"));
        inputs.put(new net.minecraft.resources.ResourceLocation("shincolle_test", "missing_tier"),
                json("{\"enabled\":true,\"material_mean\":100}", "{}"));
        Map<ShipDefinitionId, ShipDefinition> parsed = new ShipDefinitionLoader().parseDefinitions(inputs);
        helper.assertTrue(parsed.size() == 1 && parsed.containsKey(SAMPLE), "invalid material settings accepted or valid neighbor lost");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void wildTableWithoutEnabledDefinitionsExactlyMatchesOldNestedBranches(GameTestHelper helper) {
        Map<Integer, Integer> old = new HashMap<>();
        for (int ran = 0; ran < 100; ran++) {
            for (int group = 0; group < 3; group++) {
                for (int four = 0; four < 4; four++) {
                    for (int two = 0; two < 2; two++) {
                        for (int seven = 0; seven < 7; seven++) {
                            old.merge(oldWild(ran, group, four, two, seven), 1, Integer::sum);
                        }
                    }
                }
            }
        }
        withDefinitions(Map.of(), () -> {
            Map<Integer, Integer> actual = new HashMap<>();
            for (int slot = 0; slot < 700; slot++) {
                ShipChoice choice = pickWild((slot + 0.5) / 700, 0);
                helper.assertTrue(choice instanceof ShipChoice.BuiltIn, "unexpected data-defined candidate");
                actual.merge(((ShipChoice.BuiltIn) choice).shipClass(), 1, Integer::sum);
            }
            helper.assertTrue(old.size() == 19 && actual.size() == 19, "old table lost a ship");
            old.forEach((id, count) -> helper.assertTrue(actual.getOrDefault(id, 0) * 24 == count,
                    "wild probability changed for class " + id));
        });
        withDefinitions(Map.of(SAMPLE, definition(SAMPLE, "{}", "{\"enabled\":false,\"weight\":2147483647}")), () -> {
            helper.assertTrue(pickWild(0.999, 0) instanceof ShipChoice.BuiltIn, "disabled spawn definition included");
        });
        withDefinitions(Map.of(SAMPLE, definition(SAMPLE, "{}", "{\"enabled\":true,\"weight\":0}")), () -> {
            helper.assertTrue(pickWild(0.999, 0) instanceof ShipChoice.BuiltIn, "zero-weight spawn definition included");
        });
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void wildKashimaSpawnsWithIdentityScaleAndTierAttributes(GameTestHelper helper) {
        fixture(helper, entities -> {
            ShipDefinition kashima = ShipDefinitionRegistry.server().get(KASHIMA);
            helper.assertTrue(kashima != null && kashima.spawn().enabled() && kashima.spawn().weight() == 35
                    && !kashima.construction().enabled(), "Kashima acquisition definition incorrect");
            withDefinitions(Map.of(KASHIMA, kashima), () -> {
                for (int scale = 0; scale < 2; scale++) {
                    helper.assertTrue(pickWild(699.5 / 735, scale) instanceof ShipChoice.BuiltIn, "built-in weight total is not 700");
                    helper.assertTrue(pickWild(700.5 / 735, scale).equals(new ShipChoice.Defined(KASHIMA)), "Kashima weight not appended");
                    BasicEntityShipHostile ship = spawnWild(helper, entities, scale, 0.999);
                    helper.assertTrue(ship instanceof GenericHostileShipEntity generic && generic.getShipId().equals(KASHIMA.toString()),
                            "wild spawn lost Kashima identity");
                    helper.assertTrue(ship.getScaleLevel() == scale && ship.getHealth() == ship.getMaxHealth(), "wild scale/health not initialized");
                    double[] tier = scale == 0 ? ConfigHandler.scaleMobSmall : ConfigHandler.scaleMobLarge;
                    int[] columns = {ID.Attrs.HP, ID.Attrs.ATK_L, ID.Attrs.DEF, ID.Attrs.SPD, ID.Attrs.MOV, ID.Attrs.HIT};
                    float[] modifiers = ShipDefinitionAttributes.columns(kashima.hostileModifiers());
                    for (int i = 0; i < columns.length; i++) {
                        helper.assertTrue(ship.getAttrs().getAttrsRaw(columns[i]) == (float) tier[i] * modifiers[i], "wild tier attributes wrong");
                    }
                    ship.discard();
                }
            });
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void bossSelectionNeverIncludesEnabledDefinedShips(GameTestHelper helper) {
        fixture(helper, entities -> {
            ShipDefinition ship = definition(SAMPLE, "{}", "{\"enabled\":true,\"weight\":2147483647}");
            withDefinitions(Map.of(SAMPLE, ship), () -> {
                helper.assertTrue(pickWild(0.999, 0).equals(new ShipChoice.Defined(SAMPLE))
                        && pickWild(0.999, 1).equals(new ShipChoice.Defined(SAMPLE)), "spawn total overflowed int range");
                for (int scale : new int[]{2, 3}) {
                    for (int slot = 0; slot < 700; slot++) {
                        helper.assertTrue(pickWild((slot + 0.5) / 700, scale) instanceof ShipChoice.BuiltIn, "defined ship entered boss pool");
                    }
                    BasicEntityShipHostile boss = spawnWild(helper, entities, scale, 0.999);
                    helper.assertTrue(!(boss instanceof DefinitionBackedShip) && boss.getScaleLevel() == scale, "boss spawn route included addon");
                    boss.discard();
                }
            });
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void definedHostileEggDropOddsMatchBuiltInAtEveryScaleAndMissingIdsDropNothing(GameTestHelper helper) {
        fixture(helper, entities -> {
            GenericHostileShipEntity ship = (GenericHostileShipEntity) GenericShipSpawner.create(helper.getLevel(), KASHIMA, true, null);
            helper.assertTrue(ship != null, "Kashima fixture missing");
            entities.add(ship);
            BasicEntityShipHostile original = entities.add(ModEntities.DESTROYER_AKATSUKI_MOB.get().create(helper.getLevel()));
            for (int scale = 0; scale < 4; scale++) {
                ship.initAttrs(scale);
                original.initAttrs(scale);
                helper.assertTrue(ship.getScaleLevel() == scale && original.getScaleLevel() == scale, "scale fixture ignored");
                ship.getRandom().setSeed(731);
                original.getRandom().setSeed(731);
                int successes = 0;
                for (int i = 0; i < 256; i++) {
                    ItemStack egg = ship.getDropEgg();
                    helper.assertTrue(egg.isEmpty() == original.getDropEgg().isEmpty(), "drop probability differs at scale " + scale);
                    if (!egg.isEmpty()) {
                        successes++;
                        helper.assertTrue(egg.is(ModItems.GENERIC_SHIP_SPAWN_EGG.get()) && egg.getCount() == 1
                                && egg.getTag().getString("ShipId").equals(KASHIMA.toString())
                                && !egg.getTag().getBoolean("Hostile"), "drop identity or side changed");
                    }
                }
                helper.assertTrue(successes > 0 && (scale == 3 ? successes == 256 : successes < 256), "drop fixture never exercised both paths");
            }
            Map<ShipDefinitionId, ShipDefinition> absent = new HashMap<>(ShipDefinitionRegistry.server().all());
            absent.remove(KASHIMA);
            withDefinitions(absent, () -> helper.assertTrue(ship.getDropEgg().isEmpty(), "missing definition still drops an egg"));
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void killingDefinedHostileDropsMatchingFriendlyEggWhichSpawnsOwnedAlly(GameTestHelper helper) {
        fixture(helper, entities -> {
            GenericHostileShipEntity enemy = (GenericHostileShipEntity) GenericShipSpawner.create(helper.getLevel(), KASHIMA, true, null);
            helper.assertTrue(enemy != null, "Kashima fixture missing");
            entities.add(enemy);
            Vec3 pos = helper.absoluteVec(POSITION);
            enemy.moveTo(pos.x, pos.y, pos.z, 0, 0);
            enemy.initAttrs(1);
            FixedRandom dropRandom = new FixedRandom(0);
            try {
                Field field = Entity.class.getDeclaredField("random");
                field.setAccessible(true);
                field.set(enemy, dropRandom);
            } catch (ReflectiveOperationException exception) {
                throw new GameTestAssertException("Cannot control death-drop random source: " + exception);
            }
            helper.assertTrue(enemy.getRandom() == dropRandom, "death random fixture was not installed");
            helper.assertTrue(enemy.getScaleLevel() == 1 && enemy.getStateFlag(ID.F.CanDrop), "death fixture ignored scale/drop setup");
            helper.assertTrue(helper.getLevel().addFreshEntity(enemy), "enemy not added");
            GameTestEntities.assertRegistered(helper, enemy);
            helper.assertTrue(enemy.hurt(helper.getLevel().damageSources().genericKill(), Float.MAX_VALUE) && enemy.isDeadOrDying(),
                    "enemy was not killed");
            List<ItemEntity> drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(pos, pos).inflate(3),
                    e -> e.getItem().is(ModItems.GENERIC_SHIP_SPAWN_EGG.get()));
            drops.forEach(entities::add);
            helper.assertTrue(drops.size() == 1 && !enemy.getStateFlag(ID.F.CanDrop), "death did not drop exactly one egg");
            ItemStack egg = drops.get(0).getItem().copy();
            helper.assertTrue(egg.getTag().getString("ShipId").equals(KASHIMA.toString())
                    && !egg.getTag().getBoolean("Hostile"), "death drop is not the matching friendly egg");
            FakePlayer player = player(helper);
            ItemStack previous = player.getMainHandItem();
            try {
                useFriendlyEgg(helper, entities, player, egg, KASHIMA);
            } finally {
                player.setItemInHand(InteractionHand.MAIN_HAND, previous);
            }
        });
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void summonWithShipIdRecordsCurrentFriendlySpawnBehavior(GameTestHelper helper) {
        fixture(helper, entities -> {
            UUID uuid = UUID.randomUUID();
            CompoundTag nbt = new CompoundTag();
            nbt.putUUID("UUID", uuid);
            nbt.putString("ShipId", KASHIMA.toString());
            var source = helper.getLevel().getServer().createCommandSourceStack().withLevel(helper.getLevel())
                    .withPosition(helper.absoluteVec(POSITION)).withPermission(4).withSuppressedOutput();
            int result = source.getServer().getCommands().performPrefixedCommand(source,
                    "summon shincolle_kai:generic_ship ~ ~ ~ " + nbt);
            Entity entity = helper.getLevel().getEntity(uuid);
            if (entity != null) entities.add(entity);
            helper.assertTrue(result == 1 && entity instanceof GenericShipEntity, "NBT summon did not create a friendly ship");
            GenericShipEntity ship = (GenericShipEntity) entity;
            helper.assertTrue(ship.getShipId().equals(KASHIMA.toString()) && !ship.definitionState().dormant(),
                    "NBT summon identity/dormancy changed");
            helper.assertTrue(ship.position().distanceTo(helper.absoluteVec(POSITION)) < 0.001, "NBT summon position changed");
            helper.assertTrue(ship.getOwnerUUID() == null, "vanilla summon unexpectedly assigns an owner");
            helper.assertTrue(ship.saveWithoutId(new CompoundTag()).getString("ShipId").equals(KASHIMA.toString()), "summon save lost ID");
        });
    }

    private static FakePlayer player(GameTestHelper helper) {
        FakePlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "AcquisitionEgg"));
        player.getAbilities().instabuild = false;
        return player;
    }

    private static void useFriendlyEgg(GameTestHelper helper, GameTestEntities entities, FakePlayer player,
                                       ItemStack egg, ShipDefinitionId id) {
        AABB area = new AABB(helper.absoluteVec(POSITION), helper.absoluteVec(POSITION)).inflate(3);
        List<GenericShipEntity> before = helper.getLevel().getEntitiesOfClass(GenericShipEntity.class, area);
        player.setItemInHand(InteractionHand.MAIN_HAND, egg);
        var hit = new BlockHitResult(helper.absoluteVec(POSITION), Direction.UP, helper.absolutePos(new BlockPos(1, 1, 1)), false);
        var result = egg.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
        List<GenericShipEntity> after = helper.getLevel().getEntitiesOfClass(GenericShipEntity.class, area,
                ship -> !before.contains(ship));
        after.forEach(entities::add);
        helper.assertTrue(result.consumesAction() && egg.isEmpty() && after.size() == 1, "egg use did not add and consume one friendly ship");
        GenericShipEntity ship = after.get(0);
        GameTestEntities.assertRegistered(helper, ship);
        helper.assertTrue(ship.getShipId().equals(id.toString()) && player.getUUID().equals(ship.getOwnerUUID())
                && ship.isTame() && ship.getShipClass() == 0, "friendly identity or owner lost");
        helper.assertTrue(ship.getStateMinor(ID.M.NumGrudge) > 0 && !ship.getStateFlag(ID.F.NoFuel), "fresh ally has no fuel");
        ship.discard();
    }

    private static BasicEntityShipHostile spawnWild(GameTestHelper helper, GameTestEntities entities, int scale, double roll) {
        Vec3 pos = helper.absoluteVec(POSITION);
        AABB area = new AABB(pos, pos).inflate(2);
        List<BasicEntityShipHostile> before = helper.getLevel().getEntitiesOfClass(BasicEntityShipHostile.class, area);
        invoke(EntityHelper.class, "spawnRandomHostile",
                new Class<?>[]{ServerLevel.class, double.class, double.class, double.class, int.class, RandomSource.class},
                helper.getLevel(), pos.x, pos.y, pos.z, scale, new FixedRandom(roll));
        List<BasicEntityShipHostile> after = helper.getLevel().getEntitiesOfClass(BasicEntityShipHostile.class, area,
                ship -> !before.contains(ship));
        after.forEach(entities::add);
        helper.assertTrue(after.size() == 1, "random hostile spawn did not register one ship");
        GameTestEntities.assertRegistered(helper, after.get(0));
        return after.get(0);
    }

    private static ShipChoice pickWild(double roll, int scale) {
        return (ShipChoice) invoke(EntityHelper.class, "pickRandomMobShipType",
                new Class<?>[]{RandomSource.class, int.class}, new FixedRandom(roll), scale);
    }

    @SuppressWarnings("unchecked")
    private static Map<ShipChoice, Float> materialTable(int tier, int[] mats, List<ShipDefinition> definitions) {
        return (Map<ShipChoice, Float>) invoke(ShipCalc.class, "materialTable",
                new Class<?>[]{int.class, int[].class, List.class}, tier, mats, definitions);
    }

    private static Object invoke(Class<?> owner, String name, Class<?>[] types, Object... args) {
        try {
            Method method = owner.getDeclaredMethod(name, types);
            method.setAccessible(true);
            return method.invoke(null, args);
        } catch (ReflectiveOperationException exception) {
            throw new GameTestAssertException("Cannot invoke acquisition boundary " + name + ": " + exception);
        }
    }

    private static ShipDefinition definition(ShipDefinitionId id, String construction, String spawn) {
        return new ShipDefinitionValidator().validate(id, json(construction, spawn), ignored -> { });
    }

    private static JsonObject json(String construction, String spawn) {
        JsonObject json = JsonParser.parseString("{\"schema\":1,\"ship_type\":\"shincolle_kai:light_cruiser\","
                + "\"visual\":\"shincolle_test:sample_ship\",\"base_stats\":{\"hp\":42,\"atk\":13,\"mov\":0.42}}").getAsJsonObject();
        json.add("construction", JsonParser.parseString(construction));
        json.add("spawn", JsonParser.parseString(spawn));
        return json;
    }

    private static void withDefinitions(Map<ShipDefinitionId, ShipDefinition> definitions, Runnable verification) {
        ShipDefinitionRegistry registry = ShipDefinitionRegistry.server();
        Map<ShipDefinitionId, ShipDefinition> previous = registry.all();
        registry.reload(() -> definitions);
        try {
            verification.run();
        } finally {
            registry.reload(() -> previous);
        }
    }

    private static void fixture(GameTestHelper helper, Consumer<GameTestEntities> verification) {
        GameTestEntities.whenPositionsTicking(helper, () -> {
            try (GameTestEntities entities = GameTestEntities.open(helper)) {
                verification.accept(entities);
                helper.succeed();
            }
        }, POSITION);
    }

    private static Map<ShipChoice, Float> originalConstruction(int tier, int[] mats) {
        Map<Integer, Float> old = new HashMap<>();
        for (int[] entry : tier == 0 ? SMALL_TABLE : LARGE_TABLE) {
            int mean = entry[1] - mats[entry[2]];
            int distance = Math.abs(mats[0] + mats[1] + mats[2] + mats[3] - mean);
            if (tier == 0) distance = (int) (distance * 15.625F);
            old.put(entry[0], CalcHelper.getNormDist(distance));
        }
        Map<ShipChoice, Float> ordered = new java.util.LinkedHashMap<>();
        old.forEach((id, p) -> ordered.put(new ShipChoice.BuiltIn(id), p));
        return ordered;
    }

    private static int oldWild(int ran, int group, int four, int two, int seven) {
        if (ran > 75) {
            if (group == 1) return ID.ShipClass.BBYamato;
            if (group == 2) return new int[]{ID.ShipClass.BBKongou, ID.ShipClass.BBHiei,
                    ID.ShipClass.BBHaruna, ID.ShipClass.BBKirishima}[four];
            return ID.ShipClass.BBNagato;
        }
        if (ran > 45) {
            if (group != 0) return new int[]{ID.ShipClass.CATakao, ID.ShipClass.CLTenryuu,
                    ID.ShipClass.CLTatsuta, ID.ShipClass.CAAtago}[four];
            return two == 1 ? ID.ShipClass.CVKaga : ID.ShipClass.CVAkagi;
        }
        return new int[]{ID.ShipClass.DDAkatsuki, ID.ShipClass.DDHibiki, ID.ShipClass.DDIkazuchi,
                ID.ShipClass.DDInazuma, ID.ShipClass.DDShimakaze, ID.ShipClass.SSU511, ID.ShipClass.SSRo500}[seven];
    }

    private static final class FixedRandom extends LegacyRandomSource {
        private final double[] values;
        private int index;

        private FixedRandom(double... values) {
            super(731);
            this.values = values;
        }

        @Override
        public double nextDouble() {
            return values[Math.min(index++, values.length - 1)];
        }

        @Override
        public float nextFloat() {
            return 0.5F;
        }

        @Override
        public int nextInt(int bound) {
            return (int) (nextDouble() * bound);
        }

        @Override
        public long nextLong() {
            return (long) (nextDouble() * 10000000) << 1;
        }
    }
}
