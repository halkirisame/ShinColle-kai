package com.lulan.shincolle.ai.domain.waypoint;

import com.lulan.shincolle.ai.domain.command.CommandPos;

import java.util.Objects;
import java.util.Optional;

/** Where a ship has been on its route and how long it has waited where it is. */
public record WaypointProgress(Optional<CommandPos> lastWaypoint, Optional<WaypointStay> stay) {
    public static final WaypointProgress NONE = new WaypointProgress(Optional.empty(), Optional.empty());

    public WaypointProgress {
        Objects.requireNonNull(lastWaypoint, "lastWaypoint");
        Objects.requireNonNull(stay, "stay");
    }
}
