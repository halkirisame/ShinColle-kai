package com.lulan.shincolle.ai.domain.movement;

/**
 * The squared distances within which following or guarding stops, and beyond which it starts.
 */
public record FollowRange(double minSq, double maxSq) {

    /** Outside a formation: FollowMin and FollowMax plus three quarters of the width, as floats. */
    static FollowRange loose(int followMin, int followMax, float width, boolean pickItem) {
        float min = followMin + width * 0.75F;
        float max = followMax + width * 0.75F;
        // picking items up lets the ship stray five blocks further
        if (pickItem) max += 5F;
        return new FollowRange(min * min, max * max);
    }

    /** In a formation the distances are fixed; picking items up widens the outer one to 64. */
    static FollowRange formation(double minSq, double maxSq, boolean pickItem) {
        return new FollowRange(minSq, pickItem ? 64D : maxSq);
    }

    /**
     * Near a waypoint the route goes on from, within the arrival range: the inner distance is at most the
     * pass-through one, and so is the outer unless {@code keepOuter} lets it stand.
     */
    FollowRange passingThrough(boolean keepOuter) {
        return new FollowRange(Math.min(minSq, GuardMovePlanner.PASS_THROUGH_MIN_SQ),
                keepOuter ? maxSq : Math.min(maxSq, GuardMovePlanner.PASS_THROUGH_MAX_SQ));
    }
}
