package com.lulan.shincolle.ai.domain;

import java.util.Objects;

/** Provides the single time authority for one AI evaluation. */
public record AiTickContext(long tick, AiDecisionId decisionId) {

    public AiTickContext {
        if (tick < 0) {
            throw new IllegalArgumentException("tick must not be negative");
        }
        Objects.requireNonNull(decisionId, "decisionId");
        if (tick != decisionId.tick()) {
            throw new IllegalArgumentException("context tick must match the decision ID tick");
        }
    }
}
