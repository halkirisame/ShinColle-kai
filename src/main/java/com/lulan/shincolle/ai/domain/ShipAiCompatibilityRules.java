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
