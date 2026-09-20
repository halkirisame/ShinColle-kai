package com.lulan.shincolle.equip;

import com.lulan.shincolle.api.equipment.ShipAttackEffect;
import com.lulan.shincolle.entity.IShipAttackBase;
import com.lulan.shincolle.utility.BuffHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.Map;

/** Applies the canonical attack-effect map and then dispatches addon equipment hooks. */
public final class ShipOnHitEffects {

    private ShipOnHitEffects() {
    }

    public static void dispatch(LivingEntity sourceShip, Entity target, float attackAmount) {
        Map<ResourceLocation, ShipAttackEffect> effects = sourceShip instanceof IShipAttackBase ship
                ? ship.getAttackEffectMap() : Map.of();
        dispatch(sourceShip, target, attackAmount, effects);
    }

    /**
     * Dispatches using an explicit effect snapshot, for projectiles whose effects are fixed at launch.
     */
    public static void dispatch(LivingEntity sourceShip, Entity target, float attackAmount,
                                Map<ResourceLocation, ShipAttackEffect> attackEffects) {
        if (sourceShip == null || target == null || sourceShip.level().isClientSide) {
            return;
        }
        if (attackEffects != null && !attackEffects.isEmpty()) {
            BuffHelper.applyBuffOnTarget(target, attackEffects);
        }
        ShipEquipmentOnHitDispatcher.dispatch(sourceShip, target, attackAmount);
    }
}
