package com.lulan.shincolle.ai.domain.combat;

/** How an attack goal moves for its weapons. Keeping range, kiting and regaining sight are handled separately. */
public enum CombatManeuver {
    NONE,
    APPROACH_TARGET,
    STOP_FOR_FIRE
}
