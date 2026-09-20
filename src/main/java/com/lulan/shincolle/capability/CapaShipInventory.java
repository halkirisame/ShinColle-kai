package com.lulan.shincolle.capability;

import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.utility.LogHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

/**
 * Ship inventory capability.
 * Manages ship inventory slots and NBT serialization.
 * Slots 0-5: equipment slots, 6+: general inventory (3 pages x 18 slots)
 */
public class CapaShipInventory {

    public static final int EquipSlots = 6;
    public static final int SlotMax = 60; // 6 equip + 18*3 inventory
    public static final String InvName = "ShipInventory";

    private final ItemStack[] stacks;
    private final Entity owner;
    private int inventoryPage;

    public CapaShipInventory(int size, Entity owner) {
        this.stacks = new ItemStack[size];
        this.owner = owner;
        this.inventoryPage = 0;
        for (int i = 0; i < size; i++) {
            this.stacks[i] = ItemStack.EMPTY;
        }
    }

    /**
     * Whether a cargo slot is usable given how many inventory pages this ship has
     * unlocked.
     * <p>
     * [PORT] 1.10.2 parity: the original gates insertion on this
     * (`CapaShipInventory#isSlotAvailable`), and its `getFirstSlotForItem` /
     * `getFirstSlotStackable` stop scanning at the first unavailable slot. The port
     * dropped the gate, so picked-up items were written into pages the player has
     * not unlocked and cannot open. Worse, {@code BasicEntityShip#findItemInSlot}
     * does honour the page limit, so anything stranded there was invisible to the
     * ship's own consumption as well.
     */
    public boolean isSlotAvailable(int slotid) {
        if (!(owner instanceof BasicEntityShip ship)) {
            // No host to ask (the client-side placeholder container). Keep every
            // slot usable rather than silently shrinking the inventory.
            return true;
        }
        int pages = ship.getInventoryPageSize();
        if (pages < 2 && slotid >= EquipSlots + 36) {
            return false;
        }
        return pages >= 1 || slotid < EquipSlots + 18;
    }

    public ItemStack getStackInSlot(int slot) {
        if (slot >= 0 && slot < stacks.length) {
            return stacks[slot];
        }
        return ItemStack.EMPTY;
    }

    public void setStackInSlot(int slot, ItemStack stack) {
        if (slot >= 0 && slot < stacks.length) {
            stacks[slot] = stack != null ? stack : ItemStack.EMPTY;
            // trigger equipment stat recalculation when equip slot changes
            if (slot < EquipSlots && owner instanceof BasicEntityShip ship) {
                ship.calcShipAttributes(2, true);
            }
        }
    }

    public int getSlots() {
        return stacks.length;
    }

    public CompoundTag serializeNBT() {
        CompoundTag nbt = new CompoundTag();
        ListTag list = new ListTag();
        for (int i = 0; i < stacks.length; i++) {
            if (!stacks[i].isEmpty()) {
                CompoundTag itemTag = new CompoundTag();
                itemTag.putInt("Slot", i);
                stacks[i].save(itemTag);
                list.add(itemTag);
            }
        }
        nbt.put("Items", list);
        return nbt;
    }

    public void deserializeNBT(CompoundTag nbt) {
        // Clear all slots before loading to avoid stale data
        for (int i = 0; i < stacks.length; i++) {
            stacks[i] = ItemStack.EMPTY;
        }
        ListTag list = nbt.getList("Items", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag itemTag = list.getCompound(i);
            int slot = itemTag.getInt("Slot");
            if (slot >= 0 && slot < stacks.length) {
                stacks[slot] = ItemStack.of(itemTag);
            }
        }
    }

    /**
     * Find first empty slot for item storage. Returns slot index or -1 if full.
     */
    public int getFirstSlotForItem() {
        for (int i = Math.min(EquipSlots, stacks.length); i < stacks.length; i++) {
            if (stacks[i].isEmpty()) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Try to add an item stack to the inventory. Returns true if fully added.
     */
    public boolean addItemStackToInventory(ItemStack stack) {
        if (stack.isEmpty())
            return false;

        if (!canAddItemStackToInventory(stack)) {
            return false;
        }

        int cargoStart = Math.min(EquipSlots, stacks.length);
        for (int i = cargoStart; i < stacks.length && !stack.isEmpty(); i++) {
            // Original stops the scan here rather than skipping: the locked pages
            // are always the tail of the array.
            if (!isSlotAvailable(i)) {
                break;
            }
            ItemStack itemStack = stacks[i];
            if (!itemStack.isEmpty() && ItemStack.isSameItemSameTags(itemStack, stack)) {
                int toAdd = Math.min(itemStack.getMaxStackSize() - itemStack.getCount(), stack.getCount());
                if (toAdd > 0) {
                    itemStack.grow(toAdd);
                    stack.shrink(toAdd);
                }
            }
        }

        for (int i = cargoStart; i < stacks.length && !stack.isEmpty(); i++) {
            if (!isSlotAvailable(i)) {
                break;
            }
            if (stacks[i].isEmpty()) {
                int toAdd = Math.min(stack.getMaxStackSize(), stack.getCount());
                ItemStack inserted = stack.copy();
                inserted.setCount(toAdd);
                setStackInSlot(i, inserted);
                stack.shrink(toAdd);
            }
        }

        return stack.isEmpty();
    }

    /**
     * Return whether the complete stack fits in cargo slots without changing inventory state.
     */
    public boolean canAddItemStackToInventory(ItemStack stack) {
        if (stack.isEmpty())
            return false;

        int cargoStart = Math.min(EquipSlots, stacks.length);
        int capacity = 0;
        for (int i = cargoStart; i < stacks.length; i++) {
            ItemStack itemStack = stacks[i];
            if (!itemStack.isEmpty() && ItemStack.isSameItemSameTags(itemStack, stack)) {
                capacity += Math.max(0, itemStack.getMaxStackSize() - itemStack.getCount());
            } else if (itemStack.isEmpty()) {
                capacity += stack.getMaxStackSize();
            }
            if (capacity >= stack.getCount()) {
                break;
            }
        }

        if (capacity < stack.getCount()) {
            LogHelper.diag("DIAG: cargo insert failed capacity=" + capacity
                    + " requested=" + stack.getCount() + " item=" + stack.getItem());
            return false;
        }
        return true;
    }

    public int getInventoryPage() {
        return this.inventoryPage;
    }

    public void setInventoryPage(int page) {
        this.inventoryPage = page;
    }
}
