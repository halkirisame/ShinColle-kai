package com.lulan.shincolle.api.equipment;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * Highest-priority canonical extension point for an Item that is ship equipment itself.
 */
public interface IShipEquipment {

    /**
     * Resolves side-safe dynamic values for this Item. The result must use
     * {@link ShipEquipmentContext#layout()} and must not mutate game state.
     */
    ResolvedShipEquipment resolveShipEquipment(ShipEquipmentContext context);

    /** Called after an owning ship hits a target with the existing attack amount. */
    default void onShipHit(LivingEntity ship, Entity target, float attackAmount, ItemStack stack) {
    }
}
