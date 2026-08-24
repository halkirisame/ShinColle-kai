package com.lulan.shincolle.equip;

import com.lulan.shincolle.api.equipment.ShipEquipmentResolver;
import com.lulan.shincolle.capability.CapaShipInventory;
import com.lulan.shincolle.client.gui.inventory.ContainerShipInventory;
import com.lulan.shincolle.entity.BasicEntityShip;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/** Dispatches canonical equipment on-hit hooks across every equipped storage path. */
public final class ShipEquipmentOnHitDispatcher {

    private ShipEquipmentOnHitDispatcher() {
    }

    public static void dispatch(LivingEntity ship, Entity target, float attackAmount) {
        if (ship == null || target == null || ship.level().isClientSide) {
            return;
        }

        if (ship instanceof BasicEntityShip friendly) {
            CapaShipInventory inventory = friendly.getCapaShipInventory();
            if (inventory != null) {
                for (int slot = 0; slot < ContainerShipInventory.EQUIP_SLOTS; slot++) {
                    dispatchStack(ship, target, attackAmount, inventory.getStackInSlot(slot));
                }
            }
        }

        for (ItemStack stack : ShipEquipmentOptionalIntegrations.getEquippedStacks(ship)) {
            dispatchStack(ship, target, attackAmount, stack);
        }
    }

    private static void dispatchStack(LivingEntity ship, Entity target, float attackAmount, ItemStack stack) {
        if (!stack.isEmpty()) {
            ShipEquipmentResolver.dispatchServerOnShipHit(ship, target, attackAmount, stack);
        }
    }
}
