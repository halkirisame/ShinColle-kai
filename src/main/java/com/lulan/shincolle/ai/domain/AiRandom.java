package com.lulan.shincolle.ai.domain;

import java.util.Objects;

/** Supplies one uniform bounded draw without assuming ownership of generator state. */
@FunctionalInterface
public interface AiRandom {

    /** Returns a value in [0, bound); non-positive bounds must be rejected. */
    int nextBoundedInt(int bound);

    /** Validates a provider without correcting invalid values or consuming extra draws. */
    static AiRandom checked(AiRandom source) {
        Objects.requireNonNull(source, "source");
        return bound -> {
            if (bound <= 0) {
                throw new IllegalArgumentException("Bound must be positive");
            }
            int value = source.nextBoundedInt(bound);
            if (value < 0 || value >= bound) {
                throw new IllegalArgumentException("Random source returned an out-of-range value");
            }
            return value;
        };
    }
}
