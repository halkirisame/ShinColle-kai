package com.lulan.shincolle.entity;

/** Pure rule for the jump-key ascent of a mount. */
public final class MountAscendRules {

    /** Upper bound of the vertical speed the jump key can build up. */
    public static final double MAX_ASCEND_MOTION = 1D;

    private MountAscendRules() {
    }

    /**
     * Vertical motion after one tick of the jump key. Only a mount standing in liquid rises.
     */
    public static double ascend(double motionY, float moveSpeed, boolean inLiquid) {
        if (!inLiquid) {
            return motionY;
        }
        return Math.min(motionY + moveSpeed * 0.1F, MAX_ASCEND_MOTION);
    }
}
