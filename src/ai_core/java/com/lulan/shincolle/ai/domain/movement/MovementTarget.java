package com.lulan.shincolle.ai.domain.movement;

import com.lulan.shincolle.ai.domain.TargetHandle;

import java.util.Objects;

/**
 * Where a path leads. A path toward an entity and a path toward a point are made differently, so
 * the two are kept apart.
 */
public sealed interface MovementTarget {
    record Point(MovementPoint point) implements MovementTarget {
        public Point {
            Objects.requireNonNull(point, "point");
        }
    }

    record Entity(TargetHandle handle) implements MovementTarget {
        public Entity {
            Objects.requireNonNull(handle, "handle");
        }
    }

    /**
     * A reachable point beside {@code center}, for a ship stuck on its way there; the executor picks
     * it in a fixed order and falls back to {@code center} itself when none is reachable.
     */
    record Around(MovementPoint center) implements MovementTarget {
        public Around {
            Objects.requireNonNull(center, "center");
        }
    }
}
