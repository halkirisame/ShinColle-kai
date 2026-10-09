package com.lulan.shincolle.ai.domain.waypoint;

import com.lulan.shincolle.ai.domain.command.CommandPos;

import java.util.Objects;

/**
 * What one traversal check reads.
 *
 * @param riding the ship is a passenger of anything
 * @param ridingMount the ship rides its own mount (carried for the rule that lets such a ship traverse)
 * @param formationMember the ship stands in a formation slot other than the flagship's
 * @param current the block the ship guards
 * @param currentIsWaypoint a waypoint stands at {@code current}
 * @param links the links of that waypoint; {@link WaypointLinks#NONE} when there is none
 * @param waypointStayTicks the waypoint's stay setting in ticks
 * @param shipStayTicks the ship's own stay setting in ticks
 * @param bodyDistanceSqToCenter squared distance from the ship to the centre of {@code current}
 */
public record WaypointFacts(boolean blockGuard, boolean sameDimension, boolean guardingEntity, boolean sitting,
                            boolean leashed, boolean riding, boolean ridingMount, boolean formationMember,
                            CommandPos current, boolean currentIsWaypoint, WaypointLinks links,
                            int waypointStayTicks, int shipStayTicks, double bodyDistanceSqToCenter,
                            WaypointProgress progress) {
    public WaypointFacts {
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(links, "links");
        Objects.requireNonNull(progress, "progress");
    }
}
