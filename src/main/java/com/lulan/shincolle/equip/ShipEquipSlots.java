package com.lulan.shincolle.equip;

import com.lulan.shincolle.client.gui.inventory.ContainerShipInventory;
import com.lulan.shincolle.handler.ConfigHandler;

import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Layout of the third-party equipment strip appended to ShinColle's ship
 * inventory screen, next to its own {@value ContainerShipInventory#EQUIP_SLOTS}
 * built-in equip slots.
 *
 * <p>Deliberately holds no reference to Curios types: this class is safe to
 * load even when Curios is absent, since only geometry and the config value
 * live here. The actual slot contents are only ever touched from
 * {@code com.lulan.shincolle.equip.curios}, which is loaded solely behind a
 * {@code ModList.get().isLoaded("curios")} guard.
 *
 * <p>A single slot type holds every kind of third-party ship equipment,
 * mirroring how ShinColle's own six equip slots take anything - splitting it
 * per equipment family would need a slot type each and a screen far taller
 * than the GUI.
 */
public final class ShipEquipSlots {

    private ShipEquipSlots() {
    }

    /** The one Curios slot type ship equipment goes in. */
    public static final String SLOT_ID = "ship_equip";

    /** Hard ceiling, so the strip can never run off the bottom of the screen. */
    public static final int MAX_SLOTS = 10;

    /** X of the strip, just past ShinColle's 256-wide screen. */
    public static final int STRIP_X = 262;
    /** Y of the first slot. */
    public static final int STRIP_Y = 18;
    /** Width of the drawn panel. */
    public static final int PANEL_WIDTH = 26;

    public static int slotCount() {
        return Math.min(ConfigHandler.shipEquipSlotsCurios(), MAX_SLOTS);
    }

    public static int slotX() {
        return STRIP_X;
    }

    public static int slotY(int index) {
        return STRIP_Y + index * 18;
    }

    public static boolean accepts(ItemStack stack) {
        return ShipEquipProviders.accepts(stack);
    }

    /** True if the slot sits in our appended strip rather than ShinColle's own. */
    public static boolean isOurSlot(Slot slot) {
        return slot.x == STRIP_X
                && slot.y >= STRIP_Y
                && slot.y < STRIP_Y + slotCount() * 18
                && (slot.y - STRIP_Y) % 18 == 0;
    }
}
