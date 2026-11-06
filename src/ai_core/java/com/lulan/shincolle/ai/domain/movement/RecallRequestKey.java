package com.lulan.shincolle.ai.domain.movement;

import java.util.UUID;
import java.util.Objects;

/** A transient ship-specific authority, distinct from the departure's timing. */
public record RecallRequestKey(UUID serverRun, UUID owner, long login, UUID ship, int shipUid, long generation) {
    public RecallRequestKey {
        Objects.requireNonNull(serverRun);
        Objects.requireNonNull(owner);
        Objects.requireNonNull(ship);
    }
}
