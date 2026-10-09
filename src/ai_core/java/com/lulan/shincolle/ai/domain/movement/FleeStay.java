package com.lulan.shincolle.ai.domain.movement;

/**
 * While fleeing, the ship stays beside its owner: it walks back once it is more than four
 * blocks away and stops within the original flee goal's distance.
 */
public final class FleeStay {
    static final double STOP_DISTANCE_SQ = 6D;
    static final double WALK_DISTANCE_SQ = 16D;

    private FleeStay() { }

    public static boolean shouldWalk(boolean walking, double ownerDistanceSq) {
        return ownerDistanceSq > (walking ? STOP_DISTANCE_SQ : WALK_DISTANCE_SQ);
    }
}
