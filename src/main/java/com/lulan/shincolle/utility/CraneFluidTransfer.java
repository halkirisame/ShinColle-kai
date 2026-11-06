package com.lulan.shincolle.utility;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import net.minecraftforge.fluids.capability.templates.FluidTank;

/** Fluid container replacements are committed by the caller only after a successful transfer. */
public final class CraneFluidTransfer {
    public record Result(ItemStack container, int moved) { }

    private CraneFluidTransfer() { }

    public static Result move(ItemStack stack, FluidTank tank, boolean toTank, int maximum) {
        if (stack.isEmpty() || stack.getCount() != 1 || maximum <= 0) return new Result(stack, 0);
        IFluidHandlerItem handler = FluidUtil.getFluidHandler(stack.copy()).resolve().orElse(null);
        if (handler == null) return new Result(stack, 0);
        FluidStack moved = toTank ? FluidUtil.tryFluidTransfer(tank, handler, maximum, true)
                : FluidUtil.tryFluidTransfer(handler, tank, maximum, true);
        return moved.isEmpty() ? new Result(stack, 0) : new Result(handler.getContainer(), moved.getAmount());
    }

    public static boolean complete(ItemStack stack, FluidStack fluid, boolean full) {
        IFluidHandlerItem handler = FluidUtil.getFluidHandler(stack.copy()).resolve().orElse(null);
        if (handler == null) return true;
        for (int tank = 0; tank < handler.getTanks(); tank++) {
            FluidStack contents = handler.getFluidInTank(tank);
            if (full) {
                if (contents.isEmpty() || contents.getAmount() < handler.getTankCapacity(tank)
                        && (fluid.isEmpty() || contents.isFluidEqual(fluid))) return false;
            } else if (!contents.isEmpty() && !handler.drain(contents, FluidAction.SIMULATE).isEmpty()) {
                return false;
            }
        }
        return true;
    }
}
