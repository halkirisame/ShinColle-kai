package com.lulan.shincolle.ai.domain.waypoint;

import com.lulan.shincolle.ai.domain.command.CommandPos;

import java.util.Objects;

/** How long a ship has waited at the waypoint {@code at}. */
public record WaypointStay(CommandPos at, int elapsedTicks) {
    public WaypointStay {
        Objects.requireNonNull(at, "at");
        if (elapsedTicks < 0) throw new IllegalArgumentException("elapsedTicks");
    }
}
