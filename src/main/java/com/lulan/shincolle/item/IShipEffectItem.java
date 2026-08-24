package com.lulan.shincolle.item;

import com.lulan.shincolle.api.equipment.ShipAttackEffect;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;

/**
 * Interface for items that apply effects on attack.
 */
public interface IShipEffectItem {
    Map<ResourceLocation, ShipAttackEffect> getEffectOnAttack(int meta);

    int getMissileType(int meta);

    int getMissileMoveType(int meta);

    int getMissileSpeedLevel(int meta);
}
