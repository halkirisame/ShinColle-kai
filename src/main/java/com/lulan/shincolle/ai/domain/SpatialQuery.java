package com.lulan.shincolle.ai.domain;

import java.util.Objects;

/**
 * Minecraft-independent bounds for one nearby combat-target observation query.
 *
 * <p>The caller states intent — which source, how far — and the provider derives the
 * volume through {@link ShipAiCompatibilityRules#targetSearchBounds}. Callers, addons
 * included, never build the box themselves, so a change to the inflation rule reaches
 * every consumer at once.
 */
public record SpatialQuery(
        TargetHandle source,
        ObservationPosition center,
        ObservationBounds sourceBounds,
        double horizontalRange,
        double verticalRange) {

    public SpatialQuery {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(center, "center");
        Objects.requireNonNull(sourceBounds, "sourceBounds");
        requireRange(horizontalRange, "horizontalRange");
        requireRange(verticalRange, "verticalRange");
    }

    /** Search volume for this query, derived from the shared upstream rule. */
    public SearchBounds bounds() {
        return ShipAiCompatibilityRules.targetSearchBounds(
                this.center, this.sourceBounds, this.horizontalRange, this.verticalRange);
    }

    private static void requireRange(double value, String name) {
        if (!Double.isFinite(value) || value < 0D) {
            throw new IllegalArgumentException(name + " must be finite and non-negative");
        }
    }
}
