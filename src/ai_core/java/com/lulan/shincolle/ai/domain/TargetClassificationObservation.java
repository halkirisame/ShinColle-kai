package com.lulan.shincolle.ai.domain;

import java.util.Objects;

public record TargetClassificationObservation(
        TargetHandle target,
        TimedObservation<ClassifiedTargetObservation> classification,
        TargetLineOfSightObservation lineOfSight) {
    public TargetClassificationObservation {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(classification, "classification");
        Objects.requireNonNull(lineOfSight, "lineOfSight");
        if (!target.equals(lineOfSight.target())) {
            throw new IllegalArgumentException("Classification and LOS targets must match");
        }
        if (lineOfSight.measurementRequired()
                && lineOfSight.result().orElseThrow().observedAtTick()
                != classification.observedAtTick()) {
            throw new IllegalArgumentException("Classification and LOS ticks must match");
        }
    }

    public boolean isFreshAt(long currentTick, PerceptionFreshnessPolicy policy) {
        Objects.requireNonNull(policy, "policy");
        if (!this.classification.isFreshAt(currentTick, policy.maxCandidateAge())) {
            return false;
        }
        return !this.lineOfSight.measurementRequired()
                || this.lineOfSight.result().orElseThrow()
                .isFreshAt(currentTick, policy.maxLosAge());
    }
}
