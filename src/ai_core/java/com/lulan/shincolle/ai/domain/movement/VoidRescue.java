package com.lulan.shincolle.ai.domain.movement;

import java.util.OptionalLong;

/** Consecutive falling ticks, with a bounded retry while the owner's landing is unavailable. */
public final class VoidRescue {
    public static final int FALL_TICKS = 40;
    public static final int RETRY_TICKS = 20;

    private VoidRescue() { }

    public record Facts(boolean enabledDimension, double y, double velocityY, boolean grounded,
                        boolean inWater, boolean climbing, boolean riding, boolean flying,
                        boolean ownerPresent, boolean ownerGrounded) { }
    public record State(int fallingTicks, OptionalLong observedAt, OptionalLong retryAt) {
        public static State empty() {
            return new State(0, OptionalLong.empty(), OptionalLong.empty());
        }
    }
    public record Observation(State state, boolean rescue) { }

    public static Observation observe(State state, Facts facts, long now) {
        if (state.observedAt().isPresent()) {
            long previous = state.observedAt().getAsLong();
            if (now <= previous) return new Observation(state, false);
            if (now != previous + 1) state = State.empty();
        }
        boolean falling = facts.enabledDimension() && facts.velocityY() < 0D && !facts.grounded()
                && !facts.inWater() && !facts.climbing() && !facts.riding() && !facts.flying();
        if (!falling) return new Observation(State.empty(), false);
        int ticks = Math.min(FALL_TICKS, state.fallingTicks() + 1);
        OptionalLong retry = state.retryAt();
        boolean due = (ticks >= FALL_TICKS || facts.y() < 0D)
                && (retry.isEmpty() || now >= retry.getAsLong());
        if (due) retry = OptionalLong.of(now + RETRY_TICKS);
        return new Observation(new State(ticks, OptionalLong.of(now), retry),
                due && facts.ownerPresent() && facts.ownerGrounded());
    }
}
