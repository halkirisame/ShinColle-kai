package com.lulan.shincolle.ai.domain;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShipAiCompatibilityRulesTest {

    @Test
    void targetRangeUsesKaiRoundingAndLegacyMinimum() {
        assertEquals(21, ShipAiCompatibilityRules.targetSearchRange(21.49F, 6));
        assertEquals(22, ShipAiCompatibilityRules.targetSearchRange(21.5F, 6));
        assertEquals(8, ShipAiCompatibilityRules.targetSearchRange(1.49F, 6));
        assertEquals(2, ShipAiCompatibilityRules.targetSearchRange(1.5F, 6));
    }

    @Test
    void targetSearchBoxKeepsOriginalVerticalRatio() {
        assertEquals(0D, ShipAiCompatibilityRules.targetSearchVerticalInflation(0));
        assertEquals(9D, ShipAiCompatibilityRules.targetSearchVerticalInflation(12));
        assertEquals(15.75D, ShipAiCompatibilityRules.targetSearchVerticalInflation(21));
    }

    @Test
    void aimTimeKeepsOriginalFloatFormula() {
        assertEquals(30, ShipAiCompatibilityRules.aimTime(0));
        assertEquals(20, ShipAiCompatibilityRules.aimTime(75));
        assertEquals(10, ShipAiCompatibilityRules.aimTime(149));
        assertEquals(10, ShipAiCompatibilityRules.aimTime(150));
    }

    @Test
    void aimTimeKeepsTenTickFloorAboveOriginalLevelCap() {
        assertEquals(10, ShipAiCompatibilityRules.aimTime(151));
        assertEquals(10, ShipAiCompatibilityRules.aimTime(157));
        assertEquals(10, ShipAiCompatibilityRules.aimTime(158));
        assertEquals(10, ShipAiCompatibilityRules.aimTime(1000));
    }

    @Test
    void pickupSoundPitchKeepsOriginalFloatFormula() {
        float firstSample = 0.75F;
        float secondSample = 0.25F;
        float original = ((firstSample - secondSample) * 0.7F + 1.0F) * 2.0F;

        assertEquals(Float.floatToIntBits(original), Float.floatToIntBits(
                ShipAiCompatibilityRules.pickupSoundPitch(firstSample, secondSample)));
    }

    @Test
    void pickupVoiceHitSetsOriginalCooldownAfterTwoIntegerDraws() {
        List<Integer> bounds = new ArrayList<>();
        List<Integer> soundTimes = new ArrayList<>();
        int[] samples = {0, 0, 0, 9};
        AtomicInteger sampleIndex = new AtomicInteger();

        boolean startedAtMinimum = ShipAiCompatibilityRules.tryStartPickupVoiceCooldown(0, bound -> {
            bounds.add(bound);
            return samples[sampleIndex.getAndIncrement()];
        }, soundTimes::add);
        boolean startedAtMaximum = ShipAiCompatibilityRules.tryStartPickupVoiceCooldown(0, bound -> {
            bounds.add(bound);
            return samples[sampleIndex.getAndIncrement()];
        }, soundTimes::add);

        assertTrue(startedAtMinimum);
        assertTrue(startedAtMaximum);
        assertEquals(List.of(2, 10, 2, 10), bounds);
        assertEquals(List.of(40, 49), soundTimes);
    }

    @Test
    void pickupVoiceMissAndActiveCooldownDoNotSetSoundTime() {
        AtomicInteger missedSoundTime = new AtomicInteger(-1);
        assertFalse(ShipAiCompatibilityRules.tryStartPickupVoiceCooldown(0, bound -> {
            assertEquals(2, bound);
            return 1;
        }, missedSoundTime::set));
        assertEquals(-1, missedSoundTime.get());

        AtomicInteger randomCalls = new AtomicInteger();
        AtomicInteger activeSoundTime = new AtomicInteger(12);
        assertFalse(ShipAiCompatibilityRules.tryStartPickupVoiceCooldown(12, bound -> {
            randomCalls.incrementAndGet();
            return 0;
        }, activeSoundTime::set));
        assertEquals(0, randomCalls.get());
        assertEquals(12, activeSoundTime.get());
    }
}
