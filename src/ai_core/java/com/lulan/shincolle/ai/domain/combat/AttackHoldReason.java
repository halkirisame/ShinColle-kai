package com.lulan.shincolle.ai.domain.combat;

/** Why one weapon does not fire this tick while the host engages. */
public enum AttackHoldReason {
    NOT_IN_LOADOUT,
    /** The weapon's type or use flag is off, or the melee flag is off. */
    NOT_ENABLED,
    NO_AMMO,
    /** Loading with the crane; cannons and aircraft only. */
    CRANE,
    NOT_READY,
    NOT_AIMED,
    OUT_OF_RANGE,
    NO_LINE_OF_SIGHT,
    /** Riding anything; melee only. */
    PASSENGER
}
