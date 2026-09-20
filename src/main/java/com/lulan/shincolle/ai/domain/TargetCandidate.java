package com.lulan.shincolle.ai.domain;

import java.util.Objects;

/** Immutable input to the Stage 2 combat-target decision pipeline. */
public record TargetCandidate(
        TargetHandle handle,
        ClassifiedTargetObservation observation,
        double distanceSquared) {

    public TargetCandidate {
        Objects.requireNonNull(handle, "handle");
        Objects.requireNonNull(observation, "observation");
        if (!Double.isFinite(distanceSquared) || distanceSquared < 0D) {
            throw new IllegalArgumentException("Target distance squared must be finite and non-negative");
        }
    }
}
