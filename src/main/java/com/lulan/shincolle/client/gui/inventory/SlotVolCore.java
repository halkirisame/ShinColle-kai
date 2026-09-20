package com.lulan.shincolle.client.gui.inventory;

import com.lulan.shincolle.init.ModItems;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.SlotItemHandler;

/**
 * Custom slot for Volcano Core GUI.
 * Validates items for fuel input.
 */
public class SlotVolCore extends SlotItemHandler {

    public SlotVolCore(IItemHandler itemHandler, int index, int x, int y) {
        super(itemHandler, index, x, y);
    }

    /**
     * Check if the item is a valid fuel for the Volcano Core.
     * Accepted fuels: Grudge and Grudge Block.
     */
    public static boolean isValidFuel(ItemStack stack) {
        if (stack.isEmpty())
            return false;

        return stack.is(ModItems.GRUDGE.get()) || stack.is(ModItems.GRUDGE_BLOCK_ITEM.get());
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return isValidFuel(stack);
    }
}
