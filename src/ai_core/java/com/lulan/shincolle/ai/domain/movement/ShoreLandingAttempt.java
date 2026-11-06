package com.lulan.shincolle.ai.domain.movement;

/** Transient, body-owned budget for one shore target. */
public record ShoreLandingAttempt(long targetKey, int liftTicks, long blockedUntil) {
    public static ShoreLandingAttempt start(long targetKey) {
        return new ShoreLandingAttempt(targetKey, 0, 0);
    }
}
