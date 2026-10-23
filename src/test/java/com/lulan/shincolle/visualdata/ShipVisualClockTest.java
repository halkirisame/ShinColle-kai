package com.lulan.shincolle.visualdata;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShipVisualClockTest {
    @Test
    void addsThePartialTickSoTimeAdvancesBetweenTicks() {
        assertEquals(100.25, ShipVisualClock.continuousTick(100, 0.25F), 0.0);
        assertTrue(ShipVisualClock.continuousTick(100, 0.5F) > ShipVisualClock.continuousTick(100, 0.25F));
    }

    @Test
    void doesNotGoBackwardsAcrossATickBoundary() {
        assertTrue(ShipVisualClock.continuousTick(101, 0.0F) >= ShipVisualClock.continuousTick(100, 1.0F));
    }

    @Test
    void clampsAnOutOfRangePartialTick() {
        assertEquals(100.0, ShipVisualClock.continuousTick(100, -0.5F), 0.0);
        assertEquals(101.0, ShipVisualClock.continuousTick(100, 2.0F), 0.0);
    }
}
