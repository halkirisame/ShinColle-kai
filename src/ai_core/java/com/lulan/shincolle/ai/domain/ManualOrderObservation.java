package com.lulan.shincolle.ai.domain;

import java.util.Objects;

public record ManualOrderObservation(TargetHandle target, boolean valid, boolean inManualRange) {
    public static final double MANUAL_CHASE_RANGE = 64D;

    public ManualOrderObservation {
        Objects.requireNonNull(target, "target");
    }

    /** Whether the target is close enough to acquire or retain a manual lock. */
    public static boolean inChaseRange(double distanceSquared, double retentionRange) {
        double range = Math.max(MANUAL_CHASE_RANGE, retentionRange);
        return distanceSquared <= range * range;
    }
}
