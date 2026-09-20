package com.lulan.shincolle.ai.domain;

import java.util.Objects;
import java.util.Optional;

public record TargetLineOfSightObservation(
        TargetHandle target,
        boolean measurementRequired,
        Optional<TimedObservation<Boolean>> result) {
    public TargetLineOfSightObservation {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(result, "result");
        if (measurementRequired != result.isPresent()) {
            throw new IllegalArgumentException(
                    "LOS result presence must match whether measurement is required");
        }
    }

    public static TargetLineOfSightObservation notMeasured(TargetHandle target) {
        return new TargetLineOfSightObservation(target, false, Optional.empty());
    }

    public static TargetLineOfSightObservation measured(
            TargetHandle target, boolean visible, long tick) {
        return new TargetLineOfSightObservation(
                target, true, Optional.of(new TimedObservation<>(visible, tick)));
    }
}
