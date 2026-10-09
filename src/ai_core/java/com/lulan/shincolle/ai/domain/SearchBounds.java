package com.lulan.shincolle.ai.domain;

/** Axis-aligned search volume expressed without any Minecraft type. */
public record SearchBounds(
        double minX, double minY, double minZ,
        double maxX, double maxY, double maxZ) {

    public SearchBounds {
        requireFinite(minX, "minX");
        requireFinite(minY, "minY");
        requireFinite(minZ, "minZ");
        requireFinite(maxX, "maxX");
        requireFinite(maxY, "maxY");
        requireFinite(maxZ, "maxZ");
        requireOrdered(minX, maxX, "x");
        requireOrdered(minY, maxY, "y");
        requireOrdered(minZ, maxZ, "z");
    }

    private static void requireFinite(double value, String name) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
    }

    private static void requireOrdered(double min, double max, String axis) {
        if (min > max) {
            throw new IllegalArgumentException("Search bounds are inverted on the " + axis + " axis");
        }
    }
}
