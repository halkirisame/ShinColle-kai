package com.lulan.shincolle.ai.domain.task;

import com.lulan.shincolle.ai.domain.movement.MovementConstraint;
import com.lulan.shincolle.ai.domain.movement.MovementFacts;
import com.lulan.shincolle.ai.domain.movement.MovementInhibitReason;
import com.lulan.shincolle.ai.domain.movement.MovementPermission;
import com.lulan.shincolle.ai.domain.movement.MovementPoint;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * When a working ship may walk for its work. Every walk is held back while the ship sits, is leashed,
 * rides, works the crane or has no grudge. A mining shuffle also waits while the ship is engaged, and
 * never leaves the region the ship's intent allows; the walk back to the waypoint, and to the fishing
 * spot, are made to a fixed point and are not held to that region.
 */
public final class TaskMovePermissions {

    private TaskMovePermissions() {
    }

    public static MovementPermission of(MovementFacts facts, MovementConstraint region, TaskMoveRequest request) {
        Objects.requireNonNull(facts, "facts");
        Objects.requireNonNull(region, "region");
        Objects.requireNonNull(request, "request");
        Set<MovementInhibitReason> reasons = EnumSet.noneOf(MovementInhibitReason.class);
        if (facts.sittingPose()) reasons.add(MovementInhibitReason.SITTING);
        if (facts.riding()) reasons.add(MovementInhibitReason.RIDING);
        if (facts.crane()) reasons.add(MovementInhibitReason.CRANE);
        if (!facts.hasGrudge()) reasons.add(MovementInhibitReason.NO_GRUDGE);
        if (facts.leashed()) reasons.add(MovementInhibitReason.LEASHED);
        if (request instanceof TaskMoveRequest.MiningShuffle shuffle) {
            if (facts.engaged()) reasons.add(MovementInhibitReason.ENGAGED);
            if (!inside(region, shuffle.near())) reasons.add(MovementInhibitReason.OUTSIDE_REGION);
        }
        return MovementPermission.inhibited(reasons);
    }

    private static boolean inside(MovementConstraint region, MovementPoint point) {
        if (region instanceof MovementConstraint.Within within) {
            return within.anchor().distanceSq(point) <= within.radius() * within.radius();
        }
        return !(region instanceof MovementConstraint.NoCombatMovement);
    }
}
