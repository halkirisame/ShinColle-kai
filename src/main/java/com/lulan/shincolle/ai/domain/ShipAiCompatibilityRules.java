package com.lulan.shincolle.ai.domain;

import java.util.function.IntConsumer;
import java.util.function.IntUnaryOperator;

/** Pure formulas retained by the observable AI compatibility fixtures. */
public final class ShipAiCompatibilityRules {

    private ShipAiCompatibilityRules() {
    }

    public static int targetSearchRange(float attackRange, int followMax) {
        int range = Math.round(attackRange);
        return range < 2 ? Math.max(2, followMax + 2) : range;
    }

    public static double targetSearchVerticalInflation(int range) {
        return range * 0.75D;
    }

    /**
     * Search volume for one nearby-target query, matching upstream exactly.
     *
     * <p>1.10.2 and the current Goal both inflate the source's <em>bounding box</em>
     * (`getEntityBoundingBox().expand(...)` / `getBoundingBox().inflate(...)`), not a
     * point. That box spans the position by half the width horizontally but runs from
     * the position up to the full height vertically, so the volume is asymmetric on Y.
     * Centering a symmetric box on the position instead loses half the width on X/Z and
     * the whole height on Y.
     *
     * <p>The half-width division is kept in float because
     * `EntityDimensions.makeBoundingBox` computes `float f = this.width / 2.0F` before
     * widening, so the faces produced here are bit-identical to the Minecraft box.
     */
    public static SearchBounds targetSearchBounds(ObservationPosition center, ObservationBounds source,
                                                  double horizontalRange, double verticalRange) {
        if (center == null || source == null) {
            throw new IllegalArgumentException("Search bounds require a center and source size");
        }
        requireRange(horizontalRange, "horizontalRange");
        requireRange(verticalRange, "verticalRange");
        double halfWidth = source.width() / 2.0F;
        return new SearchBounds(
                center.x() - halfWidth - horizontalRange,
                center.y() - verticalRange,
                center.z() - halfWidth - horizontalRange,
                center.x() + halfWidth + horizontalRange,
                center.y() + source.height() + verticalRange,
                center.z() + halfWidth + horizontalRange);
    }

    private static void requireRange(double value, String name) {
        if (!Double.isFinite(value) || value < 0D) {
            throw new IllegalArgumentException(name + " must be finite and non-negative");
        }
    }

    public static int aimTime(int level) {
        int legacyAimTime = (int) (20F * (150 - level) / 150F) + 10;
        return Math.max(10, legacyAimTime);
    }

    public static float pickupSoundPitch(float firstSample, float secondSample) {
        return ((firstSample - secondSample) * 0.7F + 1.0F) * 2.0F;
    }

    public static boolean tryStartPickupVoiceCooldown(int soundTime, IntUnaryOperator nextInt,
                                                       IntConsumer setSoundTime) {
        if (soundTime > 0 || nextInt.applyAsInt(2) != 0) {
            return false;
        }

        setSoundTime.accept(40 + nextInt.applyAsInt(10));
        return true;
    }
}
