package com.lulan.shincolle.visualdata;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShipVisualRenderMathTest {
    @Test
    void scaleAndShadowMatchLegacyRatiosAndNeverAccumulate() {
        for (int level = 0; level <= 3; level++) {
            assertEquals(0.4F * (level + 1), ShipVisualRenderMath.scale(0.4F, level));
            assertEquals(0.46F * (level + 1), ShipVisualRenderMath.scale(0.46F, level));
            assertEquals(0.35F + level * 0.4F, ShipVisualRenderMath.shadow(0.35F, level));
        }
        assertEquals(0.46F, ShipVisualRenderMath.scale(0.46F, 0));
        assertEquals(0.35F, ShipVisualRenderMath.shadow(0.35F, 0));
    }

    @Test
    void everyDrawingCalculationClampsScaleLevel() {
        for (int invalid : new int[]{Integer.MIN_VALUE, -1, 4, Integer.MAX_VALUE}) {
            int expected = invalid < 0 ? 0 : 3;
            assertEquals(expected, ShipVisualRenderMath.scaleLevel(invalid));
            assertEquals(ShipVisualRenderMath.scale(0.46F, expected), ShipVisualRenderMath.scale(0.46F, invalid));
            assertEquals(ShipVisualRenderMath.shadow(0.35F, expected), ShipVisualRenderMath.shadow(0.35F, invalid));
        }
    }

    @Test
    void lodUsesStrictSquaredCameraDistanceAtEveryBoundary() {
        for (float distance : new float[]{1, 12.5F, 48, 256}) {
            double squared = (double) distance * distance;
            assertFalse(ShipVisualRenderMath.distant(0, distance));
            assertFalse(ShipVisualRenderMath.distant(Math.nextDown(squared), distance));
            assertFalse(ShipVisualRenderMath.distant(squared, distance));
            assertTrue(ShipVisualRenderMath.distant(Math.nextUp(squared), distance));
        }
    }
}
