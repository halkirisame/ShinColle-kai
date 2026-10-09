package com.lulan.shincolle.ai.domain.movement;

import java.util.Objects;

/** Where a ship's head is asked to turn, how fast, and why. */
public record LookRequest(MovementTarget target, float yawSpeed, float pitchSpeed, LookReason reason) {
    public LookRequest {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(reason, "reason");
        if (target instanceof MovementTarget.Around) {
            throw new IllegalArgumentException("A head cannot look around a point");
        }
    }
}
