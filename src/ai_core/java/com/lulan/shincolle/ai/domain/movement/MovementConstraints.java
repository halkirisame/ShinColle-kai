package com.lulan.shincolle.ai.domain.movement;

import java.util.Optional;

/** The region each movement intent lets combat move in. */
public final class MovementConstraints {
    private MovementConstraints() { }

    /**
     * Where the anchors are and how wide the follow circle is, as plain values.
     *
     * @param ownerPosition   the owner, when present in the same level
     * @param guardedPosition the guarded entity, when it resolves
     */
    public record Facts(boolean formation, Optional<MovementPoint> ownerPosition,
                        Optional<MovementPoint> guardedPosition, int followMax, float width, boolean pickItem) { }

    public static MovementConstraint of(MovementIntent intent, Facts facts) {
        if (intent instanceof MovementIntent.Sit) {
            return new MovementConstraint.NoCombatMovement(ConstraintSource.SIT);
        }
        if (intent instanceof MovementIntent.Flee) {
            return new MovementConstraint.NoCombatMovement(ConstraintSource.FLEE);
        }
        if (intent instanceof MovementIntent.MoveTo) {
            return new MovementConstraint.Unconstrained();
        }
        if (facts.formation()) {
            return new MovementConstraint.NoCombatMovement(ConstraintSource.FORMATION);
        }
        double radius = followRadius(facts.followMax(), facts.width(), facts.pickItem());
        if (intent instanceof MovementIntent.GuardPosition guard) {
            MovementPoint anchor = new MovementPoint(guard.position().x() + 0.5D, guard.position().y() + 0.5D,
                    guard.position().z() + 0.5D);
            return new MovementConstraint.Within(anchor, radius, ConstraintSource.GUARD_POSITION);
        }
        Optional<MovementPoint> anchor = intent instanceof MovementIntent.GuardEntity
                ? facts.guardedPosition() : facts.ownerPosition();
        ConstraintSource source = intent instanceof MovementIntent.GuardEntity
                ? ConstraintSource.GUARD_ENTITY : ConstraintSource.FOLLOW_OWNER;
        return anchor.<MovementConstraint>map(point -> new MovementConstraint.Within(point, radius, source))
                .orElseGet(MovementConstraint.Unconstrained::new);
    }

    /** The distance at which the follow and guard goals start pulling the ship back. */
    public static double followRadius(int followMax, float width, boolean pickItem) {
        float radius = followMax + width * 0.75F;
        if (pickItem) radius += 5F;
        return radius;
    }
}
