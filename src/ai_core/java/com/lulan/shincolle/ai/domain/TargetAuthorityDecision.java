package com.lulan.shincolle.ai.domain;

import java.util.Objects;

public record TargetAuthorityDecision(
        ShipTargetAuthorityState next,
        boolean consumeRevenge,
        boolean autoScanPerformed) {
    public TargetAuthorityDecision {
        Objects.requireNonNull(next, "next");
    }
}
