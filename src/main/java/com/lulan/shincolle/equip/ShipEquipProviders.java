package com.lulan.shincolle.equip;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Registry of {@link ShipEquipProvider}s, resolving which one (if any)
 * handles a given stack in the ship-equip Curios slot.
 *
 * <p>Items implementing {@link IShipEquipment} directly are always checked
 * first via a built-in provider - registered providers (added by optional
 * integrations like Tinkers') only get a chance for items that don't.
 */
public final class ShipEquipProviders {

    private ShipEquipProviders() {
    }

    private static final ShipEquipProvider ITEM_SELF = new ShipEquipProvider() {
        @Override
        public boolean accepts(ItemStack stack) {
            return stack.getItem() instanceof IShipEquipment;
        }

        @Override
        public float[] computeShipAttrs(ItemStack stack) {
            return ((IShipEquipment) stack.getItem()).computeShipAttrs(stack);
        }

        @Override
        public void applyAttackEffects(Map<Integer, int[]> effectMap, ItemStack stack) {
            ((IShipEquipment) stack.getItem()).applyAttackEffects(effectMap, stack);
        }

        @Override
        public void onShipHit(LivingEntity ship, Entity target, float damageDealt, ItemStack stack) {
            ((IShipEquipment) stack.getItem()).onShipHit(ship, target, damageDealt, stack);
        }
    };

    /** Registered by optional-dependency integrations (e.g. Tinkers'), in load order. */
    private static final List<ShipEquipProvider> EXTRA = new ArrayList<>();

    /**
     * Called once, at mod construction, by an optional-dependency integration
     * that's actually loaded (see {@code ShinColle}'s constructor). Never call
     * this - or reference the provider passed to it - from code that isn't
     * itself behind the matching {@code ModList.isLoaded(...)} guard.
     */
    public static void register(ShipEquipProvider provider) {
        EXTRA.add(provider);
    }

    /** The provider that would handle this stack, or {@code null} if it isn't valid ship equipment. */
    public static ShipEquipProvider find(ItemStack stack) {
        if (ITEM_SELF.accepts(stack)) {
            return ITEM_SELF;
        }
        for (ShipEquipProvider provider : EXTRA) {
            if (provider.accepts(stack)) {
                return provider;
            }
        }
        return null;
    }

    public static boolean accepts(ItemStack stack) {
        return find(stack) != null;
    }
}
