package com.lulan.shincolle.gametest;

import com.lulan.shincolle.client.gui.inventory.ContainerLargeShipyard;
import com.lulan.shincolle.client.gui.inventory.ContainerSmallShipyard;
import com.lulan.shincolle.handler.ConfigHandler;
import com.lulan.shincolle.init.ModBlocks;
import com.lulan.shincolle.init.ModItems;
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
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.config.ConfigTracker;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

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

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void largeAcceptsOnlySingleWholeLavaFuelContainers(GameTestHelper helper) {
        var tile = large(helper, 0);
        helper.assertTrue(tile.isItemValidForSlot(1, filledShipTank(helper, Fluids.LAVA, 2500)),
                "Large input must accept a single tank with at least one lava bucket");
        helper.assertTrue(!tile.isItemValidForSlot(1, filledShipTank(helper, Fluids.LAVA, 500))
                        && !tile.isItemValidForSlot(1, filledShipTank(helper, Fluids.WATER, 2000)),
                "Partial lava and water must not be accepted as fuel containers");
        var stacked = filledShipTank(helper, Fluids.LAVA, 2000);
        stacked.setCount(2);
        helper.assertTrue(!tile.isItemValidForSlot(1, stacked), "Stacked fuel containers must be rejected");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "config_consumers")
    public static void largeLavaContainerDrainsOneBucketPerTickAndKeepsRemainder(GameTestHelper helper) {
        withFuelValues(helper, 1, 1, () -> {
            var tile = large(helper, 0);
            tile.getInventory().setStackInSlot(1, filledShipTank(helper, Fluids.LAVA, 2500));
            tick(helper, tile);
            helper.assertTrue(tile.getPowerRemained() == (int) (20000 * tile.getFuelMagni())
                            && remainingFluid(tile, 1) == 1500
                            && tile.getInventory().getStackInSlot(1).is(ModItems.SHIP_TANK.get()),
                    "First tick must consume 1000mB and retain the tank with 1500mB");
            tick(helper, tile);
            helper.assertTrue(tile.getPowerRemained() == 40000 && remainingFluid(tile, 1) == 500,
                    "Second tick must consume exactly one more bucket");
            tick(helper, tile);
            helper.assertTrue(tile.getPowerRemained() == 40000 && remainingFluid(tile, 1) == 500,
                    "Sub-bucket remainder must not be consumed");
        });
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "config_consumers")
    public static void largeFullFuelRetainsLavaContainerContents(GameTestHelper helper) {
        withFuelValues(helper, 1, 1, () -> {
            for (int excess : new int[]{0, 1}) {
                var tile = large(helper, 1382400 + excess);
                tile.getInventory().setStackInSlot(1, filledShipTank(helper, Fluids.LAVA, 2500));
                tick(helper, tile);
                helper.assertTrue(tile.getPowerRemained() == 1382400 + excess && remainingFluid(tile, 1) == 2500,
                        "Full or overflow storage must not drain the lava container");
            }
        });
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void largeAutomationExtractsSpentContainersButRetainsFuelAndResources(GameTestHelper helper) {
        var tile = large(helper, 0);
        var inventory = tile.getInventory();
        inventory.setStackInSlot(1, filledShipTank(helper, Fluids.LAVA, 500));
        inventory.setStackInSlot(2, new ItemStack(Items.BUCKET));
        inventory.setStackInSlot(3, filledShipTank(helper, Fluids.LAVA, 2500));
        inventory.setStackInSlot(4, new ItemStack(Items.COAL));
        inventory.setStackInSlot(5, new ItemStack(ModItems.GRUDGE.get()));
        inventory.setStackInSlot(6, new ItemStack(ModItems.SHIP_SPAWN_EGG.get()));
        inventory.setStackInSlot(7, new ItemStack(ModItems.INSTANT_CON_MAT.get()));
        var automation = tile.getCapability(ForgeCapabilities.ITEM_HANDLER).orElseThrow(IllegalStateException::new);
        helper.assertTrue(automation.extractItem(1, 1, true).is(ModItems.SHIP_TANK.get())
                        && remainingFluid(tile, 1) == 500,
                "Simulated extraction must expose but not mutate a spent tank");
        helper.assertTrue(automation.extractItem(1, 1, false).is(ModItems.SHIP_TANK.get())
                        && automation.extractItem(2, 1, false).is(Items.BUCKET)
                        && inventory.getStackInSlot(1).isEmpty() && inventory.getStackInSlot(2).isEmpty(),
                "Recycle mode must let automation remove partial tanks and empty buckets");
        for (int slot = 3; slot <= 7; slot++) {
            helper.assertTrue(automation.extractItem(slot, 64, false).isEmpty(),
                    "Automation must retain usable fuel, resources, eggs and construction material in slot " + slot);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "config_consumers")
    public static void largeLavaContainersRespectFuelMultiplierAndSaturation(GameTestHelper helper) {
        withFuelValues(helper, 1, 1, () -> {
            var tile = large(helper, 1382399);
            tile.getInventory().setStackInSlot(1, filledShipTank(helper, Fluids.LAVA, 2000));
            tick(helper, tile);
            helper.assertTrue(tile.getPowerRemained() == 1402399 && remainingFluid(tile, 1) == 1000,
                    "One unit of capacity must permit a full bucket's fuel value to overflow");
            tick(helper, tile);
            helper.assertTrue(tile.getPowerRemained() == 1402399 && remainingFluid(tile, 1) == 1000,
                    "Overflow must prevent further container intake");
        });
        withFuelValues(helper, 1, 1e9, () -> {
            var tile = large(helper, 1);
            tile.getInventory().setStackInSlot(1, filledShipTank(helper, Fluids.LAVA, 1000));
            tick(helper, tile);
            helper.assertTrue(tile.getPowerRemained() == Integer.MAX_VALUE && remainingFluid(tile, 1) == 0,
                    "Extreme multiplier must saturate without wrapping and retain the empty tank");
        });
        for (double multiplier : new double[]{0, -1}) {
            withFuelValues(helper, 1, multiplier, () -> {
                var tile = large(helper, 0);
                tile.getInventory().setStackInSlot(1, filledShipTank(helper, Fluids.LAVA, 1000));
                tick(helper, tile);
                helper.assertTrue(tile.getPowerRemained() == 0 && remainingFluid(tile, 1) == 1000,
                        "Nonpositive fuel value must not consume a container's contents");
            });
        }
        helper.succeed();
    }

    private static ItemStack filledShipTank(GameTestHelper helper,
                                           net.minecraft.world.level.material.Fluid fluid, int amount) {
        var handler = FluidUtil.getFluidHandler(new ItemStack(ModItems.SHIP_TANK.get()))
                .orElseThrow(IllegalStateException::new);
        helper.assertTrue(handler.fill(new FluidStack(fluid, amount), IFluidHandler.FluidAction.EXECUTE) == amount
                        && handler.getFluidInTank(0).getAmount() == amount,
                "Ship-tank fixture must contain the requested fluid amount");
        return handler.getContainer();
    }

    private static int remainingFluid(TileMultiGrudgeHeavy tile, int slot) {
        return FluidUtil.getFluidHandler(tile.getInventory().getStackInSlot(slot)).orElseThrow(IllegalStateException::new)
                .getFluidInTank(0).getAmount();
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
        tile.setCorePos(tile.getBlockPos());
        helper.assertTrue(tile.hasCorePos(), "Large fuel-intake fixture must represent a formed core");
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
        try {
            ConfigConsumerGameTests.setCached(ConfigHandler.tileShipyardSmall, 460800D, 48D, smallValue);
            ConfigConsumerGameTests.setCached(ConfigHandler.tileShipyardLarge, 1382400D, 48D, largeValue);
            TileEntitySmallShipyard.reloadConfig();
            TileMultiGrudgeHeavy.reloadConfig();
            helper.assertTrue(small(helper, 0).getFuelMagni() == (float) smallValue
                            && large(helper, 0).getFuelMagni() == (float) largeValue,
                    "Fuel-value fixture must reach the tile statics");
            checks.run();
        } finally {
            // The production reload event restores the cache from the config and reloads the tiles.
            fireReload();
        }
    }

    private static void fireReload() {
        ModConfig config = ConfigTracker.INSTANCE.configSets().get(ModConfig.Type.COMMON).stream()
                .filter(c -> c.getSpec() == ConfigHandler.COMMON_SPEC).findFirst().orElseThrow();
        ModList.get().getModContainerById(Reference.MOD_ID).orElseThrow()
                .dispatchConfigEvent(new ModConfigEvent.Reloading(config));
    }
}
