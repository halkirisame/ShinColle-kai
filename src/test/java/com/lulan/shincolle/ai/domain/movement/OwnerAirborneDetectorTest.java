package com.lulan.shincolle.ai.domain.movement;

import com.lulan.shincolle.ai.domain.DimensionKey;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OwnerAirborneDetectorTest {
    private static final DimensionKey OVERWORLD = new DimensionKey("minecraft", "overworld");
    private static final DimensionKey NETHER = new DimensionKey("minecraft", "the_nether");
    private static final OwnerAirborneDetector.Facts STANDING = new OwnerAirborneDetector.Facts(false, true);
    private static final OwnerAirborneDetector.Facts IN_AIR = new OwnerAirborneDetector.Facts(false, false);
    private static final OwnerAirborneDetector.Facts GLIDING = new OwnerAirborneDetector.Facts(true, false);

    private static OwnerAirborneDetector.State at(OwnerAirborneDetector.State state, double y,
                                                  OwnerAirborneDetector.Facts facts, long now) {
        return OwnerAirborneDetector.observe(state, OVERWORLD, new MovementPoint(0D, y, 0D), facts, now);
    }

    @Test
    void glidingIsAirborneAtOnceEvenWithNoHistory() {
        assertTrue(at(OwnerAirborneDetector.State.initial(OVERWORLD), 100D, GLIDING, 0).airborne());
    }

    @Test
    void standingOrFirstSightInTheAirIsNotAirborne() {
        var state = at(OwnerAirborneDetector.State.initial(OVERWORLD), 64D, STANDING, 0);
        assertFalse(state.airborne());
        assertFalse(at(OwnerAirborneDetector.State.initial(OVERWORLD), 64D, IN_AIR, 0).airborne());
    }

    @Test
    void risingInAJumpIsNotAirborneButFallingIs() {
        var state = at(OwnerAirborneDetector.State.initial(OVERWORLD), 64D, STANDING, 0);
        state = at(state, 64.42D, IN_AIR, 1);
        assertFalse(state.airborne());
        state = at(state, 64.77D, IN_AIR, 2);
        assertFalse(state.airborne());
        state = at(state, 64.7D, IN_AIR, 3);
        assertTrue(state.airborne());
        state = at(state, 64D, STANDING, 4);
        assertFalse(state.airborne());
    }

    @Test
    void swimmingRidingAndClimbingDoNotCountAsFalling() {
        var state = at(OwnerAirborneDetector.State.initial(OVERWORLD), 64D, STANDING, 0);
        assertFalse(at(state, 63.5D, STANDING, 1).airborne());
    }

    @Test
    void aGapInObservationOrADimensionChangeForgetsTheHeight() {
        var state = at(OwnerAirborneDetector.State.initial(OVERWORLD), 100D, IN_AIR, 0);
        assertFalse(at(state, 50D, IN_AIR, 5).airborne());
        assertFalse(OwnerAirborneDetector.observe(state, NETHER, new MovementPoint(0D, 50D, 0D), IN_AIR, 1).airborne());
    }

    @Test
    void theSameTickTwiceChangesNothing() {
        var state = at(OwnerAirborneDetector.State.initial(OVERWORLD), 100D, IN_AIR, 0);
        state = at(state, 99D, IN_AIR, 1);
        assertTrue(state.airborne());
        assertTrue(at(state, 200D, STANDING, 1).airborne());
    }
}
