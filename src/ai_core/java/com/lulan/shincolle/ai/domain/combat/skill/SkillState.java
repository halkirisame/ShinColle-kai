package com.lulan.shincolle.ai.domain.combat.skill;

import java.util.Objects;

/** Transient combat state owned by one server entity, never by a goal. */
public record SkillState(SkillKind kind, SkillPhase phase, int ticks, int remaining) {
    public SkillState {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(phase, "phase");
        if (ticks < 0 || remaining < 0) throw new IllegalArgumentException("negative skill counter");
    }

    public static SkillState idle(SkillKind kind) {
        return new SkillState(kind, SkillPhase.IDLE, 0, 0);
    }

    /** A continuous attack occupies movement and suspends ordinary weapons. */
    public boolean running() {
        return kind != SkillKind.NAGATO && switch (phase) {
            case CHARGE, HORIZONTAL, SPIN, FINAL -> true;
            default -> false;
        };
    }
}
