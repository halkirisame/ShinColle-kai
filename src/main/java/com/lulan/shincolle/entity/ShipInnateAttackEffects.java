package com.lulan.shincolle.entity;

import com.lulan.shincolle.api.equipment.ShipAttackEffect;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import java.util.HashMap;

/** Internal helpers for the built-in ships' intrinsic on-hit effects. */
public final class ShipInnateAttackEffects {

    public static final ResourceLocation SLOWNESS = vanilla("slowness");
    public static final ResourceLocation MINING_FATIGUE = vanilla("mining_fatigue");
    public static final ResourceLocation BLINDNESS = vanilla("blindness");
    public static final ResourceLocation HUNGER = vanilla("hunger");
    public static final ResourceLocation WEAKNESS = vanilla("weakness");
    public static final ResourceLocation POISON = vanilla("poison");
    public static final ResourceLocation UNLUCK = vanilla("unluck");

    private ShipInnateAttackEffects() {
    }

    public static void put(IShipAttackBase ship, ResourceLocation effectId,
                           int amplifier, int durationTicks, int chancePercent) {
        HashMap<ResourceLocation, ShipAttackEffect> effects = ship.getAttackEffectMap();
        if (effects == null) {
            effects = new HashMap<>();
            ship.setAttackEffectMap(effects);
        }
        ShipAttackEffect effect = new ShipAttackEffect(effectId, amplifier, durationTicks,
                Mth.clamp(chancePercent, 0, 100));
        effects.put(effectId, effect);
    }

    private static ResourceLocation vanilla(String path) {
        return ResourceLocation.fromNamespaceAndPath("minecraft", path);
    }
}
