package com.lulan.shincolle.ai.domain.movement;

/** The intent (or its detail) a movement constraint came from. */
public enum ConstraintSource {
    FOLLOW_OWNER,
    GUARD_POSITION,
    GUARD_ENTITY,
    FORMATION,
    FLEE,
    SIT
}
