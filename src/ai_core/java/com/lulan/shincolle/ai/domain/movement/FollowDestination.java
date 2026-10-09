package com.lulan.shincolle.ai.domain.movement;

/** What a following ship's stored destination stands for. */
public enum FollowDestination {
    /** The owner itself: being near the owner is having arrived. */
    OWNER,
    /**
     * The formation's flagship slot: a rounded and sometimes moved block next to the owner, not the owner's own
     * position. Being near the owner is not arriving; being near the slot as worked out now is.
     */
    FLAGSHIP_PLACE,
    /** The ship's place in a formation, which can be far from the owner: being near the owner is not arriving. */
    FORMATION_PLACE
}
