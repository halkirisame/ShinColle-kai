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
    void horizontalThirtyIsInclusiveAndVerticalMovementDoesNotCount() {
        for (double distance : new double[]{29.9D, 30D, 30.1D}) {
            var observed = OwnerJumpDetector.observe(history(), OVERWORLD, new MovementPoint(distance, 64D, 0D), 20);
            assertEquals(distance >= 30D, observed.departedFrom().isPresent());
        }
        assertFalse(OwnerJumpDetector.observe(history(), OVERWORLD, new MovementPoint(0D, 164D, 0D), 20)
                .departedFrom().isPresent());
        assertTrue(OwnerJumpDetector.observe(history(), OVERWORLD, new MovementPoint(18D, 64D, 24D), 20)
                .departedFrom().isPresent());
    }

    @Test
    void comparesEveryTickAgainstExactlyTwentyTicksAgo() {
        var state = OwnerJumpDetector.State.initial(OVERWORLD);
        for (int tick = 0; tick < 25; tick++) {
            var observed = OwnerJumpDetector.observe(state, OVERWORLD, new MovementPoint(tick, 64D, 0D), tick);
            assertFalse(observed.departedFrom().isPresent());
            state = observed.state();
        }
        var jumped = OwnerJumpDetector.observe(state, OVERWORLD, new MovementPoint(35D, 64D, 0D), 25);
        assertEquals(Optional.of(new MovementPoint(5D, 64D, 0D)), jumped.departedFrom());
        assertEquals(20, jumped.state().history().size());
    }

    @Test
    void fastContinuousTravelIsAllowedButOnlyOncePerFourHundredTicks() {
        var state = OwnerJumpDetector.State.initial(OVERWORLD);
        for (int tick = 0; tick <= 420; tick++) {
            var observed = OwnerJumpDetector.observe(state, OVERWORLD, new MovementPoint(tick * 2D, 64D, 0D), tick);
            assertEquals(tick == 20 || tick == 420, observed.departedFrom().isPresent(), "tick " + tick);
            state = observed.state();
        }
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
