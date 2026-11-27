package com.lulan.shincolle.ai.domain.task;

import com.lulan.shincolle.ai.domain.movement.MovementBody;
import com.lulan.shincolle.ai.domain.movement.MovementConstraint;
import com.lulan.shincolle.ai.domain.movement.MovementFacts;
import com.lulan.shincolle.ai.domain.movement.MovementInhibitReason;
import com.lulan.shincolle.ai.domain.movement.MovementIntent;
import com.lulan.shincolle.ai.domain.movement.MovementIntentResolver;
import com.lulan.shincolle.ai.domain.movement.MovementPermission;
import com.lulan.shincolle.ai.domain.movement.MovementPoint;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * When a working ship may walk for its work. Every walk is held back while the ship sits, is leashed,
 * works the crane or has no grudge. Riding inhibits every mining shuffle; only returning to a waypoint
 * or a fishing spot may use the ship's own mount, outside fleeing and engagement. A mining shuffle also waits while engaged,
 * and never leaves the region the ship's intent allows; the fixed-point walks are not held to that region.
 */
public final class TaskMovePermissions {

    private TaskMovePermissions() {
    }

    public static MovementPermission of(MovementFacts facts, MovementConstraint region, TaskMoveRequest request) {
        return of(facts, region, request, MovementBody.SELF);
    }

    /** VEHICLE means the boundary has identified the ship's own mount as its working body. */
    public static MovementPermission of(MovementFacts facts, MovementConstraint region, TaskMoveRequest request,
                                        MovementBody body) {
        Objects.requireNonNull(facts, "facts");
        Objects.requireNonNull(region, "region");
        Objects.requireNonNull(request, "request");
        Set<MovementInhibitReason> reasons = EnumSet.noneOf(MovementInhibitReason.class);
        if (facts.sittingPose()) reasons.add(MovementInhibitReason.SITTING);
        boolean mountedFixedPointWalk = body == MovementBody.VEHICLE
                && (request instanceof TaskMoveRequest.ReturnToWaypoint || request instanceof TaskMoveRequest.FishingSpot);
        if (facts.riding() && !mountedFixedPointWalk) reasons.add(MovementInhibitReason.RIDING);
        if (mountedFixedPointWalk) {
            if (MovementIntentResolver.resolve(facts).intent() instanceof MovementIntent.Flee) {
                reasons.add(MovementInhibitReason.FLEEING);
            }
            if (facts.engaged()) reasons.add(MovementInhibitReason.ENGAGED);
        }
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
