package com.lulan.shincolle.ai.domain.combat.skill;

import com.lulan.shincolle.ai.domain.movement.MovementConstraint;
import com.lulan.shincolle.ai.domain.movement.MovementPoint;

/** A skill never moves outside the region permitted by the current command. */
public final class SkillMovementPermission {
    private SkillMovementPermission() { }

    public static boolean allows(MovementConstraint constraint, MovementPoint destination) {
        if (!Double.isFinite(destination.x()) || !Double.isFinite(destination.y())
                || !Double.isFinite(destination.z())) return false;
        if (constraint instanceof MovementConstraint.NoCombatMovement) return false;
        return !(constraint instanceof MovementConstraint.Within within)
                || within.anchor().distanceSq(destination) <= within.radius() * within.radius();
    }
}
