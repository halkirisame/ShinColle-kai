package com.lulan.shincolle.ai.domain;

import java.util.Objects;

public record CurrentLockObservation(
        boolean resolvable,
        boolean alive,
        boolean inSourceRange,
        boolean invulnerablePlayer,
        TargetPriorityTier priority) {
    public CurrentLockObservation(
            boolean resolvable, boolean alive, boolean inSourceRange, boolean invulnerablePlayer) {
        this(resolvable, alive, inSourceRange, invulnerablePlayer, TargetPriorityTier.NORMAL);
    }

    public CurrentLockObservation {
        Objects.requireNonNull(priority, "priority");
    }

    public boolean valid() {
        return resolvable && alive && inSourceRange && !invulnerablePlayer;
    }
}
