package com.lulan.shincolle.utility;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.energy.IEnergyStorage;

/** Standard FE transfers only; no optional energy-system classes are loaded here. */
public final class CraneEnergyTransfer {
    public record Result(ItemStack container, int moved) { }

    private CraneEnergyTransfer() { }

    public static Result move(ItemStack stack, IEnergyStorage buffer, boolean toBuffer, int maximum) {
        if (stack.isEmpty() || stack.getCount() != 1 || maximum <= 0) return new Result(stack, 0);
        ItemStack copy = stack.copy();
        IEnergyStorage item = copy.getCapability(ForgeCapabilities.ENERGY).resolve().orElse(null);
        if (item == null) return new Result(stack, 0);
        IEnergyStorage source = toBuffer ? item : buffer;
        IEnergyStorage target = toBuffer ? buffer : item;
        int amount = target.receiveEnergy(source.extractEnergy(maximum, true), true);
        if (amount <= 0) return new Result(stack, 0);
        int extracted = source.extractEnergy(amount, false);
        int moved = target.receiveEnergy(extracted, false);
        if (moved < extracted) source.receiveEnergy(extracted - moved, false);
        return new Result(copy, moved);
    }

    public static boolean complete(ItemStack stack, boolean full) {
        IEnergyStorage item = stack.copy().getCapability(ForgeCapabilities.ENERGY).resolve().orElse(null);
        return item == null || (full ? item.receiveEnergy(Integer.MAX_VALUE, true) == 0
                : item.extractEnergy(Integer.MAX_VALUE, true) == 0);
    }
}
