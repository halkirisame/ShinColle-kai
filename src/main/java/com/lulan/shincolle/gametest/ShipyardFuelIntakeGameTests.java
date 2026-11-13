package com.lulan.shincolle.gametest;

import com.lulan.shincolle.client.gui.inventory.ContainerLargeShipyard;
import com.lulan.shincolle.client.gui.inventory.ContainerSmallShipyard;
import com.lulan.shincolle.handler.ConfigHandler;
import com.lulan.shincolle.init.ModBlocks;
import com.lulan.shincolle.reference.Reference;
import com.lulan.shincolle.tileentity.TileEntitySmallShipyard;
import com.lulan.shincolle.tileentity.TileMultiGrudgeHeavy;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.config.ConfigTracker;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ShipyardFuelIntakeGameTests {
    private ShipyardFuelIntakeGameTests() { }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "config_consumers")
    public static void smallHighMultiplierCoalRetainsOverflow(GameTestHelper helper) {
        withFuelValues(helper, 400, 1, () -> {
            var tile = small(helper, 0);
            tile.getInventory().setStackInSlot(4, new ItemStack(Items.COAL));
            tick(helper, tile);
            helper.assertTrue(tile.getInventory().getStackInSlot(4).isEmpty()
                            && tile.getPowerRemained() == 640000,
                    "One coal must add 640000 fuel even though small storage is 460800");
        });
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "config_consumers")
    public static void fullAndOverflowShipyardsRetainFuelInputsAndLava(GameTestHelper helper) {
        withFuelValues(helper, 400, 86.4, () -> {
            for (int excess : new int[]{0, 1}) {
                var small = small(helper, 460800 + excess);
                var large = large(helper, 1382400 + excess);
                small.getInventory().setStackInSlot(0, new ItemStack(Items.COAL));
                small.getInventory().setStackInSlot(4, new ItemStack(Items.LAVA_BUCKET));
                large.getInventory().setStackInSlot(1, new ItemStack(Items.COAL));
                large.getInventory().setStackInSlot(2, new ItemStack(Items.LAVA_BUCKET));
                var smallTank = fillLava(helper, small, 1000);
                var largeTank = fillLava(helper, large, 1000);
                tick(helper, small);
                tick(helper, large);
                helper.assertTrue(small.getPowerRemained() == 460800 + excess
                                && large.getPowerRemained() == 1382400 + excess
                                && small.getInventory().getStackInSlot(0).is(Items.COAL)
                                && small.getInventory().getStackInSlot(4).is(Items.LAVA_BUCKET)
                                && small.getInventory().getStackInSlot(6).isEmpty()
                                && large.getInventory().getStackInSlot(1).is(Items.COAL)
                                && large.getInventory().getStackInSlot(2).is(Items.LAVA_BUCKET)
                                && smallTank.getFluidInTank(0).getAmount() == 1000
                                && largeTank.getFluidInTank(0).getAmount() == 1000,
                        "At or above max, neither shipyard may consume items, containers or tank lava");
            }
        });
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "config_consumers")
    public static void largeHighMultiplierLavaTankRetainsOverflow(GameTestHelper helper) {
        withFuelValues(helper, 1, 86.4, () -> {
            var tile = large(helper, 0);
            var tank = fillLava(helper, tile, 1000);
            tick(helper, tile);
            helper.assertTrue(tank.getFluidInTank(0).isEmpty() && tile.getPowerRemained() == 1728000,
                    "One large lava intake must add 1728000 fuel and empty the tank");
        });
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "config_consumers")
    public static void smallHighMultiplierLavaBucketRetainsOverflow(GameTestHelper helper) {
        withFuelValues(helper, 30, 1, () -> {
            var tile = small(helper, 0);
            tile.getInventory().setStackInSlot(4, new ItemStack(Items.LAVA_BUCKET));
            tick(helper, tile);
            helper.assertTrue(tile.getPowerRemained() == 600000
                            && tile.getInventory().getStackInSlot(4).isEmpty()
                            && tile.getInventory().getStackInSlot(6).is(Items.BUCKET)
                            && fluidHandler(tile).getFluidInTank(0).isEmpty(),
                    "Small lava container must move through the tank, retain 600000 fuel and return its bucket");
        });
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "config_consumers")
    public static void largeHighMultiplierLavaBucketReturnsContainer(GameTestHelper helper) {
        withFuelValues(helper, 1, 86.4, () -> {
            var tile = large(helper, 0);
            tile.getInventory().setStackInSlot(1, new ItemStack(Items.LAVA_BUCKET));
            tick(helper, tile);
            helper.assertTrue(tile.getPowerRemained() == 1728000
                            && tile.getInventory().getStackInSlot(1).is(Items.BUCKET),
                    "Large lava fuel item must retain its whole value and return the bucket in its input slot");
        });
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "config_consumers")
    public static void partialLavaKeepsSmallAndLargeIntakeUnits(GameTestHelper helper) {
        withFuelValues(helper, 30, 86.4, () -> {
            var small = small(helper, 460799);
            var large = large(helper, 0);
            var smallTank = fillLava(helper, small, 500);
            var largeTank = fillLava(helper, large, 500);
            tick(helper, small);
            tick(helper, large);
            helper.assertTrue(small.getPowerRemained() == 760799 && smallTank.getFluidInTank(0).isEmpty()
                            && large.getPowerRemained() == 0 && largeTank.getFluidInTank(0).getAmount() == 500,
                    "Small may drain 500mB across max; large must still wait for a whole 1000mB");
        });
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "config_consumers")
    public static void defaultMultiplierAcceptsOneItemNearCapacity(GameTestHelper helper) {
        withFuelValues(helper, 1, 1, () -> {
            var small = small(helper, 460799);
            var large = large(helper, 1382399);
            small.getInventory().setStackInSlot(4, new ItemStack(Items.COAL, 2));
            large.getInventory().setStackInSlot(1, new ItemStack(Items.COAL, 2));
            tick(helper, small);
            tick(helper, large);
            helper.assertTrue(small.getPowerRemained() == 462399 && large.getPowerRemained() == 1383999
                            && small.getInventory().getStackInSlot(4).getCount() == 1
                            && large.getInventory().getStackInSlot(1).getCount() == 1,
                    "One remaining unit of room must accept exactly one coal at default multipliers");
            tick(helper, small);
            tick(helper, large);
            helper.assertTrue(small.getPowerRemained() == 462399 && large.getPowerRemained() == 1383999
                            && small.getInventory().getStackInSlot(4).getCount() == 1
                            && large.getInventory().getStackInSlot(1).getCount() == 1,
                    "Stored overflow must prevent the next intake");
        });
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "config_consumers")
    public static void extremeMultiplierFuelItemsSaturateWithoutWrapping(GameTestHelper helper) {
        withFuelValues(helper, 1e9, 1e9, () -> {
            var small = small(helper, 1);
            var large = large(helper, 1);
            small.getInventory().setStackInSlot(4, new ItemStack(Items.COAL));
            large.getInventory().setStackInSlot(1, new ItemStack(Items.COAL));
            tick(helper, small);
            tick(helper, large);
            helper.assertTrue(small.getPowerRemained() == Integer.MAX_VALUE
                            && large.getPowerRemained() == Integer.MAX_VALUE
                            && small.getInventory().getStackInSlot(4).isEmpty()
                            && large.getInventory().getStackInSlot(1).isEmpty(),
                    "Adding a saturated fuel item to nonzero storage must stop at int max, not wrap");
        });
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "config_consumers")
    public static void extremeMultiplierLavaTanksSaturateWithoutWrapping(GameTestHelper helper) {
        withFuelValues(helper, 1e9, 1e9, () -> {
            var small = small(helper, 1);
            var large = large(helper, 1);
            var smallTank = fillLava(helper, small, 1000);
            var largeTank = fillLava(helper, large, 1000);
            tick(helper, small);
            tick(helper, large);
            helper.assertTrue(small.getPowerRemained() == Integer.MAX_VALUE
                            && large.getPowerRemained() == Integer.MAX_VALUE
                            && smallTank.getFluidInTank(0).isEmpty() && largeTank.getFluidInTank(0).isEmpty(),
                    "Adding saturated tank fuel to nonzero storage must stop at int max, not wrap");
        });
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "config_consumers")
    public static void extremeMultiplierLavaContainersSaturateWithoutWrapping(GameTestHelper helper) {
        withFuelValues(helper, 1e9, 1e9, () -> {
            var small = small(helper, 1);
            var large = large(helper, 1);
            small.getInventory().setStackInSlot(4, new ItemStack(Items.LAVA_BUCKET));
            large.getInventory().setStackInSlot(1, new ItemStack(Items.LAVA_BUCKET));
            tick(helper, small);
            tick(helper, large);
            helper.assertTrue(small.getPowerRemained() == Integer.MAX_VALUE
                            && large.getPowerRemained() == Integer.MAX_VALUE
                            && small.getInventory().getStackInSlot(6).is(Items.BUCKET)
                            && large.getInventory().getStackInSlot(1).is(Items.BUCKET),
                    "Extreme lava containers must retain saturated fuel and return empty buckets");
        });
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "config_consumers")
    public static void overflowFuelDisplayClampsForBothShipyards(GameTestHelper helper) {
        withFuelValues(helper, 1, 1, () -> {
            var small = small(helper, 0);
            var large = large(helper, 0);
            var player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(
                    UUID.fromString("b42ea783-5b3f-43fd-8e29-b373a4e749b4"), "fuel_display"));
            var smallMenu = new ContainerSmallShipyard(1, player.getInventory(), small);
            var largeMenu = new ContainerLargeShipyard(2, player.getInventory(), large);
            for (int fuel : new int[]{1728000, Integer.MAX_VALUE}) {
                small.setPowerRemained(fuel);
                large.setPowerRemained(fuel);
                helper.assertTrue(smallMenu.getFuelPercent() == 1000 && largeMenu.getFuelPercent() == 1000
                                && small.getPowerRemainingScaled(52) == 52
                                && large.getPowerRemainingScaled(52) == 52,
                        "Overflow fuel must clamp actual menu data to 1000 and tile bars to their pixel width");
            }
            small.setPowerRemained(230400);
            large.setPowerRemained(691200);
            helper.assertTrue(smallMenu.getFuelPercent() == 500 && largeMenu.getFuelPercent() == 500
                            && small.getPowerRemainingScaled(52) == 26 && large.getPowerRemainingScaled(52) == 26,
                    "Below max, menu percentages and tile bars must still scale proportionally");
        });
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "config_consumers")
    public static void overflowFuelPersistsAcrossSaveAndLoad(GameTestHelper helper) {
        withFuelValues(helper, 1, 1, () -> {
            var small = small(helper, Integer.MAX_VALUE);
            var large = large(helper, Integer.MAX_VALUE);
            var loadedSmall = small(helper, 0);
            var loadedLarge = large(helper, 0);
            loadedSmall.load(small.saveWithoutMetadata());
            loadedLarge.load(large.saveWithoutMetadata());
            helper.assertTrue(loadedSmall.getPowerRemained() == Integer.MAX_VALUE
                            && loadedLarge.getPowerRemained() == Integer.MAX_VALUE,
                    "Save and load must retain overflow fuel rather than clamp it to storage max");
        });
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "config_consumers")
    public static void nonpositiveFuelValuesDoNotConsumeInputs(GameTestHelper helper) {
        for (double multiplier : new double[]{0, -1}) {
            withFuelValues(helper, multiplier, multiplier, () -> {
                var small = small(helper, 0);
                var large = large(helper, 0);
                small.getInventory().setStackInSlot(0, new ItemStack(Items.COAL));
                small.getInventory().setStackInSlot(4, new ItemStack(Items.LAVA_BUCKET));
                large.getInventory().setStackInSlot(1, new ItemStack(Items.COAL));
                large.getInventory().setStackInSlot(2, new ItemStack(Items.LAVA_BUCKET));
                var smallTank = fillLava(helper, small, 1000);
                var largeTank = fillLava(helper, large, 1000);
                tick(helper, small);
                tick(helper, large);
                helper.assertTrue(small.getPowerRemained() == 0 && large.getPowerRemained() == 0
                                && small.getInventory().getStackInSlot(0).is(Items.COAL)
                                && small.getInventory().getStackInSlot(4).is(Items.LAVA_BUCKET)
                                && large.getInventory().getStackInSlot(1).is(Items.COAL)
                                && large.getInventory().getStackInSlot(2).is(Items.LAVA_BUCKET)
                                && smallTank.getFluidInTank(0).getAmount() == 1000
                                && largeTank.getFluidInTank(0).getAmount() == 1000,
                        "Nonpositive fuel values must still reject all intake paths");
            });
        }
        helper.succeed();
    }

    private static TileEntitySmallShipyard small(GameTestHelper helper, int fuel) {
        var tile = new TileEntitySmallShipyard(helper.absolutePos(new BlockPos(1, 2, 1)),
                ModBlocks.SMALL_SHIPYARD.get().defaultBlockState());
        tile.setLevel(helper.getLevel());
        tile.setPowerRemained(fuel);
        helper.assertTrue(tile.getPowerRemained() == fuel && tile.getPowerMax() == 460800
                        && tile.getBuildType() == 0,
                "Small fixture must apply fuel and max and disable building to isolate intake");
        return tile;
    }

    private static TileMultiGrudgeHeavy large(GameTestHelper helper, int fuel) {
        var tile = new TileMultiGrudgeHeavy(helper.absolutePos(new BlockPos(2, 2, 2)),
                ModBlocks.GRUDGE_HEAVY.get().defaultBlockState());
        tile.setLevel(helper.getLevel());
        tile.setPowerRemained(fuel);
        helper.assertTrue(tile.getPowerRemained() == fuel && tile.getPowerMax() == 1382400
                        && tile.getBuildType() == 0 && tile.getInvMode() == 0,
                "Large fixture must apply fuel and max and disable building/release to isolate intake");
        return tile;
    }

    private static IFluidHandler fluidHandler(BlockEntity tile) {
        return tile.getCapability(ForgeCapabilities.FLUID_HANDLER).orElseThrow(IllegalStateException::new);
    }

    private static IFluidHandler fillLava(GameTestHelper helper, BlockEntity tile, int amount) {
        var tank = fluidHandler(tile);
        int filled = tank.fill(new FluidStack(Fluids.LAVA, amount), IFluidHandler.FluidAction.EXECUTE);
        helper.assertTrue(filled == amount && tank.getFluidInTank(0).getAmount() == amount,
                "Lava fixture must fill the requested amount through the exposed capability");
        return tank;
    }

    private static void tick(GameTestHelper helper, TileEntitySmallShipyard tile) {
        TileEntitySmallShipyard.serverTick(helper.getLevel(), tile.getBlockPos(), tile.getBlockState(), tile);
    }

    private static void tick(GameTestHelper helper, TileMultiGrudgeHeavy tile) {
        TileMultiGrudgeHeavy.serverTick(helper.getLevel(), tile.getBlockPos(), tile.getBlockState(), tile);
    }

    private static void withFuelValues(GameTestHelper helper, double smallValue, double largeValue, Runnable checks) {
        var smallConfig = ConfigHandler.COMMON.tileShipyardSmallConfig;
        var largeConfig = ConfigHandler.COMMON.tileShipyardLargeConfig;
        var oldSmall = smallConfig.get();
        var oldLarge = largeConfig.get();
        TileEntitySmallShipyard.reloadConfig();
        TileMultiGrudgeHeavy.reloadConfig();
        try {
            smallConfig.set(List.of(460800D, 48D, smallValue));
            largeConfig.set(List.of(1382400D, 48D, largeValue));
            fireReload();
            helper.assertTrue(ConfigHandler.tileShipyardSmall[2] == smallValue
                            && ConfigHandler.tileShipyardLarge[2] == largeValue
                            && small(helper, 0).getFuelMagni() == (float) smallValue
                            && large(helper, 0).getFuelMagni() == (float) largeValue,
                    "Fuel-value fixture must reach both config cache and tile statics through production reload");
            checks.run();
        } finally {
            smallConfig.set(oldSmall);
            largeConfig.set(oldLarge);
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
