package com.lulan.shincolle.ai.domain;

import java.util.Objects;

/** Minecraft-independent bounds for one nearby combat-target observation query. */
public record SpatialQuery(
        TargetHandle source,
        ObservationPosition center,
        double horizontalRange,
        double verticalRange) {

    public SpatialQuery {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(center, "center");
        requireRange(horizontalRange, "horizontalRange");
        requireRange(verticalRange, "verticalRange");
    }

    private static void requireRange(double value, String name) {
        if (!Double.isFinite(value) || value < 0D) {
            throw new IllegalArgumentException(name + " must be finite and non-negative");
        }
    }
}
