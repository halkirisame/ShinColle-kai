package com.lulan.shincolle.ai.domain;

/** Observer for one Stage 2 candidate-selection evaluation. */
@FunctionalInterface
public interface TargetSelectionProfiler {

    TargetSelectionProfiler NONE = (candidateCount, eligibleCount) -> {
    };

    /** Called exactly once after eligibility filtering for each selection evaluation. */
    void recordSelection(int candidateCount, int eligibleCount);
}
