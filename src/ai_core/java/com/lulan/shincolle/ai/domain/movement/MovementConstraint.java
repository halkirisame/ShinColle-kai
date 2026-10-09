package com.lulan.shincolle.ai.domain.movement;

/** Where combat may move the ship, as the current intent allows. */
public sealed interface MovementConstraint {
    record Unconstrained() implements MovementConstraint { }

    /** Combat movement stays within {@code radius} of {@code anchor}. */
    record Within(MovementPoint anchor, double radius, ConstraintSource source) implements MovementConstraint {
        public Within {
            if (!Double.isFinite(radius) || radius < 0D) {
                throw new IllegalArgumentException("Region radius must be finite and non-negative");
            }
        }
    }

    /** The intent owns the ship's movement; combat holds still. */
    record NoCombatMovement(ConstraintSource source) implements MovementConstraint { }
}
