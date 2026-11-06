package com.lulan.shincolle.ai.domain;

import java.util.Objects;
import java.util.Optional;

public record TargetAuthorityInput(
        int tickExisted,
        ShipTargetAuthorityState previous,
        SourceGate gate,
        Optional<ManualOrderObservation> manual,
        Optional<RevengeObservation> revenge,
        Optional<CurrentLockObservation> current,
        Optional<TargetState> autoScan,
        Optional<OwnerAssistObservation> ownerAssist) {
    public TargetAuthorityInput(
            int tickExisted, ShipTargetAuthorityState previous, SourceGate gate,
            Optional<ManualOrderObservation> manual, Optional<RevengeObservation> revenge,
            Optional<CurrentLockObservation> current, Optional<TargetState> autoScan) {
        this(tickExisted, previous, gate, manual, revenge, current, autoScan, Optional.empty());
    }

    public TargetAuthorityInput {
        if (tickExisted < 0) {
            throw new IllegalArgumentException("Entity tick must not be negative");
        }
        Objects.requireNonNull(previous, "previous");
        Objects.requireNonNull(gate, "gate");
        manual = Objects.requireNonNull(manual, "manual");
        revenge = Objects.requireNonNull(revenge, "revenge");
        current = Objects.requireNonNull(current, "current");
        autoScan = Objects.requireNonNull(autoScan, "autoScan");
        ownerAssist = Objects.requireNonNull(ownerAssist, "ownerAssist");
    }
}
