package com.lulan.shincolle.ai.domain.waypoint;

/** Why a ship does not take part in waypoint traversal this check. */
public enum WaypointSkipReason {
    NO_BLOCK_GUARD,
    OTHER_DIMENSION,
    GUARDING_ENTITY,
    SITTING,
    LEASHED,
    RIDING,
    FORMATION_MEMBER,
    NOT_A_WAYPOINT,
    NOT_ARRIVED
}
