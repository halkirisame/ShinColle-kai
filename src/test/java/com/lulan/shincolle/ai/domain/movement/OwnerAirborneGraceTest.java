package com.lulan.shincolle.ai.domain.movement;

import com.lulan.shincolle.ai.domain.DimensionKey;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A missing position update is not a landing: falling holds until support, a rise, or the grace runs out. */
class OwnerAirborneGraceTest {
    private static final DimensionKey OVERWORLD = new DimensionKey("minecraft", "overworld");
    private static final DimensionKey NETHER = new DimensionKey("minecraft", "the_nether");
    private static final OwnerAirborneDetector.Facts STANDING = new OwnerAirborneDetector.Facts(false, true);
    private static final OwnerAirborneDetector.Facts IN_AIR = new OwnerAirborneDetector.Facts(false, false);
    private static final OwnerAirborneDetector.Facts GLIDING = new OwnerAirborneDetector.Facts(true, false);
    private static final long GRACE = OwnerAirborneDetector.AIRBORNE_GRACE_TICKS;

    private static OwnerAirborneDetector.State at(OwnerAirborneDetector.State state, double y,
                                                  OwnerAirborneDetector.Facts facts, long now) {
        return OwnerAirborneDetector.observe(state, OVERWORLD, new MovementPoint(0D, y, 0D), facts, now);
    }

    /** Observed descending at tick 101 (Y 105 -> 104). */
    private static OwnerAirborneDetector.State falling() {
        var state = at(OwnerAirborneDetector.State.initial(OVERWORLD), 105D, IN_AIR, 100);
        state = at(state, 104D, IN_AIR, 101);
        assertTrue(state.airborne());
        return state;
    }

    @Test
    void oneTickWithoutAPositionUpdateKeepsFalling() {
        assertTrue(at(falling(), 104D, IN_AIR, 102).airborne());
    }

    @Test
    void fallingIsHeldThroughTheGraceAndReleasedAfterIt() {
        var state = falling();
        for (long now = 102; now < 101 + GRACE; now++) {
            state = at(state, 104D, IN_AIR, now);
            assertTrue(state.airborne(), "held at " + now);
        }
        state = at(state, 104D, IN_AIR, 101 + GRACE);
        assertFalse(state.airborne());
        assertFalse(at(state, 104D, IN_AIR, 102 + GRACE).airborne());
    }

    @Test
    void anotherDescentRestartsTheGrace() {
        var state = falling();
        state = at(state, 104D, IN_AIR, 108);
        state = at(state, 104D, IN_AIR, 109);
        state = at(state, 103D, IN_AIR, 110);
        state = at(state, 103D, IN_AIR, 110 + GRACE - 1);
        assertTrue(state.airborne());
        assertFalse(at(state, 103D, IN_AIR, 110 + GRACE).airborne());
    }

    @Test
    void supportDuringTheGraceReleasesAtOnce() {
        assertFalse(at(falling(), 104D, STANDING, 102).airborne());
    }

    @Test
    void aRiseDuringTheGraceReleasesAtOnce() {
        var state = at(falling(), 104D, IN_AIR, 102);
        assertTrue(state.airborne());
        assertFalse(at(state, 104.4D, IN_AIR, 103).airborne());
    }

    @Test
    void glidingHoldsAtOnceAndFallingResumesTheGraceFromTheLastDescent() {
        var state = at(falling(), 104D, GLIDING, 102);
        assertTrue(state.airborne());
        state = at(state, 104D, IN_AIR, 103);
        assertTrue(state.airborne());
    }

    @Test
    void aDimensionChangeEmptiesTheHold() {
        var state = OwnerAirborneDetector.observe(falling(), NETHER, new MovementPoint(0D, 104D, 0D), IN_AIR, 102);
        assertFalse(state.airborne());
        assertFalse(OwnerAirborneDetector.observe(state, NETHER, new MovementPoint(0D, 104D, 0D), IN_AIR, 103).airborne());
    }

    @Test
    void aLongSilenceDoesNotHoldForever() {
        assertFalse(at(falling(), 104D, IN_AIR, 101 + GRACE + 50).airborne());
    }

    @Test
    void neverSeenDescendingNeverHolds() {
        var state = at(OwnerAirborneDetector.State.initial(OVERWORLD), 104D, IN_AIR, 100);
        assertFalse(at(state, 104D, IN_AIR, 101).airborne());
    }
}
