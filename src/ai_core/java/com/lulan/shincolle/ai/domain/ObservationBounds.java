package com.lulan.shincolle.ai.domain;

/**
 * Size of an observed entity, independent of any runtime representation.
 *
 * <p>Width and height are kept as floats because the upstream search volume derives
 * its half-width with a float division; see
 * {@link ShipAiCompatibilityRules#targetSearchBounds}.
 */
public record ObservationBounds(float width, float height) {

    public ObservationBounds {
        requireSize(width, "width");
        requireSize(height, "height");
    }

    private static void requireSize(float value, String name) {
        if (!Float.isFinite(value) || value < 0F) {
            throw new IllegalArgumentException(name + " must be finite and non-negative");
        }
    }
}
