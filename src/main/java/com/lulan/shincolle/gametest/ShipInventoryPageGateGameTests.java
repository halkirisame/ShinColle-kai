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

    private static BasicEntityShip spawn(GameTestHelper helper, GameTestEntities entities, int drumState) {
        Entity e = entities.add(ModEntities.DESTROYER_SHIMAKAZE.get().create(helper.getLevel()));
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
        try (GameTestEntities entities = GameTestEntities.open(helper)) {
            int highest = highestFilledCargoSlot(spawn(helper, entities, 0));
            int limit = ContainerShipInventory.EQUIP_SLOTS + 18;
            if (highest >= limit) {
                throw new AssertionError("Page 0 only: expected slots below " + limit
                        + " but items reached slot " + highest);
            }
            helper.succeed();
        }
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void pickupStopsAtSecondPageWithOneDrum(GameTestHelper helper) {
        try (GameTestEntities entities = GameTestEntities.open(helper)) {
            int highest = highestFilledCargoSlot(spawn(helper, entities, 1));
            int limit = ContainerShipInventory.EQUIP_SLOTS + 36;
            if (highest >= limit) {
                throw new AssertionError("Pages 0-1: expected slots below " + limit
                        + " but items reached slot " + highest);
            }
            helper.succeed();
        }
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void pickupUsesEveryPageWithTwoDrums(GameTestHelper helper) {
        try (GameTestEntities entities = GameTestEntities.open(helper)) {
            int highest = highestFilledCargoSlot(spawn(helper, entities, 2));
            if (highest != CapaShipInventory.SlotMax - 1) {
                throw new AssertionError("All pages unlocked: expected the last slot "
                        + (CapaShipInventory.SlotMax - 1) + " to be used, but the highest was " + highest);
            }
            helper.succeed();
        }
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void pageSizeSetterUpdatesTheValueTheGetterReads(GameTestHelper helper) {
        try (GameTestEntities entities = GameTestEntities.open(helper)) {
            BasicEntityShip ship = spawn(helper, entities, 0);
            ship.setInventoryPageSize(2);
            if (ship.getInventoryPageSize() != 2) {
                throw new AssertionError("setInventoryPageSize must write the field "
                        + "getInventoryPageSize reads; got " + ship.getInventoryPageSize());
            }
            helper.succeed();
        }
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void cargoRejectionPreservesStacksAtEveryPageBoundary(GameTestHelper helper) {
        try (GameTestEntities entities = GameTestEntities.open(helper)) {
            for (int drums = 0; drums <= 2; drums++) {
                BasicEntityShip ship = spawn(helper, entities, drums);
                helper.assertTrue(ship.getInventoryPageSize() == drums, "Fixture must set the drum state");
                CapaShipInventory inventory = ship.getCapaShipInventory();
                int limit = CapaShipInventory.EquipSlots + 18 * (drums + 1);
                for (int slot = CapaShipInventory.EquipSlots; slot < limit; slot++) {
                    inventory.setStackInSlot(slot, new ItemStack(Items.STONE, 64));
                }
                inventory.setStackInSlot(limit - 1, new ItemStack(Items.IRON_INGOT, 63));
                ItemStack rejected = new ItemStack(Items.IRON_INGOT, 2);
                var before = inventory.serializeNBT();
                boolean predicted = inventory.canAddItemStackToInventory(rejected);
                helper.assertTrue(before.equals(inventory.serializeNBT()) && rejected.getCount() == 2,
                        "Capacity simulation must not change either inventory");
                helper.assertTrue(!inventory.addItemStackToInventory(rejected),
                        "Two items must not fit in one remaining unlocked space");
                helper.assertTrue(rejected.getCount() == 2 && before.equals(inventory.serializeNBT()),
                        "Rejected insertion must leave source and every cargo slot unchanged: drums=" + drums);
                helper.assertTrue(!predicted, "Locked cargo must not contribute capacity: drums=" + drums);

                ItemStack accepted = new ItemStack(Items.IRON_INGOT, 1);
                helper.assertTrue(inventory.canAddItemStackToInventory(accepted),
                        "The remaining unlocked stack space must still be available");
                helper.assertTrue(inventory.addItemStackToInventory(accepted) && accepted.isEmpty(),
                        "An item fitting the unlocked stack must be consumed");
                helper.assertTrue(inventory.getStackInSlot(limit - 1).getCount() == 64,
                        "The last unlocked stack must be filled");
                for (int slot = limit; slot < inventory.getSlots(); slot++) {
                    helper.assertTrue(inventory.getStackInSlot(slot).isEmpty(), "Locked cargo must stay empty");
                }
            }
            helper.succeed();
        }
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void cargoEmptySlotSearchRespectsPageUnlocks(GameTestHelper helper) {
        try (GameTestEntities entities = GameTestEntities.open(helper)) {
            for (int drums = 0; drums < 2; drums++) {
                BasicEntityShip ship = spawn(helper, entities, drums);
                helper.assertTrue(ship.getInventoryPageSize() == drums, "Fixture must set the drum state");
                CapaShipInventory inventory = ship.getCapaShipInventory();
                int limit = CapaShipInventory.EquipSlots + 18 * (drums + 1);
                for (int slot = CapaShipInventory.EquipSlots; slot < limit; slot++) {
                    inventory.setStackInSlot(slot, new ItemStack(Items.STONE, 64));
                }
                helper.assertTrue(!inventory.isSlotAvailable(limit), "Fixture must have a locked next page");
                helper.assertTrue(inventory.getFirstSlotForItem() == -1,
                        "A locked empty page must not be exposed as pickup capacity: drums=" + drums);
                helper.assertTrue(!inventory.canAddItemStackToInventory(new ItemStack(Items.STONE, 1)),
                        "Full unlocked cargo must reject another item");
                ship.setInventoryPageSize(drums + 1);
                helper.assertTrue(ship.getInventoryPageSize() == drums + 1,
                        "Fixture must unlock the next page");
                helper.assertTrue(inventory.getFirstSlotForItem() == limit,
                        "Unlocking a page must expose its first cargo slot");
                ItemStack accepted = new ItemStack(Items.STONE, 1);
                helper.assertTrue(inventory.canAddItemStackToInventory(accepted)
                                && inventory.addItemStackToInventory(accepted) && accepted.isEmpty(),
                        "Newly unlocked cargo must accept the item");
                helper.assertTrue(inventory.getStackInSlot(limit).getCount() == 1,
                        "The item must be stored in the newly unlocked page");
            }
            helper.succeed();
        }
    }
}
