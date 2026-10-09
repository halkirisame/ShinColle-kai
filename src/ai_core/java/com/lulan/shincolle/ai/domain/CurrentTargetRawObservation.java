package com.lulan.shincolle.ai.domain;

import java.util.Objects;
import java.util.Optional;

public record CurrentTargetRawObservation(
        TimedObservation<Optional<RawEntityObservation>> observation) {
    public CurrentTargetRawObservation {
        Objects.requireNonNull(observation, "observation");
    }

    public static CurrentTargetRawObservation observed(RawEntityObservation target, long tick) {
        Objects.requireNonNull(target, "target");
        return new CurrentTargetRawObservation(new TimedObservation<>(Optional.of(target), tick));
    }

    public static CurrentTargetRawObservation absent(long tick) {
        return new CurrentTargetRawObservation(new TimedObservation<>(Optional.empty(), tick));
    }

    public long ageAt(long currentTick) {
        return this.observation.ageAt(currentTick);
    }

    public boolean isFreshAt(long currentTick, long maxAge) {
        return this.observation.isFreshAt(currentTick, maxAge);
    }
}
