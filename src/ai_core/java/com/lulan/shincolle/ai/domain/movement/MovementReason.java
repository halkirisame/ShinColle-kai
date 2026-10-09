package com.lulan.shincolle.ai.domain.movement;

/** Why a step was planned, for explaining a move afterwards. */
public enum MovementReason {
    /** A validated built-in special attack. */
    SKILL_ATTACK,
    /** An accepted block-pointer move inside the vertical separation window. */
    VERTICAL_RECALL,
    /** Sustained airborne descent in a configured void dimension. */
    VOID_RESCUE,
    FOLLOW_OWNER,
    FOLLOW_ARRIVED,
    GUARD,
    GUARD_ARRIVED,
    MOVE_ARRIVED,
    FLEE_TO_OWNER,
    WANDER,
    SIT,
    MOUNT_TO_HOST,
    PICK_ITEM,
    PICK_ITEM_REACHED,
    COMBAT_TOWARD_TARGET,
    COMBAT_TO_REGION_EDGE,
    COMBAT_HOLD,
    GOAL_STOPPED,
    /** Stuck on the way: the same path again. */
    STUCK_REPATH,
    /** Still stuck: a path to a reachable point beside where the ship was going. */
    STUCK_DETOUR,
    /** Stuck for long enough: stop trying to get there. */
    STUCK_GIVE_UP,
    TELEPORT_FAR,
    /** Stuck for longer than the teleport cooldown. */
    TELEPORT_TIME,
    TELEPORT_PATH_FAILED,
    /** Fleeing and stuck for long enough. */
    TELEPORT_STUCK,
    /** A mount left too far behind the ship it carries. */
    MOUNT_RECALL,
    /** The way to a point a command has just set. */
    COMMAND_APPLIED,
    /** The way on to the next waypoint of a route. */
    WAYPOINT_ADVANCED,
    /** Back to the waypoint a cooking or crafting ship works at. */
    TASK_RETURN,
    /** Toward the spot a fishing ship casts from. */
    TASK_FISHING_SPOT,
    /** The small walk a mining ship takes while it stands still. */
    TASK_MINING_SHUFFLE
}
