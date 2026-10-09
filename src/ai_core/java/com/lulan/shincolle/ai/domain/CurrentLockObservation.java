package com.lulan.shincolle.ai.domain;

public record CurrentLockObservation(
        boolean resolvable,
        boolean alive,
        boolean inSourceRange,
        boolean invulnerablePlayer) {
    public boolean valid() {
        return resolvable && alive && inSourceRange && !invulnerablePlayer;
    }
}
