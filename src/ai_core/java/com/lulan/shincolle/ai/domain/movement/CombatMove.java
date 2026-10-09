package com.lulan.shincolle.ai.domain.movement;

/** What an attack goal does with the ship's feet this tick. */
public sealed interface CombatMove {
    Reason reason();

    record Hold(Reason reason) implements CombatMove { }

    record Approach(MovementPoint destination, Reason reason) implements CombatMove { }

    /**
     * The attack goal's wish to close in or stay put, given as the ship and target positions
     * and whether the goal would already hold (in range, or in melee reach).
     */
    record Request(MovementPoint self, MovementPoint target, boolean holdForFire) { }

    enum Reason {
        IN_POSITION,
        TOWARD_TARGET,
        TO_REGION_EDGE,
        AT_REGION_EDGE,
        NO_COMBAT_MOVEMENT
    }
}
