package com.lulan.shincolle.entity;

import com.lulan.shincolle.gametest.GameTestEntities;
import com.lulan.shincolle.handler.ConfigHandler;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.item.ShipSpawnEgg;
import com.lulan.shincolle.reference.Reference;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraftforge.common.ForgeConfigSpec.DoubleValue;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class EggDropChanceGameTests {

    private EggDropChanceGameTests() {
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void defaultEggDropChancesMatchExistingProbabilities(GameTestHelper helper) {
        helper.assertTrue(ConfigHandler.COMMON.eggDropChanceMobSmall.getDefault() == 0.2D,
                "Small hostile ship default must be 1/5");
        helper.assertTrue(ConfigHandler.COMMON.eggDropChanceMobLarge.getDefault() == 1.0D / 3.0D,
                "Large hostile ship default must be exactly 1/3");
        helper.assertTrue(ConfigHandler.COMMON.eggDropChanceBossSmall.getDefault() == 0.9D,
                "Small boss default must be 9/10");
        helper.assertTrue(ConfigHandler.COMMON.eggDropChanceBossLarge.getDefault() == 1.0D,
                "Large boss default must be certain");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void eggDropRollUsesStrictBoundaryAndSkipsCertainRolls(GameTestHelper helper) {
        FixedRandom below = new FixedRandom(0.4999D);
        helper.assertTrue(BasicEntityShipHostile.rollEggDrop(below, 0.5D), "A roll below the chance must succeed");
        helper.assertTrue(below.calls == 1, "An intermediate chance must draw exactly once");
        FixedRandom equal = new FixedRandom(0.5D);
        helper.assertTrue(!BasicEntityShipHostile.rollEggDrop(equal, 0.5D), "A roll equal to the chance must fail");
        helper.assertTrue(equal.calls == 1, "An intermediate chance must draw exactly once");
        FixedRandom certain = new FixedRandom(0.5D);
        helper.assertTrue(BasicEntityShipHostile.rollEggDrop(certain, 1.0D), "Chance 1 must always succeed");
        helper.assertTrue(!BasicEntityShipHostile.rollEggDrop(certain, 0.0D), "Chance 0 must always fail");
        helper.assertTrue(certain.calls == 0, "Certain outcomes must not consume randomness");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void eggDropChanceReadsTheSettingForEachScale(GameTestHelper helper) {
        double[] chances = {0.1D, 0.4D, 0.7D, 0.8D};
        withEggDropChances(helper, chances, () -> {
            for (int scale = 0; scale < chances.length; scale++) {
                helper.assertTrue(ConfigHandler.eggDropChance(scale) == chances[scale],
                        "Wrong configured chance at scale " + scale);
            }
        });
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void zeroEggDropChanceDisablesDeathEggsAtEveryScale(GameTestHelper helper) {
        assertConfiguredEggDrops(helper, false);
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void certainEggDropChanceGivesMatchingDeathEggsAtEveryScale(GameTestHelper helper) {
        assertConfiguredEggDrops(helper, true);
        helper.succeed();
    }

    private static void assertConfiguredEggDrops(GameTestHelper helper, boolean shouldDrop) {
        double chance = shouldDrop ? 1.0D : 0.0D;
        try (GameTestEntities entities = GameTestEntities.open(helper)) {
            BasicEntityShipHostile ship = ModEntities.DESTROYER_AKATSUKI_MOB.get().create(helper.getLevel());
            helper.assertTrue(ship != null, "Hostile ship fixture missing");
            entities.add(ship);
            withEggDropChances(helper, new double[]{chance, chance, chance, chance}, () -> {
                for (int scale = 0; scale < 4; scale++) {
                    ship.initAttrs(scale);
                    helper.assertTrue(ship.getScaleLevel() == scale, "Scale fixture was not applied");
                    for (int attempt = 0; attempt < 256; attempt++) {
                        ItemStack egg = ship.getDropEgg();
                        helper.assertTrue(egg.isEmpty() != shouldDrop,
                                "Wrong egg outcome at scale " + scale + ", attempt " + attempt);
                        if (shouldDrop) {
                            helper.assertTrue(ShipSpawnEgg.getShipClass(egg) == ship.getShipClass(),
                                    "Death egg must match the hostile ship class");
                        }
                    }
                }
            });
        }
    }

    private static void withEggDropChances(GameTestHelper helper, double[] chances, Runnable assertion) {
        DoubleValue[] settings = {ConfigHandler.COMMON.eggDropChanceMobSmall, ConfigHandler.COMMON.eggDropChanceMobLarge,
                ConfigHandler.COMMON.eggDropChanceBossSmall, ConfigHandler.COMMON.eggDropChanceBossLarge};
        double[] previous = new double[settings.length];
        for (int scale = 0; scale < settings.length; scale++) {
            previous[scale] = settings[scale].get();
        }
        try {
            for (int scale = 0; scale < settings.length; scale++) {
                settings[scale].set(chances[scale]);
                helper.assertTrue(settings[scale].get() == chances[scale], "Chance fixture was not applied at scale " + scale);
            }
            assertion.run();
        } finally {
            for (int scale = 0; scale < settings.length; scale++) {
                settings[scale].set(previous[scale]);
            }
        }
    }

    private static final class FixedRandom extends LegacyRandomSource {
        private final double value;
        private int calls;

        private FixedRandom(double value) {
            super(731);
            this.value = value;
        }

        @Override
        public double nextDouble() {
            calls++;
            return value;
        }
    }
}
