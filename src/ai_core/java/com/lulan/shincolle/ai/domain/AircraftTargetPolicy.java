package com.lulan.shincolle.ai.domain;

import java.util.Optional;

/** Priority candidates override living old targets without changing normal fallback. */
public final class AircraftTargetPolicy {
    private AircraftTargetPolicy() { }

    public static Optional<AircraftPriorityTarget> select(
            boolean active, Optional<TargetLock> manual, Optional<OwnerAssistObservation> assist) {
        if (!active) return Optional.empty();
        Optional<AircraftPriorityTarget> ordered = manual.filter(lock -> lock.source() == TargetSource.MANUAL)
                .map(lock -> new AircraftPriorityTarget(lock.target(), TargetSource.MANUAL, Optional.empty()));
        return ordered.or(() -> assist.filter(OwnerAssistObservation::valid)
                .map(observation -> new AircraftPriorityTarget(observation.attack().target(),
                        TargetSource.OWNER_ASSIST, Optional.of(observation.attack()))));
    }
}
