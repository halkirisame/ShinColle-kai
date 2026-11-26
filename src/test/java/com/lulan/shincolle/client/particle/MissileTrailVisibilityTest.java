package com.lulan.shincolle.client.particle;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MissileTrailVisibilityTest {
    @Test
    void torpedoBeforeLiquidDoesNotEmitEvenAfterLaunchDelay() {
        assertFalse(MissileTrailVisibility.allowsEmission(2, 3, false));
        assertFalse(MissileTrailVisibility.allowsEmission(2, 100, false));
    }

    @Test
    void torpedoAfterLiquidEmitsWithoutAnAdditionalAgeGate() {
        assertTrue(MissileTrailVisibility.allowsEmission(2, 1, true));
        assertTrue(MissileTrailVisibility.allowsEmission(2, 100, true));
    }

    @Test
    void otherMovementTypesKeepTheirThreeTickStart() {
        for (int movementType : new int[]{0, 1, 3}) {
            for (boolean enteredLiquid : new boolean[]{false, true}) {
                assertFalse(MissileTrailVisibility.allowsEmission(movementType, 2, enteredLiquid));
                assertTrue(MissileTrailVisibility.allowsEmission(movementType, 3, enteredLiquid));
            }
        }
    }
}
