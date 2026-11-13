package com.lulan.shincolle.gametest;

import com.lulan.shincolle.handler.ConfigHandler;
import com.lulan.shincolle.init.ModBlocks;
import com.lulan.shincolle.init.ModItems;
import com.lulan.shincolle.reference.Reference;
import com.lulan.shincolle.tileentity.TileEntitySmallShipyard;
import com.lulan.shincolle.tileentity.TileMultiGrudgeHeavy;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.config.ConfigTracker;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;

@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ShipyardFuelUseGameTests {
    private ShipyardFuelUseGameTests() { }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "config_consumers")
    public static void smallHalfFuelUsePreservesBuildSpeed(GameTestHelper helper) {
        withFuelUse(helper, 0.5, 1.0, () -> {
            var tile = small(helper, 10000);
            tick(helper, tile);
            helper.assertTrue(tile.getPowerConsumed() == 48 && tile.getPowerRemained() == 9976,
                    "Half fuel use must add 48 progress and drain 24 fuel");
        });
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "config_consumers")
    public static void smallFractionalFuelUseCarriesExactlyAcrossHundredTicks(GameTestHelper helper) {
        withFuelUse(helper, 0.0025, 1.0, () -> {
            var tile = small(helper, 10000);
            for (int i = 0; i < 100; i++) tick(helper, tile);
            helper.assertTrue(tile.getPowerConsumed() == 4800 && tile.getPowerRemained() == 9988,
                    "100 fractional ticks must add 4800 progress and drain exactly 12 fuel");
        });
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "config_consumers")
    public static void smallFractionalFuelUseBuildsWithOneFuel(GameTestHelper helper) {
        withFuelUse(helper, 0.0025, 1.0, () -> {
            var tile = small(helper, 1);
            helper.assertTrue(tile.hasRemainedPower(), "One fuel must satisfy the fractional tick threshold");
            tick(helper, tile);
            helper.assertTrue(tile.getPowerConsumed() == 48 && tile.getPowerRemained() == 1,
                    "The first fractional tick must progress without draining a whole fuel unit");
        });
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "config_consumers")
    public static void smallFractionalFuelUseDoesNotBuildWithEmptyGauge(GameTestHelper helper) {
        withFuelUse(helper, 0.0025, 1.0, () -> {
            var tile = small(helper, 0);
            helper.assertTrue(!tile.hasRemainedPower(), "An empty gauge must not satisfy a positive fuel cost");
            tick(helper, tile);
            helper.assertTrue(tile.getPowerConsumed() == 0 && tile.getPowerRemained() == 0,
                    "Fractional fuel use must not build for free with an empty gauge");
        });
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "config_consumers")
    public static void largeHalfFuelUsePreservesBuildSpeed(GameTestHelper helper) {
        withFuelUse(helper, 1.0, 0.5, () -> {
            var tile = large(helper, 10000);
            tick(helper, tile);
            helper.assertTrue(tile.getPowerConsumed() == 48 && tile.getPowerRemained() == 9976,
                    "Large shipyard half fuel use must add 48 progress and drain 24 fuel");
        });
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "config_consumers")
    public static void fuelUseReloadChangesExistingAndNewSmallShipyards(GameTestHelper helper) {
        withFuelUse(helper, 1.0, 1.0, () -> {
            var existing = small(helper, 10000);
            tick(helper, existing);
            helper.assertTrue(existing.getPowerConsumed() == 48 && existing.getPowerRemained() == 9952,
                    "Default fuel-use fixture must retain the original cost");
            ConfigHandler.COMMON.tileShipyardSmallFuelUse.set(0.5);
            fireReload();
            helper.assertTrue(ConfigHandler.tileShipyardSmallFuelUse == 0.5, "Reload must sync small fuel use");
            tick(helper, existing);
            helper.assertTrue(existing.getPowerConsumed() == 96 && existing.getPowerRemained() == 9928,
                    "Reload must change fuel use on the already-running small shipyard");
            var fresh = small(helper, 10000);
            tick(helper, fresh);
            helper.assertTrue(fresh.getPowerConsumed() == 48 && fresh.getPowerRemained() == 9976,
                    "A new small shipyard must also use the reloaded multiplier");
        });
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "config_consumers")
    public static void fuelUseReloadChangesExistingAndNewLargeShipyards(GameTestHelper helper) {
        withFuelUse(helper, 1.0, 1.0, () -> {
            var existing = large(helper, 10000);
            tick(helper, existing);
            helper.assertTrue(existing.getPowerConsumed() == 48 && existing.getPowerRemained() == 9952,
                    "Default large fuel-use fixture must retain the original cost");
            ConfigHandler.COMMON.tileShipyardLargeFuelUse.set(0.5);
            fireReload();
            helper.assertTrue(ConfigHandler.tileShipyardLargeFuelUse == 0.5, "Reload must sync large fuel use");
            tick(helper, existing);
            helper.assertTrue(existing.getPowerConsumed() == 96 && existing.getPowerRemained() == 9928,
                    "Reload must change fuel use on the already-running large shipyard");
            var fresh = large(helper, 10000);
            tick(helper, fresh);
            helper.assertTrue(fresh.getPowerConsumed() == 48 && fresh.getPowerRemained() == 9976,
                    "A new large shipyard must also use the reloaded multiplier");
        });
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "config_consumers")
    public static void nonpositiveBuildSpeedsKeepOriginalFuelAndProgressCalculation(GameTestHelper helper) {
        withFuelUse(helper, 0.5, 0.5, () -> {
            for (int speed : new int[]{0, -48}) {
                ConfigHandler.COMMON.tileShipyardSmallConfig.set(List.of(460800D, (double) speed, 1D));
                ConfigHandler.COMMON.tileShipyardLargeConfig.set(List.of(1382400D, (double) speed, 1D));
                fireReload();
                helper.assertTrue(ConfigHandler.tileShipyardSmall[1] == speed
                                && ConfigHandler.tileShipyardLarge[1] == speed,
                        "Nonpositive speed fixture must apply to both shipyards");
                var small = small(helper, 10000);
                var large = large(helper, 10000);
                tick(helper, small);
                tick(helper, large);
                helper.assertTrue(small.getPowerConsumed() == speed && small.getPowerRemained() == 10000 - speed
                                && large.getPowerConsumed() == speed && large.getPowerRemained() == 10000 - speed,
                        "Nonpositive speed must ignore fuel-use multipliers and preserve the original calculation");
            }
        });
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "config_consumers")
    public static void increasedFuelUseRequiresWholeTickCost(GameTestHelper helper) {
        withFuelUse(helper, 1.01, 1.01, () -> {
            var small = small(helper, 48);
            var large = large(helper, 48);
            helper.assertTrue(!small.hasRemainedPower() && !large.hasRemainedPower(),
                    "48.48 fuel per tick must require at least 49 fuel");
            tick(helper, small);
            tick(helper, large);
            helper.assertTrue(small.getPowerConsumed() == 0 && large.getPowerConsumed() == 0,
                    "An underfunded tick must not progress");
            small.setPowerRemained(49);
            large.setPowerRemained(49);
            tick(helper, small);
            tick(helper, large);
            helper.assertTrue(small.getPowerConsumed() == 48 && small.getPowerRemained() == 1
                            && large.getPowerConsumed() == 48 && large.getPowerRemained() == 1,
                    "A funded tick must progress and never overdraw fuel");
        });
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "config_consumers")
    public static void fractionalCarrySkipsInstantTicksAndIsNotSaved(GameTestHelper helper) {
        withFuelUse(helper, 0.0025, 0.0025, () -> {
            var small = small(helper, 10000);
            var large = large(helper, 10000);
            for (int i = 0; i < 8; i++) {
                tick(helper, small);
                tick(helper, large);
            }
            var loadedSmall = small(helper, 0);
            var loadedLarge = large(helper, 0);
            loadedSmall.load(small.saveWithoutMetadata());
            loadedLarge.load(large.saveWithoutMetadata());
            tick(helper, loadedSmall);
            tick(helper, loadedLarge);
            helper.assertTrue(loadedSmall.getPowerConsumed() == 432 && loadedSmall.getPowerRemained() == 10000
                            && loadedLarge.getPowerConsumed() == 432 && loadedLarge.getPowerRemained() == 10000,
                    "Loading must preserve progress and whole fuel but not the 0.96 fractional carry");
            small.getInventory().setStackInSlot(4, new ItemStack(ModItems.INSTANT_CON_MAT.get()));
            large.getInventory().setStackInSlot(1, new ItemStack(ModItems.INSTANT_CON_MAT.get()));
            tick(helper, small);
            tick(helper, large);
            helper.assertTrue(small.getPowerConsumed() == 57984 && small.getPowerRemained() == 10000
                            && large.getPowerConsumed() == 57984 && large.getPowerRemained() == 10000,
                    "Instant construction must add only its original progress, without draining fuel or carry");
            tick(helper, small);
            tick(helper, large);
            helper.assertTrue(small.getPowerConsumed() == 58032 && small.getPowerRemained() == 9999
                            && large.getPowerConsumed() == 58032 && large.getPowerRemained() == 9999,
                    "The next normal tick must resume the unchanged fractional carry");
        });
        helper.succeed();
    }

    private static TileEntitySmallShipyard small(GameTestHelper helper, int fuel) {
        var tile = new TileEntitySmallShipyard(helper.absolutePos(new BlockPos(1, 2, 1)),
                ModBlocks.SMALL_SHIPYARD.get().defaultBlockState());
        tile.setLevel(helper.getLevel());
        tile.setBuildType(1);
        for (int i = 0; i < 4; i++) {
            tile.setMatBuild(i, 64);
            tile.setMatStock(i, 1000);
            helper.assertTrue(tile.getMatBuild(i) == 64 && tile.getMatStock(i) == 1000,
                    "Small material fixture must apply");
        }
        tile.setPowerRemained(fuel);
        helper.assertTrue(tile.getBuildType() == 1 && tile.getPowerRemained() == fuel,
                "Small build and fuel fixture must apply");
        return tile;
    }

    private static TileMultiGrudgeHeavy large(GameTestHelper helper, int fuel) {
        var tile = new TileMultiGrudgeHeavy(helper.absolutePos(new BlockPos(2, 2, 2)),
                ModBlocks.GRUDGE_HEAVY.get().defaultBlockState());
        tile.setLevel(helper.getLevel());
        tile.setBuildType(1);
        for (int i = 0; i < 4; i++) {
            tile.setMatBuild(i, 1000);
            tile.setMatStock(i, 1000);
            helper.assertTrue(tile.getMatBuild(i) == 1000 && tile.getMatStock(i) == 1000,
                    "Large material fixture must apply");
        }
        tile.setPowerRemained(fuel);
        helper.assertTrue(tile.getBuildType() == 1 && tile.getPowerRemained() == fuel,
                "Large build and fuel fixture must apply");
        return tile;
    }

    private static void tick(GameTestHelper helper, TileEntitySmallShipyard tile) {
        TileEntitySmallShipyard.serverTick(helper.getLevel(), tile.getBlockPos(), tile.getBlockState(), tile);
    }

    private static void tick(GameTestHelper helper, TileMultiGrudgeHeavy tile) {
        TileMultiGrudgeHeavy.serverTick(helper.getLevel(), tile.getBlockPos(), tile.getBlockState(), tile);
    }

    private static void withFuelUse(GameTestHelper helper, double smallUse, double largeUse, Runnable checks) {
        var smallConfig = ConfigHandler.COMMON.tileShipyardSmallConfig;
        var largeConfig = ConfigHandler.COMMON.tileShipyardLargeConfig;
        var smallFuel = ConfigHandler.COMMON.tileShipyardSmallFuelUse;
        var largeFuel = ConfigHandler.COMMON.tileShipyardLargeFuelUse;
        var oldSmall = smallConfig.get();
        var oldLarge = largeConfig.get();
        double oldSmallFuel = smallFuel.get();
        double oldLargeFuel = largeFuel.get();
        TileEntitySmallShipyard.reloadConfig();
        TileMultiGrudgeHeavy.reloadConfig();
        try {
            smallConfig.set(List.of(460800D, 48D, 1D));
            largeConfig.set(List.of(1382400D, 48D, 1D));
            smallFuel.set(smallUse);
            largeFuel.set(largeUse);
            fireReload();
            helper.assertTrue(ConfigHandler.tileShipyardSmall[1] == 48 && ConfigHandler.tileShipyardLarge[1] == 48
                            && ConfigHandler.tileShipyardSmallFuelUse == smallUse
                            && ConfigHandler.tileShipyardLargeFuelUse == largeUse,
                    "Fuel-use and default-speed fixture must sync through the production reload event");
            checks.run();
        } finally {
            smallConfig.set(oldSmall);
            largeConfig.set(oldLarge);
            smallFuel.set(oldSmallFuel);
            largeFuel.set(oldLargeFuel);
            fireReload();
            TileEntitySmallShipyard.reloadConfig();
            TileMultiGrudgeHeavy.reloadConfig();
        }
    }

    private static void fireReload() {
        ModConfig config = ConfigTracker.INSTANCE.configSets().get(ModConfig.Type.COMMON).stream()
                .filter(c -> c.getSpec() == ConfigHandler.COMMON_SPEC).findFirst().orElseThrow();
        ModList.get().getModContainerById(Reference.MOD_ID).orElseThrow()
                .dispatchConfigEvent(new ModConfigEvent.Reloading(config));
    }
}
