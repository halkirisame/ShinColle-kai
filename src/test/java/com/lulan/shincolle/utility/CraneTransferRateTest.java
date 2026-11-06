package com.lulan.shincolle.utility;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class CraneTransferRateTest {
    @Test
    void zeroPumpsCannotTransfer() {
        assertEquals(0, CraneTransferRate.perCycle(100, 0, 0, 40, 5));
    }

    @Test
    void multipliesWholeCycleAndPreservesFractionalLevelFactor() {
        assertEquals(1536, CraneTransferRate.perCycle(2, 2, 0, 40, 5));
        assertEquals(1728, CraneTransferRate.perCycle(2, 2, 2, 40, 5));
    }

    @Test
    void clampsNegativeSettingsAndOverflow() {
        assertEquals(0, CraneTransferRate.perCycle(10, 1, 1, -40, -5));
        assertEquals(Integer.MAX_VALUE, CraneTransferRate.perCycle(Integer.MAX_VALUE, 6, 20, Integer.MAX_VALUE, 100));
    }
}
