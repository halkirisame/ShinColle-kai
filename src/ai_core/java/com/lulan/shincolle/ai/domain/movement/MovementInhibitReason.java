package com.lulan.shincolle.ai.domain.movement;

public enum MovementInhibitReason {
    SITTING,
    RIDING,
    RIDING_SHIP,
    LEASHED,
    CRANE,
    FISHING,
    NO_GRUDGE,
    PICK_ITEM_OFF,
    /** Fleeing: the ship stays beside its owner. */
    FLEEING,
    /** Engaged with a target: the head stays on it, so the ship does not look around. */
    ENGAGED,
    /** A walk for work whose point lies outside the region the intent allows. */
    OUTSIDE_REGION
}
