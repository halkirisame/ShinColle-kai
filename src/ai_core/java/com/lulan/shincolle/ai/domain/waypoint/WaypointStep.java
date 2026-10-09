package com.lulan.shincolle.ai.domain.waypoint;

import com.lulan.shincolle.ai.domain.command.CommandPos;

import java.util.Objects;
import java.util.Set;

/** What one traversal check decided. */
public sealed interface WaypointStep {
    /** Nothing happens and the progress is left alone. */
    record Skip(Set<WaypointSkipReason> reasons) implements WaypointStep {
        public Skip {
            reasons = Set.copyOf(reasons);
        }
    }

    /** Arrived but still waiting. */
    record Wait(WaypointProgress next) implements WaypointStep {
        public Wait {
            Objects.requireNonNull(next, "next");
        }
    }

    /** The wait is over and the waypoint leads nowhere. */
    record Hold(WaypointProgress next) implements WaypointStep {
        public Hold {
            Objects.requireNonNull(next, "next");
        }
    }

    /** The wait is over; the ship is sent on to {@code destination}. */
    record Advance(CommandPos destination, WaypointProgress next) implements WaypointStep {
        public Advance {
            Objects.requireNonNull(destination, "destination");
            Objects.requireNonNull(next, "next");
        }
    }
}
