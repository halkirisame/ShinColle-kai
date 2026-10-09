package com.lulan.shincolle.ai.domain.task;

import com.lulan.shincolle.ai.domain.movement.MovementBody;
import com.lulan.shincolle.ai.domain.movement.MovementPermission;
import com.lulan.shincolle.ai.domain.movement.MovementPlan;
import com.lulan.shincolle.ai.domain.movement.MovementPoint;
import com.lulan.shincolle.ai.domain.movement.MovementReason;
import com.lulan.shincolle.ai.domain.movement.MovementStep;
import com.lulan.shincolle.ai.domain.movement.MovementTarget;

import java.util.Objects;

/**
 * How a working ship's walks are made: one path of ordinary speed from the ship itself, to the point
 * the request names. A request that is not permitted makes no plan.
 */
public final class TaskMovePlanner {

    static final double SPEED = 1D;

    private TaskMovePlanner() {
    }

    public static MovementPlan plan(TaskMoveRequest request, MovementPermission permission) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(permission, "permission");
        if (!permission.allowed()) {
            return MovementPlan.NONE;
        }
        if (request instanceof TaskMoveRequest.ReturnToWaypoint back) {
            return path(new MovementPoint(back.waypoint().x(), back.waypoint().y(), back.waypoint().z()),
                    MovementReason.TASK_RETURN);
        }
        if (request instanceof TaskMoveRequest.FishingSpot fishing) {
            return path(fishing.spot(), MovementReason.TASK_FISHING_SPOT);
        }
        return path(((TaskMoveRequest.MiningShuffle) request).near(), MovementReason.TASK_MINING_SHUFFLE);
    }

    private static MovementPlan path(MovementPoint point, MovementReason reason) {
        return MovementPlan.of(new MovementStep.PathTo(MovementBody.SELF, new MovementTarget.Point(point), SPEED,
                reason));
    }
}
