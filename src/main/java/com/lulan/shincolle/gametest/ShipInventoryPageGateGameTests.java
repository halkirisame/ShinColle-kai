package com.lulan.shincolle.gametest;

import com.lulan.shincolle.capability.CapaShipInventory;
import com.lulan.shincolle.client.gui.inventory.ContainerShipInventory;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Regression coverage for picked-up items being written past the inventory pages
 * the ship has actually unlocked.
 * <p>
 * The original gates insertion on {@code CapaShipInventory#isSlotAvailable} and
 * stops scanning at the first unavailable slot. The port dropped that gate, so
 * items landed in pages the player cannot open - and because
 * {@code BasicEntityShip#findItemInSlot} does honour the limit, they were also
 * invisible to the ship's own consumption.
 */
@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ShipInventoryPageGateGameTests {

    private ShipInventoryPageGateGameTests() {
    }

    private static BasicEntityShip spawn(GameTestHelper helper, int drumState) {
        Entity e = ModEntities.DESTROYER_SHIMAKAZE.get().create(helper.getLevel());
        if (!(e instanceof BasicEntityShip ship)) {
            throw new AssertionError("Failed to create ship");
        }
        ship.setStateMinor(ID.M.DrumState, drumState);
        return ship;
    }

    /** Fill every cargo slot the ship will accept, and report the highest one used. */
    private static int highestFilledCargoSlot(BasicEntityShip ship) {
        CapaShipInventory inv = ship.getCapaShipInventory();
        for (int i = 0; i < CapaShipInventory.SlotMax * 2; i++) {
            if (!inv.addItemStackToInventory(new ItemStack(Items.STONE, 64))) {
                break;
            }
        }
        int highest = -1;
        for (int i = ContainerShipInventory.EQUIP_SLOTS; i < CapaShipInventory.SlotMax; i++) {
            if (!inv.getStackInSlot(i).isEmpty()) {
                highest = i;
            }
        }
        return highest;
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void pickupStopsAtFirstPageWhenNoDrums(GameTestHelper helper) {
        int highest = highestFilledCargoSlot(spawn(helper, 0));
        int limit = ContainerShipInventory.EQUIP_SLOTS + 18;
        if (highest >= limit) {
            throw new AssertionError("Page 0 only: expected slots below " + limit
                    + " but items reached slot " + highest);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void pickupStopsAtSecondPageWithOneDrum(GameTestHelper helper) {
        int highest = highestFilledCargoSlot(spawn(helper, 1));
        int limit = ContainerShipInventory.EQUIP_SLOTS + 36;
        if (highest >= limit) {
            throw new AssertionError("Pages 0-1: expected slots below " + limit
                    + " but items reached slot " + highest);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void pickupUsesEveryPageWithTwoDrums(GameTestHelper helper) {
        int highest = highestFilledCargoSlot(spawn(helper, 2));
        if (highest != CapaShipInventory.SlotMax - 1) {
            throw new AssertionError("All pages unlocked: expected the last slot "
                    + (CapaShipInventory.SlotMax - 1) + " to be used, but the highest was " + highest);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void pageSizeSetterUpdatesTheValueTheGetterReads(GameTestHelper helper) {
        BasicEntityShip ship = spawn(helper, 0);
        ship.setInventoryPageSize(2);
        if (ship.getInventoryPageSize() != 2) {
            throw new AssertionError("setInventoryPageSize must write the field "
                    + "getInventoryPageSize reads; got " + ship.getInventoryPageSize());
        }
        helper.succeed();
    }
}
