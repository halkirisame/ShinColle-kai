package com.lulan.shincolle.ai.domain;

import java.util.Objects;

public record TimedObservation<T>(T value, long observedAtTick) {
    public TimedObservation {
        Objects.requireNonNull(value, "value");
        if (observedAtTick < 0) {
            throw new IllegalArgumentException("Observation tick must not be negative");
        }
    }

    public long ageAt(long currentTick) {
        if (currentTick < this.observedAtTick) {
            throw new IllegalArgumentException("Current tick predates the observation");
        }
        return currentTick - this.observedAtTick;
    }

    public boolean isFreshAt(long currentTick, long maxAge) {
        if (maxAge < 0) {
            throw new IllegalArgumentException("Maximum observation age must not be negative");
        }
        return ageAt(currentTick) <= maxAge;
    }
}
