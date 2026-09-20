package com.lulan.shincolle.api.equipment;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * Adapter for third-party Items that cannot implement {@link IShipEquipment} directly.
 */
public interface ShipEquipmentProvider {

    /** Side-safe predicate which must not mutate the supplied stack or game state. */
    boolean matches(ItemStack stack);

    /** Resolves side-safe dynamic values for one stack accepted by {@link #matches(ItemStack)}. */
    ResolvedShipEquipment resolveShipEquipment(ShipEquipmentContext context);

    /** Called after an owning ship hits a target with the existing attack amount. */
    default void onShipHit(LivingEntity ship, Entity target, float attackAmount, ItemStack stack) {
    }
}
