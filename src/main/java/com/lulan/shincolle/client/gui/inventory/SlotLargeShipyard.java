package com.lulan.shincolle.client.gui.inventory;

import com.lulan.shincolle.tileentity.TileMultiGrudgeHeavy;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.SlotItemHandler;

/**
 * Custom slot for Large Shipyard GUI.
 * Output slot (isOutputSlot=true) does not accept item placement.
 * Material slots accept any item.
 */
public class SlotLargeShipyard extends SlotItemHandler {

    private final boolean isOutputSlot;
    private final TileMultiGrudgeHeavy tile;

    public SlotLargeShipyard(IItemHandler itemHandler, int index, int x, int y, boolean isOutputSlot,
                             TileMultiGrudgeHeavy tile) {
        super(itemHandler, index, x, y);
        this.isOutputSlot = isOutputSlot;
        this.tile = tile;
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return !isOutputSlot && (tile == null || tile.isItemValidForSlot(getSlotIndex(), stack));
    }

    public boolean isOutputSlot() {
        return isOutputSlot;
    }
}
