package com.lulan.shincolle.ai.domain.combat;

/** A weapon a host fires on its own timer. */
public enum WeaponChannel {
    LIGHT,
    HEAVY,
    /** Aircraft, light and heavy alternating on one timer. */
    AIR,
    MELEE
}
