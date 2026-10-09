package com.lulan.shincolle.ai.domain;

import java.util.Objects;

/** Current combat-target authority without a live Minecraft entity reference. */
public record TargetLock(TargetHandle target, TargetSource source, long acquiredAtTick) {
    public TargetLock {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(source, "source");
        if (acquiredAtTick < 0L) {
            throw new IllegalArgumentException("Acquisition tick must not be negative");
        }
    }
}
