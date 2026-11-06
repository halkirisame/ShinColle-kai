package com.lulan.shincolle.ai.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManualOrderObservationTest {
    @Test
    void manualChaseReachesBeyondAttackRange() {
        assertTrue(ManualOrderObservation.inChaseRange(8D * 8D, 4D));
    }

    @Test
    void manualChaseIncludesSixtyFourButNotSixtyFive() {
        assertTrue(ManualOrderObservation.inChaseRange(64D * 64D, 4D));
        assertFalse(ManualOrderObservation.inChaseRange(65D * 65D, 4D));
    }

    @Test
    void longerRetentionRangeIsNotShortened() {
        assertTrue(ManualOrderObservation.inChaseRange(80D * 80D, 80D));
        assertFalse(ManualOrderObservation.inChaseRange(81D * 81D, 80D));
    }
}
