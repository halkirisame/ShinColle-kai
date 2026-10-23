package com.lulan.shincolle.ai.domain.combat;

import java.util.Objects;
import java.util.OptionalInt;

/** Transient burst authority; an evaluation mark also survives completion and restart. */
public record RuBurstState(Phase phase, OptionalInt lastEvaluatedTick) {
    public RuBurstState {
        Objects.requireNonNull(phase, "phase");
        Objects.requireNonNull(lastEvaluatedTick, "lastEvaluatedTick");
    }

    public sealed interface Phase permits Idle, Burst { }
    public record Idle() implements Phase { }
    public record Aim(int x, int y, int z) { }
    public record Burst(int remaining, Aim fixedAim) implements Phase {
        public Burst {
            if (remaining <= 0) throw new IllegalArgumentException("remaining must be positive");
            Objects.requireNonNull(fixedAim, "fixedAim");
        }
    }

    public static RuBurstState initial() {
        return new RuBurstState(new Idle(), OptionalInt.empty());
    }

    public boolean busy() {
        return this.phase instanceof Burst;
    }
}
