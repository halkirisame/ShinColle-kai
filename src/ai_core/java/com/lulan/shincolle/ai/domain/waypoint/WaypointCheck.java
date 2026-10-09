package com.lulan.shincolle.ai.domain.waypoint;

import java.util.Objects;

/** The last traversal check of a ship and when it was made; for inspection, never saved. */
public record WaypointCheck(int tick, WaypointStep step) {
    public WaypointCheck {
        Objects.requireNonNull(step, "step");
    }
}
