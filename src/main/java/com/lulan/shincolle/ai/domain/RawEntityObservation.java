package com.lulan.shincolle.ai.domain;

import java.util.Objects;

public record RawEntityObservation(
        TargetHandle handle,
        EntityTypeKey entityType,
        RelationIdentity relationIdentity,
        ObservationPosition position,
        boolean alive) {
    public RawEntityObservation {
        Objects.requireNonNull(handle, "handle");
        Objects.requireNonNull(entityType, "entityType");
        Objects.requireNonNull(relationIdentity, "relationIdentity");
        Objects.requireNonNull(position, "position");
    }
}
