package com.lulan.shincolle.ai.domain;

import java.util.Objects;
import java.util.Optional;

/** Session-only target authority state owned by one ship. */
public record ShipTargetAuthorityState(
        Optional<TargetLock> lock,
        TargetScanSchedule autoScan,
        int lastConsumedRevengeTick) {
    public ShipTargetAuthorityState {
        lock = Objects.requireNonNull(lock, "lock");
        Objects.requireNonNull(autoScan, "autoScan");
    }

    public static ShipTargetAuthorityState initial() {
        return new ShipTargetAuthorityState(Optional.empty(), new TargetScanSchedule(0L), 0);
    }
}
