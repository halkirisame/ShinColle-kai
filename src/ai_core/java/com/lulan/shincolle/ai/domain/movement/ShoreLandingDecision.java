package com.lulan.shincolle.ai.domain.movement;

import java.util.EnumSet;

/** A deterministic shore assistance result, including all applicable refusals. */
public record ShoreLandingDecision(Action action, EnumSet<Reason> reasons, ShoreLandingAttempt next) {
    public ShoreLandingDecision {
        reasons = reasons.clone();
    }

    @Override
    public EnumSet<Reason> reasons() {
        return reasons.clone();
    }

    public enum Action { LIFT, NONE }

    public enum Reason {
        NOT_MOVING, FLYING, PLAYER_CONTROLLED, ON_GROUND, NOT_AFLOAT, NO_HORIZONTAL_COLLISION,
        NO_LEDGE, TARGET_NOT_ON_LEDGE, LEDGE_TOO_HIGH, NO_HEADROOM, ATTEMPT_LIMIT
    }
}
