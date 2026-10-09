package com.lulan.shincolle.ai.domain.waypoint;

import com.lulan.shincolle.ai.domain.command.CommandPos;

import java.util.Objects;
import java.util.Optional;

/** The links a waypoint holds to the waypoints before and after it; an unset link is empty, never a zero position. */
public record WaypointLinks(Optional<CommandPos> next, Optional<CommandPos> last) {
    public static final WaypointLinks NONE = new WaypointLinks(Optional.empty(), Optional.empty());

    public WaypointLinks {
        Objects.requireNonNull(next, "next");
        Objects.requireNonNull(last, "last");
    }
}
