package com.lulan.shincolle.ai.domain;

public record PerceptionFreshnessPolicy(
        long maxCurrentTargetAge,
        long maxCandidateAge,
        long maxLosAge) {
    public static final PerceptionFreshnessPolicy AUTOMATIC_TARGETING =
            new PerceptionFreshnessPolicy(0, 8, 8);

    public PerceptionFreshnessPolicy {
        if (maxCurrentTargetAge < 0 || maxCandidateAge < 0 || maxLosAge < 0) {
            throw new IllegalArgumentException("Perception freshness ages must not be negative");
        }
    }
}
