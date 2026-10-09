package com.lulan.shincolle.ai.domain.movement;

/** Why a ship's head is turned where it is. */
public enum LookReason {
    /** Nothing to fight: toward the admiral being followed. */
    FOLLOW_OWNER,
    /** Nothing to fight: toward the point being guarded. */
    GUARD,
    /** Engaged: toward the locked target, whatever the ship is walking to. */
    ENGAGED_TARGET,
    /** Toward the point a command has just set. */
    COMMAND_APPLIED,
    /** Toward the next waypoint of a route. */
    WAYPOINT_ADVANCED
}
