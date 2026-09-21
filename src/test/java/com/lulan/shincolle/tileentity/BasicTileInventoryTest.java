package com.lulan.shincolle.tileentity;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.items.ItemStackHandler;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BasicTileInventoryTest {

    @BeforeAll
    static void bootstrapMinecraft() throws ReflectiveOperationException {
        SharedConstants.tryDetectVersion();
        Field bootstrapped = Bootstrap.class.getDeclaredField("isBootstrapped");
        bootstrapped.setAccessible(true);
        bootstrapped.setBoolean(null, true);
        BuiltInRegistries.bootStrap();
    }

    @Test
    void expandsSmallerSavedInventoryWithoutLosingContents() {
        ItemStackHandler savedInventory = inventoryWithContents(6);
        BasicTileInventory blockEntity = createInventory(7);

        blockEntity.load(tagWithInventory(savedInventory));

        ItemStackHandler loadedInventory = blockEntity.getInventory();
        assertEquals(7, loadedInventory.getSlots());
        assertStack(loadedInventory, 0, Items.IRON_INGOT, 12);
        assertStack(loadedInventory, 3, Items.COAL, 7);
        assertStack(loadedInventory, 5, Items.BUCKET, 1);
        assertTrue(loadedInventory.getStackInSlot(6).isEmpty());
    }

    @Test
    void keepsInventoryWhenSavedSizeMatchesConfiguredSize() {
        ItemStackHandler savedInventory = inventoryWithContents(7);
        BasicTileInventory blockEntity = createInventory(7);

        blockEntity.load(tagWithInventory(savedInventory));

        ItemStackHandler loadedInventory = blockEntity.getInventory();
        assertEquals(7, loadedInventory.getSlots());
        assertStack(loadedInventory, 0, Items.IRON_INGOT, 12);
        assertStack(loadedInventory, 3, Items.COAL, 7);
        assertStack(loadedInventory, 5, Items.BUCKET, 1);
        assertTrue(loadedInventory.getStackInSlot(6).isEmpty());
    }

    @Test
    void doesNotShrinkInventorySavedWithMoreSlots() {
        ItemStackHandler savedInventory = inventoryWithContents(8);
        savedInventory.setStackInSlot(7, new ItemStack(Items.DIAMOND, 2));
        BasicTileInventory blockEntity = createInventory(7);

        blockEntity.load(tagWithInventory(savedInventory));

        ItemStackHandler loadedInventory = blockEntity.getInventory();
        assertEquals(8, loadedInventory.getSlots());
        assertStack(loadedInventory, 7, Items.DIAMOND, 2);
    }

    private static BasicTileInventory createInventory(int slots) {
        return new BasicTileInventory(null, BlockPos.ZERO, Blocks.AIR.defaultBlockState(), slots);
    }

    private static ItemStackHandler inventoryWithContents(int slots) {
        ItemStackHandler inventory = new ItemStackHandler(slots);
        inventory.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 12));
        inventory.setStackInSlot(3, new ItemStack(Items.COAL, 7));
        inventory.setStackInSlot(5, new ItemStack(Items.BUCKET));
        return inventory;
    }

    private static CompoundTag tagWithInventory(ItemStackHandler inventory) {
        CompoundTag tag = new CompoundTag();
        tag.put("Inventory", inventory.serializeNBT());
        return tag;
    }

    private static void assertStack(ItemStackHandler inventory, int slot, Item item, int count) {
        ItemStack stack = inventory.getStackInSlot(slot);
        assertEquals(item, stack.getItem());
        assertEquals(count, stack.getCount());
    }
}
