package com.lulan.shincolle.ai.domain;

import java.util.Objects;
import java.util.Optional;

/** A confirmed attack, timed at damage dispatch rather than at observation. */
public record RecentOwnerAttack(TargetHandle target, long occurredAt, long sequence) {
    public RecentOwnerAttack {
        Objects.requireNonNull(target, "target");
    }

    public boolean currentAt(long now) {
        long age = now - this.occurredAt;
        return age >= 0L && age <= 200L;
    }

    /** Later failures must not erase an earlier success, including in the same tick. */
    public static Optional<RecentOwnerAttack> confirm(
            Optional<RecentOwnerAttack> previous, RecentOwnerAttack candidate, float damage, boolean canceled) {
        if (canceled || !Float.isFinite(damage) || damage <= 0F) return previous;
        return previous.filter(attack -> attack.sequence() > candidate.sequence()).or(() -> Optional.of(candidate));
    }
}
