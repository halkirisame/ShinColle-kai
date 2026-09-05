package com.lulan.shincolle.item;

/** Interface for equipment items with legacy missile behavior. */
public interface IShipEffectItem {
    int getMissileType(int meta);

    int getMissileMoveType(int meta);

    int getMissileSpeedLevel(int meta);
}
