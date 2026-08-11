package com.lulan.shincolle.equip;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.util.Map;

/**
 * Bridges a ship-equip Curios slot's contents to ShinColle's stat system for
 * items that don't (and, being another mod's item, sometimes can't)
 * implement {@link IShipEquipment} themselves.
 *
 * <p>An item can either implement {@link IShipEquipment} directly (see that
 * interface for the addon-author path - e.g. a purpose-built equipment item)
 * or be recognised by a registered provider instead - e.g. a plain Tinkers'
 * Construct tool a player forged themselves, read by {@code
 * com.lulan.shincolle.equip.tinkers.ShipTinkersIntegration}. Both paths feed
 * the same stat pipeline via {@link ShipEquipProviders}.
 */
public interface ShipEquipProvider {

    boolean accepts(ItemStack stack);

    float[] computeShipAttrs(ItemStack stack);

    default void applyAttackEffects(Map<Integer, int[]> effectMap, ItemStack stack) {
    }

    default void onShipHit(LivingEntity ship, Entity target, float damageDealt, ItemStack stack) {
    }
}
