package com.lulan.shincolle.ai.domain;

import java.util.Objects;

public record ManualOrderObservation(TargetHandle target, boolean valid, boolean inManualRange) {
    public ManualOrderObservation {
        Objects.requireNonNull(target, "target");
    }
}
