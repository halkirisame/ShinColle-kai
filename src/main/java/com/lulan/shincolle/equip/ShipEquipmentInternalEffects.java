package com.lulan.shincolle.equip;

import com.lulan.shincolle.ShinColle;
import com.lulan.shincolle.api.equipment.ResolvedShipEquipment;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.entity.IShipAttackBase;
import com.lulan.shincolle.entity.LegacyBasicEquipEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * Internal bridge for behavior that intentionally remains outside the public
 * ResourceLocation-based equipment API during Stage 4.
 */
public final class ShipEquipmentInternalEffects {

    private ShipEquipmentInternalEffects() {
    }

    /** Applies legacy state/effect data only after canonical resolution succeeded. */
    public static void apply(LivingEntity entity, IShipAttackBase ship, ItemStack stack,
                             ResolvedShipEquipment resolved) {
        try {
            ship.getAttackEffectMap().putAll(resolved.attackEffects());
            if (entity instanceof BasicEntityShip friendly) {
                LegacyBasicEquipEffects.apply(friendly, stack);
            }
        } catch (RuntimeException error) {
            ShinColle.LOGGER.warn("Ship equipment internal effects failed for {}; continuing recalculation",
                    stack.getItem(), error);
        }
    }
}
