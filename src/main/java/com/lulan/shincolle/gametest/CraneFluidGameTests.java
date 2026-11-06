package com.lulan.shincolle.gametest;

import com.lulan.shincolle.init.ModBlocks;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;
import com.lulan.shincolle.tileentity.TileEntityCrane;
import com.lulan.shincolle.utility.CraneFluidTransfer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CraneFluidGameTests {
    private CraneFluidGameTests() { }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void craneInternalFluidTransferPreservesOverflow(GameTestHelper helper) {
        TileEntityCrane restored = CraneCapacityGameTests.restoreWithSmallerCapacity(helper);
        FluidTank tank = (FluidTank) restored.getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.NORTH)
                .orElseThrow(IllegalStateException::new);
        CraneFluidTransfer.Result input = CraneFluidTransfer.move(new ItemStack(Items.WATER_BUCKET), tank, true, 1000);
        helper.assertTrue(input.moved() == 0 && input.container().is(Items.WATER_BUCKET) && restored.getFluidAmount() == 3000,
                "The shared chest/ship transfer must not consume a filled bucket or buffered overflow on input.");
        CraneFluidTransfer.Result output = CraneFluidTransfer.move(new ItemStack(Items.BUCKET), tank, false, 1000);
        helper.assertTrue(output.moved() == 1000 && output.container().is(Items.WATER_BUCKET) && restored.getFluidAmount() == 2000,
                "The shared chest/ship transfer must still be able to extract overflow into a bucket.");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void craneWaterBucketsLoadUnloadAndCompleteFullMode(GameTestHelper helper) {
        GameTestEntities.whenPositionsTicking(helper, () -> {
            try (GameTestEntities entities = GameTestEntities.open(helper)) {
                CraneResourceFixture f = CraneResourceFixture.open(helper, entities, 2);
                f.crane().setLiquidMode(1);
                f.crane().setCraneMode(1);
                f.chest().setItem(0, new ItemStack(Items.WATER_BUCKET));
                f.ship().getCapaShipInventory().setStackInSlot(6, new ItemStack(Items.BUCKET));
                f.tick(80);
                helper.assertTrue(f.chest().getItem(0).is(Items.BUCKET)
                                && f.ship().getCapaShipInventory().getStackInSlot(6).is(Items.WATER_BUCKET)
                                && f.crane().getFluidAmount() == 0 && f.ship().getStateMinor(ID.M.CraneState) == 0,
                        "Loading must replace both buckets, conserve water and finish when the ship's container is full.");
                f.working();
                f.crane().setLiquidMode(2);
                f.tick(16);
                helper.assertTrue(f.crane().getFluidAmount() == 1000 && f.crane().getDockedShip() == f.ship(),
                        "Unloading first drains the ship after the chest pre-transfer; full mode must still wait.");
                f.tick(16);
                helper.assertTrue(f.chest().getItem(0).is(Items.WATER_BUCKET)
                                && f.ship().getCapaShipInventory().getStackInSlot(6).is(Items.BUCKET)
                                && f.crane().getFluidAmount() == 0 && f.ship().getStateMinor(ID.M.CraneState) == 0,
                        "The next pre-transfer must fill the chest container and finish full mode without losing water.");
                helper.succeed();
            }
        }, CraneResourceFixture.POSITION);
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void craneZeroPumpsCannotFillShipContainers(GameTestHelper helper) {
        GameTestEntities.whenPositionsTicking(helper, () -> {
            try (GameTestEntities entities = GameTestEntities.open(helper)) {
                CraneResourceFixture f = CraneResourceFixture.open(helper, entities, 0);
                f.crane().setLiquidMode(1);
                f.crane().setCraneMode(0);
                f.chest().setItem(0, new ItemStack(Items.WATER_BUCKET));
                f.ship().getCapaShipInventory().setStackInSlot(6, new ItemStack(Items.BUCKET));
                f.tick(80);
                helper.assertTrue(f.crane().getFluidAmount() == 1000
                                && f.ship().getCapaShipInventory().getStackInSlot(6).is(Items.BUCKET)
                                && f.ship().getStateMinor(ID.M.CraneState) == 0,
                        "Chest pre-transfer may buffer water, but a zero-rate ship must not receive it or prolong no-work mode.");
                helper.succeed();
            }
        }, CraneResourceFixture.POSITION);
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void craneFluidCapabilityPersistsAndHonorsSidesAndLifecycle(GameTestHelper helper) {
        TileEntityCrane tile = new TileEntityCrane(BlockPos.ZERO, ModBlocks.CRANE.get().defaultBlockState());
        var capability = tile.getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.NORTH);
        helper.assertTrue(capability.isPresent()
                        && !tile.getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.UP).isPresent()
                        && !tile.getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.DOWN).isPresent(),
                "The fluid capability must be horizontal, not exposed above or below the crane.");
        var fluid = capability.orElseThrow(IllegalStateException::new);
        helper.assertTrue(fluid.fill(new FluidStack(Fluids.WATER, 1000), FluidAction.SIMULATE) == 1000
                        && tile.getFluidAmount() == 0, "Simulated input must not change the buffer.");
        fluid.fill(new FluidStack(Fluids.WATER, 1000), FluidAction.EXECUTE);
        TileEntityCrane restored = new TileEntityCrane(BlockPos.ZERO, ModBlocks.CRANE.get().defaultBlockState());
        restored.load(tile.saveWithFullMetadata());
        helper.assertTrue(restored.getFluidAmount() == 1000 && restored.getFluidCapacity() == tile.getFluidCapacity(),
                "Fluid buffer must survive a save/load round trip.");
        tile.invalidateCaps();
        helper.assertTrue(!capability.isPresent(), "Removal must invalidate existing fluid handles.");
        tile.reviveCaps();
        helper.assertTrue(tile.getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.NORTH).isPresent(),
                "Revival must expose a fresh fluid handle.");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void craneFluidEmptyModeChecksShipSupply(GameTestHelper helper) {
        GameTestEntities.whenPositionsTicking(helper, () -> {
            try (GameTestEntities entities = GameTestEntities.open(helper)) {
                CraneResourceFixture f = CraneResourceFixture.open(helper, entities, 2);
                f.crane().setLiquidMode(2);
                f.crane().setCraneMode(2);
                f.ship().getCapaShipInventory().setStackInSlot(6, new ItemStack(Items.WATER_BUCKET));
                f.tick(80);
                helper.assertTrue(f.crane().getFluidAmount() == 1000 && f.ship().getStateMinor(ID.M.CraneState) == 0
                                && f.ship().getCapaShipInventory().getStackInSlot(6).is(Items.BUCKET),
                        "Empty mode must complete after the ship supply drains, leaving buffered water for the chest.");
                helper.succeed();
            }
        }, CraneResourceFixture.POSITION);
    }
}
