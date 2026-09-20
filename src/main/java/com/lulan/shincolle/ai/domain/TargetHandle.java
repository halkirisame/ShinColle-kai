package com.lulan.shincolle.ai.domain;

import java.util.Objects;
import java.util.UUID;

public record TargetHandle(UUID uuid, DimensionKey dimension) implements Comparable<TargetHandle> {
    public TargetHandle {
        Objects.requireNonNull(uuid, "uuid");
        Objects.requireNonNull(dimension, "dimension");
    }

    @Override
    public int compareTo(TargetHandle other) {
        Objects.requireNonNull(other, "other");
        int dimensionOrder = this.dimension.compareTo(other.dimension);
        if (dimensionOrder != 0) {
            return dimensionOrder;
        }
        int highOrder = Long.compareUnsigned(
                this.uuid.getMostSignificantBits(), other.uuid.getMostSignificantBits());
        if (highOrder != 0) {
            return highOrder;
        }
        return Long.compareUnsigned(
                this.uuid.getLeastSignificantBits(), other.uuid.getLeastSignificantBits());
    }
}
