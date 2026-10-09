package com.lulan.shincolle.ai.domain;

import java.util.Objects;

public record RevengeObservation(
        TargetHandle target,
        int revengeTick,
        boolean acceptedByRevengePredicate) {
    public RevengeObservation {
        Objects.requireNonNull(target, "target");
    }
}
