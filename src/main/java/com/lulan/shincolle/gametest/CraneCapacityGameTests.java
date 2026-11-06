package com.lulan.shincolle.gametest;

import com.lulan.shincolle.handler.ConfigHandler;
import com.lulan.shincolle.init.ModBlocks;
import com.lulan.shincolle.reference.Reference;
import com.lulan.shincolle.tileentity.TileEntityCrane;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CraneCapacityGameTests {
    private CraneCapacityGameTests() { }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void craneFluidCapacityShrinkPreservesOverflowAndRejectsInput(GameTestHelper helper) {
        TileEntityCrane restored = restoreWithSmallerCapacity(helper);
        var fluid = restored.getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.NORTH)
                .orElseThrow(IllegalStateException::new);
        helper.assertTrue(fluid.fill(new FluidStack(Fluids.WATER, 100), FluidAction.SIMULATE) == 0
                        && restored.getFluidAmount() == 3000,
                "Simulated input above capacity must return zero without changing saved water.");
        helper.assertTrue(fluid.fill(new FluidStack(Fluids.WATER, 100), FluidAction.EXECUTE) == 0
                        && restored.getFluidAmount() == 3000,
                "Executed input above capacity must return zero without discarding saved water.");
        helper.assertTrue(fluid.drain(2500, FluidAction.SIMULATE).getAmount() == 2500
                        && restored.getFluidAmount() == 3000,
                "Simulated extraction must include the overflow without changing it.");
        helper.assertTrue(fluid.drain(new FluidStack(Fluids.WATER, 2500), FluidAction.EXECUTE).getAmount() == 2500
                        && restored.getFluidAmount() == 500,
                "Extraction must make all overflow available, not just the new capacity.");
        helper.assertTrue(fluid.fill(new FluidStack(Fluids.WATER, 1000), FluidAction.SIMULATE) == 500
                        && restored.getFluidAmount() == 500,
                "Input simulation must use only the remaining space once below capacity.");
        helper.assertTrue(fluid.fill(new FluidStack(Fluids.WATER, 1000), FluidAction.EXECUTE) == 500
                        && restored.getFluidAmount() == 1000,
                "Input must resume normally without exceeding the new capacity.");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void craneEnergyCapacityShrinkPreservesOverflowAndRejectsInput(GameTestHelper helper) {
        TileEntityCrane restored = restoreWithSmallerCapacity(helper);
        var energy = restored.getCapability(ForgeCapabilities.ENERGY, Direction.NORTH)
                .orElseThrow(IllegalStateException::new);
        helper.assertTrue(energy.receiveEnergy(100, true) == 0 && restored.getEnergyAmount() == 3000,
                "Simulated input above capacity must return zero without changing saved FE.");
        helper.assertTrue(energy.receiveEnergy(100, false) == 0 && restored.getEnergyAmount() == 3000,
                "Executed input above capacity must return zero without discarding saved FE.");
        helper.assertTrue(energy.extractEnergy(1000, true) == 1000 && restored.getEnergyAmount() == 3000,
                "Simulated extraction must include the FE overflow without changing it.");
        helper.assertTrue(energy.extractEnergy(1000, false) == 1000 && restored.getEnergyAmount() == 2000,
                "FE extraction must retain its normal per-call limit while consuming overflow.");
        helper.assertTrue(energy.extractEnergy(1000, false) == 1000 && restored.getEnergyAmount() == 1000
                        && energy.receiveEnergy(100, false) == 0 && restored.getEnergyAmount() == 1000,
                "Input must stay blocked until extraction brings FE strictly below capacity.");
        helper.assertTrue(energy.extractEnergy(500, false) == 500 && restored.getEnergyAmount() == 500,
                "Extraction must make all FE overflow available, not just the new capacity.");
        helper.assertTrue(energy.receiveEnergy(1000, true) == 500 && restored.getEnergyAmount() == 500,
                "FE simulation must use only the remaining space once below capacity.");
        helper.assertTrue(energy.receiveEnergy(1000, false) == 500 && restored.getEnergyAmount() == 1000,
                "FE input must resume normally without exceeding the new capacity.");
        helper.succeed();
    }

    static TileEntityCrane restoreWithSmallerCapacity(GameTestHelper helper) {
        int[] previous = ConfigHandler.tileCrane;
        try {
            ConfigHandler.tileCrane = new int[] {4000, 4000};
            TileEntityCrane original = new TileEntityCrane(BlockPos.ZERO, ModBlocks.CRANE.get().defaultBlockState());
            int water = original.getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.NORTH)
                    .orElseThrow(IllegalStateException::new).fill(new FluidStack(Fluids.WATER, 3000), FluidAction.EXECUTE);
            int energy = original.getCapability(ForgeCapabilities.ENERGY, Direction.NORTH)
                    .orElseThrow(IllegalStateException::new).receiveEnergy(3000, false);
            helper.assertTrue(original.getFluidCapacity() == 4000 && original.getEnergyCapacity() == 4000
                            && water == 3000 && energy == 3000,
                    "The larger-capacity fixture must actually accept all resources before saving.");
            CompoundTag saved = original.saveWithFullMetadata();
            ConfigHandler.tileCrane = new int[] {1000, 1000};
            TileEntityCrane restored = new TileEntityCrane(BlockPos.ZERO, ModBlocks.CRANE.get().defaultBlockState());
            restored.load(saved);
            helper.assertTrue(restored.getFluidCapacity() == 1000 && restored.getEnergyCapacity() == 1000
                            && restored.getFluidAmount() == 3000 && restored.getEnergyAmount() == 3000,
                    "Loading with smaller capacities must retain all saved water and FE.");
            TileEntityCrane savedAgain = new TileEntityCrane(BlockPos.ZERO, ModBlocks.CRANE.get().defaultBlockState());
            savedAgain.load(restored.saveWithFullMetadata());
            helper.assertTrue(savedAgain.getFluidAmount() == 3000 && savedAgain.getEnergyAmount() == 3000,
                    "Saving the overflowing crane again must not truncate either resource.");
            return savedAgain;
        } finally {
            ConfigHandler.tileCrane = previous;
        }
    }
}
