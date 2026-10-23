package com.lulan.shincolle.api.experimental.equipment;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * Experimental: may change or be removed without an API version change.
 *
 * <p>Optional callback for an Item or a provider that already takes part in ship equipment resolution
 * ({@code IShipEquipment} or {@code ShipEquipmentProvider}). Implement it in addition to that interface to be
 * told when an owning ship hits a target. When and how often a hit is reported depends on the ship attack
 * implementation, whose public API is not designed yet, so this contract is expected to change together with it.</p>
 */
public interface ShipEquipmentHitCallback {

    /** Called after an owning ship hits a target with the existing attack amount. */
    void onShipHit(LivingEntity ship, Entity target, float attackAmount, ItemStack stack);
}
