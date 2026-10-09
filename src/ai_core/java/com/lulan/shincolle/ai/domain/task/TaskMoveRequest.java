package com.lulan.shincolle.ai.domain.task;

import com.lulan.shincolle.ai.domain.command.CommandPos;
import com.lulan.shincolle.ai.domain.movement.MovementPoint;

import java.util.Objects;

/** A walk a working ship asks for: where, and what for. */
public sealed interface TaskMoveRequest {

    /** Back to the guard point a cooking or crafting ship works at, when it has strayed from its chest. */
    record ReturnToWaypoint(CommandPos waypoint) implements TaskMoveRequest {
        public ReturnToWaypoint {
            Objects.requireNonNull(waypoint, "waypoint");
        }
    }

    /** To the spot a fishing ship casts from, when it is too far from it. */
    record FishingSpot(MovementPoint spot) implements TaskMoveRequest {
        public FishingSpot {
            Objects.requireNonNull(spot, "spot");
        }
    }

    /** A mining ship standing still takes a short walk to a point near it. */
    record MiningShuffle(MovementPoint near) implements TaskMoveRequest {
        public MiningShuffle {
            Objects.requireNonNull(near, "near");
        }
    }
}
