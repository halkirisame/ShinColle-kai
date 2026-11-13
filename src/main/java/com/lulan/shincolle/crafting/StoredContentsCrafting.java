package com.lulan.shincolle.crafting;

import com.lulan.shincolle.block.ItemBlockGrudgeHeavy;
import com.lulan.shincolle.item.ShipTank;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;

/** Preserves stored contents at the crafting boundary without changing the inputs. */
final class StoredContentsCrafting {
    private StoredContentsCrafting() { }

    static ItemStack assemble(CraftingContainer grid, ItemStack result) {
        FluidStack combined = FluidStack.EMPTY;
        for (int i = 0; i < grid.getContainerSize(); i++) {
            ItemStack input = grid.getItem(i);
            if (input.getItem() instanceof ItemBlockGrudgeHeavy && ItemBlockGrudgeHeavy.hasStoredContents(input)) {
                return ItemStack.EMPTY;
            }
            if (input.getItem() instanceof ShipTank) {
                IFluidHandlerItem source = input.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM).orElse(null);
                if (source == null) return ItemStack.EMPTY;
                FluidStack fluid = source.getFluidInTank(0);
                if (fluid.isEmpty()) continue;
                if (!(result.getItem() instanceof ShipTank tank)) return ItemStack.EMPTY;
                if (!combined.isEmpty() && !combined.isFluidEqual(fluid)) return ItemStack.EMPTY;
                if (fluid.getAmount() > tank.getTankCapacity() - combined.getAmount()) return ItemStack.EMPTY;
                if (combined.isEmpty()) {
                    combined = fluid.copy();
                } else {
                    combined.grow(fluid.getAmount());
                }
            }
        }
        if (!combined.isEmpty()) {
            IFluidHandlerItem target = result.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM).orElse(null);
            if (target == null || target.fill(combined, IFluidHandler.FluidAction.EXECUTE) != combined.getAmount()) {
                return ItemStack.EMPTY;
            }
        }
        return result;
    }
}
