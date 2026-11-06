package com.lulan.shincolle.ai.domain;

import java.util.Objects;
import java.util.Optional;

/** Transient aircraft priority projection, not its ordinary target retention policy. */
public record AircraftPriorityTarget(TargetHandle target, TargetSource source, Optional<RecentOwnerAttack> attack) {
    public AircraftPriorityTarget {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(attack, "attack");
    }
}
