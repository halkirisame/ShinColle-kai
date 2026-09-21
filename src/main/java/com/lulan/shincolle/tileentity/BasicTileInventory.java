package com.lulan.shincolle.tileentity;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.items.ItemStackHandler;

/**
 * Block entity with inventory capability.
 */
public class BasicTileInventory extends BasicTileEntity {

    protected final ItemStackHandler inventory;
    protected final int slotCount;

    public BasicTileInventory(BlockEntityType<?> type, BlockPos pos, BlockState state, int slots) {
        super(type, pos, state);
        this.slotCount = slots;
        this.inventory = new ItemStackHandler(slots) {
            @Override
            protected void onContentsChanged(int slot) {
                setChanged();
            }
        };
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("Inventory", inventory.serializeNBT());
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("Inventory")) {
            inventory.deserializeNBT(tag.getCompound("Inventory"));
            if (inventory.getSlots() < slotCount) {
                CompoundTag resizedInventory = inventory.serializeNBT();
                resizedInventory.putInt("Size", slotCount);
                inventory.deserializeNBT(resizedInventory);
            }
        }
    }

    public ItemStackHandler getInventory() {
        return inventory;
    }
}
