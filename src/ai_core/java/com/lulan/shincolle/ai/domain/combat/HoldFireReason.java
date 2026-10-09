package com.lulan.shincolle.ai.domain.combat;

/**
 * Why a host may not fire with any weapon this tick. A condition that stops only some weapons,
 * such as the crane for cannons and aircraft or riding for melee, belongs to that weapon instead.
 */
public enum HoldFireReason {
    /** The target authority holds no lock, or the locked entity is gone. */
    NO_TARGET,
    /** The action constraints forbid firing (out of fuel, dead). */
    FIRING_BLOCKED,
    SITTING,
    /** Riding a ship mount, which fires for its rider. */
    ON_SHIP_MOUNT
}
