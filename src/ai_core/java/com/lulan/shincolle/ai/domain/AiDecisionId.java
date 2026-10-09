package com.lulan.shincolle.ai.domain;

/** Identifies one AI decision without retaining a Minecraft runtime object. */
public record AiDecisionId(long tick, long sequence) {

    public AiDecisionId {
        if (tick < 0) {
            throw new IllegalArgumentException("tick must not be negative");
        }
        if (sequence < 0) {
            throw new IllegalArgumentException("sequence must not be negative");
        }
    }
}
