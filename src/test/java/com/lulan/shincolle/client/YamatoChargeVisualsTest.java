package com.lulan.shincolle.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YamatoChargeVisualsTest {
    @Test
    void standingHeightRemainsUnchangedAtEveryScale() {
        for (int scale = 0; scale <= 3; scale++) {
            double height = 2.1D * (scale + 1);
            assertEquals(height * 0.6D, YamatoChargeVisuals.heightOffset(height, scale, false, true, false));
        }
    }

    @Test
    void muzzleOffsetPointsAlongBodyYawAtTwiceTheWidth() {
        double[] south = YamatoChargeVisuals.forwardOffset(1.3D, 0D);
        assertEquals(0D, south[0], 1E-12);
        assertEquals(2.6D, south[1], 1E-12);
        double[] west = YamatoChargeVisuals.forwardOffset(1.3D, Math.PI / 2D);
        assertEquals(-2.6D, west[0], 1E-12);
        assertEquals(0D, west[1], 1E-12);
    }

    @Test
    void sittingAndRidingFollowBothModelPoseTranslationsAtEveryScale() {
        double[] scales = {0.5D, 1D, 1.5D, 2D};
        for (int level = 0; level < scales.length; level++) {
            double height = 2.1D * (level + 1);
            double standing = height * 0.6D;
            for (double poseY : new double[] {0.4D, 0.5D, 0.54D}) {
                double lowered = YamatoChargeVisuals.heightOffset(height, level, true, poseY == 0.4D, poseY == 0.5D);
                assertEquals(standing - poseY - poseY * scales[level], lowered, 1E-12);
                assertTrue(lowered < standing);
            }
        }
    }
}
