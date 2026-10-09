package com.lulan.shincolle.ai.domain.movement;

/** The recovery a stuck ship tries, in order: the same path again, a point beside it, then giving up. */
public enum StuckStage {
    NONE,
    REPATH,
    DETOUR,
    /** Picking items and fighting give up; following, guarding and fleeing keep going and may teleport. */
    GIVE_UP;

    static StuckStage of(int stuckWindows) {
        if (stuckWindows <= 0) return NONE;
        if (stuckWindows == 1) return REPATH;
        if (stuckWindows == 2) return DETOUR;
        return GIVE_UP;
    }
}
