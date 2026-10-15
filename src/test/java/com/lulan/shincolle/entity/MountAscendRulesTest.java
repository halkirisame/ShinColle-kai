package com.lulan.shincolle.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MountAscendRulesTest {

    @Test
    void inLiquidMotionYRisesByTenthOfMoveSpeed() {
        assertEquals(0.05D + 0.5F * 0.1F, MountAscendRules.ascend(0.05D, 0.5F, true), 1.0E-9);
    }

    @Test
    void outsideLiquidMotionYIsUnchanged() {
        assertEquals(-0.005D, MountAscendRules.ascend(-0.005D, 0.5F, false), 0D);
    }

    @Test
    void ascentStopsAtTheCap() {
        assertEquals(1D, MountAscendRules.ascend(0.99D, 0.5F, true), 0D);
        assertEquals(1D, MountAscendRules.ascend(1D, 0.5F, true), 0D);
    }
}
