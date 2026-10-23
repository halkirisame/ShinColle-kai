package com.lulan.shincolle.ai.domain.movement;

import com.lulan.shincolle.ai.domain.DimensionKey;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OwnerJumpDetectorTest {
    private static final DimensionKey OVERWORLD = new DimensionKey("minecraft", "overworld");
    private static final DimensionKey NETHER = new DimensionKey("minecraft", "the_nether");
    private static final MovementPoint HOME = new MovementPoint(0D, 64D, 0D);

    private static OwnerJumpDetector.State history() {
        var state = OwnerJumpDetector.State.initial(OVERWORLD);
        for (int tick = 0; tick < 20; tick++) {
            state = OwnerJumpDetector.observe(state, OVERWORLD, HOME, tick).state();
        }
        return state;
    }

    @Test
    void horizontalTwelveIsInclusiveAndVerticalMovementDoesNotCount() {
        for (double distance : new double[]{11.9D, 12D, 12.1D, 20D, 29.9D}) {
            var observed = OwnerJumpDetector.observe(history(), OVERWORLD, new MovementPoint(distance, 64D, 0D), 20);
            assertEquals(distance >= 12D, observed.departedFrom().isPresent());
        }
        assertFalse(OwnerJumpDetector.observe(history(), OVERWORLD, new MovementPoint(0D, 164D, 0D), 20)
                .departedFrom().isPresent());
        assertTrue(OwnerJumpDetector.observe(history(), OVERWORLD, new MovementPoint(7.2D, 64D, 9.6D), 20)
                .departedFrom().isPresent());
    }

    @Test
    void comparesEveryTickAgainstExactlyTwentyTicksAgo() {
        var state = OwnerJumpDetector.State.initial(OVERWORLD);
        for (int tick = 0; tick < 25; tick++) {
            var observed = OwnerJumpDetector.observe(state, OVERWORLD, new MovementPoint(tick * 0.5D, 64D, 0D), tick);
            assertFalse(observed.departedFrom().isPresent());
            state = observed.state();
        }
        var jumped = OwnerJumpDetector.observe(state, OVERWORLD, new MovementPoint(20D, 64D, 0D), 25);
        assertEquals(Optional.of(new MovementPoint(2.5D, 64D, 0D)), jumped.departedFrom());
        assertEquals(1, jumped.state().history().size());
    }

    @Test
    void fastContinuousTravelIsDetectedAtMostOncePerTwentyTicks() {
        var state = OwnerJumpDetector.State.initial(OVERWORLD);
        for (int tick = 0; tick <= 420; tick++) {
            var observed = OwnerJumpDetector.observe(state, OVERWORLD, new MovementPoint(tick * 2D, 64D, 0D), tick);
            assertEquals(tick >= 20 && tick % 20 == 0, observed.departedFrom().isPresent(), "tick " + tick);
            state = observed.state();
        }
    }

    @Test
    void aSecondJumpAfterTwentyOrMoreTicksIsDetectedWithoutWaitingForTheHoldToEnd() {
        for (int gap : new int[]{20, 21, 100, 399, 400, 401}) {
            var state = history();
            var first = OwnerJumpDetector.observe(state, OVERWORLD, new MovementPoint(300D, 64D, 0D), 20);
            assertTrue(first.departedFrom().isPresent(), "first jump, gap " + gap);
            state = first.state();
            for (int tick = 21; tick < 20 + gap; tick++) {
                var still = OwnerJumpDetector.observe(state, OVERWORLD, new MovementPoint(300D, 64D, 0D), tick);
                assertFalse(still.departedFrom().isPresent(), "gap " + gap + ", tick " + tick);
                state = still.state();
            }
            var second = OwnerJumpDetector.observe(state, OVERWORLD, new MovementPoint(600D, 64D, 0D), 20 + gap);
            assertEquals(Optional.of(new MovementPoint(300D, 64D, 0D)), second.departedFrom(), "second jump, gap " + gap);
        }
    }

    @Test
    void aSecondJumpBeforeTheWindowHasFilledAgainIsNotDetected() {
        var first = OwnerJumpDetector.observe(history(), OVERWORLD, new MovementPoint(300D, 64D, 0D), 20);
        var state = first.state();
        for (int tick = 21; tick < 39; tick++) {
            state = OwnerJumpDetector.observe(state, OVERWORLD, new MovementPoint(300D, 64D, 0D), tick).state();
        }
        assertFalse(OwnerJumpDetector.observe(state, OVERWORLD, new MovementPoint(600D, 64D, 0D), 39)
                .departedFrom().isPresent());
    }

    @Test
    void theSameJumpIsNotDetectedTwiceWhileThePreJumpPositionsLeaveTheWindow() {
        var first = OwnerJumpDetector.observe(history(), OVERWORLD, new MovementPoint(300D, 64D, 0D), 20);
        assertTrue(first.departedFrom().isPresent());
        assertEquals(1, first.state().history().size());
        var state = first.state();
        // Standing at the destination through the whole window and well past it.
        for (int tick = 21; tick <= 80; tick++) {
            var observed = OwnerJumpDetector.observe(state, OVERWORLD, new MovementPoint(300D, 64D, 0D), tick);
            assertFalse(observed.departedFrom().isPresent(), "tick " + tick);
            state = observed.state();
        }
    }

    @Test
    void everyDetectionWithinTheHoldStaysRecognisedAfterALaterOne() {
        var state = history();
        var first = OwnerJumpDetector.observe(state, OVERWORLD, new MovementPoint(300D, 64D, 0D), 20);
        state = first.state();
        assertTrue(state.detectedAt(20));
        for (int tick = 21; tick < 100; tick++) {
            state = OwnerJumpDetector.observe(state, OVERWORLD, new MovementPoint(300D, 64D, 0D), tick).state();
        }
        var second = OwnerJumpDetector.observe(state, OVERWORLD, new MovementPoint(600D, 64D, 0D), 100);
        assertTrue(second.departedFrom().isPresent());
        state = second.state();
        assertTrue(state.detectedAt(20));
        assertTrue(state.detectedAt(100));
        assertFalse(state.detectedAt(21));
        // The first one leaves the list once it has been held for the full four hundred ticks.
        for (int tick = 101; tick <= 420; tick++) {
            state = OwnerJumpDetector.observe(state, OVERWORLD, new MovementPoint(600D, 64D, 0D), tick).state();
        }
        assertFalse(state.detectedAt(20));
        assertTrue(state.detectedAt(100));
        state = OwnerJumpDetector.observe(state, OVERWORLD, new MovementPoint(600D, 64D, 0D), 500).state();
        assertFalse(state.detectedAt(100));
    }

    @Test
    void dimensionChangeAndGapsDiscardTheOldWindow() {
        var nether = OwnerJumpDetector.observe(history(), NETHER, new MovementPoint(500D, 64D, 0D), 20);
        assertFalse(nether.departedFrom().isPresent());
        assertEquals(1, nether.state().history().size());
        assertFalse(OwnerJumpDetector.observe(history(), OVERWORLD, new MovementPoint(500D, 64D, 0D), 30)
                .departedFrom().isPresent());
    }

    @Test
    void duplicateTickCannotRefreshHistoryOrFireAgain() {
        var first = OwnerJumpDetector.observe(history(), OVERWORLD, new MovementPoint(300D, 64D, 0D), 20);
        var duplicate = OwnerJumpDetector.observe(first.state(), OVERWORLD, HOME, 20);
        assertEquals(first.state(), duplicate.state());
        assertFalse(duplicate.departedFrom().isPresent());
    }
}
