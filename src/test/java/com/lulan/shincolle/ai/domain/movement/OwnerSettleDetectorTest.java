package com.lulan.shincolle.ai.domain.movement;

import com.lulan.shincolle.ai.domain.DimensionKey;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OwnerSettleDetectorTest {
    private static final DimensionKey OVERWORLD = new DimensionKey("minecraft", "overworld");
    private static final DimensionKey NETHER = new DimensionKey("minecraft", "the_nether");
    private static final OwnerSettleDetector.Facts STANDING = new OwnerSettleDetector.Facts(false, true);

    private static OwnerSettleDetector.State still(int ticks, OwnerSettleDetector.Facts facts) {
        var state = OwnerSettleDetector.State.initial(OVERWORLD);
        for (int tick = 0; tick < ticks; tick++) {
            state = OwnerSettleDetector.observe(state, OVERWORLD, new MovementPoint(0D, 64D, 0D), facts, tick).state();
        }
        return state;
    }

    @Test
    void needsAFullWindowBeforeItSettles() {
        var state = OwnerSettleDetector.State.initial(OVERWORLD);
        for (int tick = 0; tick <= OwnerSettleDetector.WINDOW; tick++) {
            var observed = OwnerSettleDetector.observe(state, OVERWORLD, new MovementPoint(0D, 64D, 0D), STANDING, tick);
            assertEquals(tick == OwnerSettleDetector.WINDOW, observed.settled(), "tick " + tick);
            assertEquals(tick == OwnerSettleDetector.WINDOW, observed.becameSettled(), "tick " + tick);
            state = observed.state();
        }
        var next = OwnerSettleDetector.observe(state, OVERWORLD, new MovementPoint(0D, 64D, 0D), STANDING,
                OwnerSettleDetector.WINDOW + 1);
        assertTrue(next.settled());
        assertFalse(next.becameSettled());
    }

    @Test
    void sixBlocksInTheWindowIsNotSettledAndVerticalMovementDoesNotCount() {
        var state = still(OwnerSettleDetector.WINDOW, STANDING);
        int now = OwnerSettleDetector.WINDOW;
        assertTrue(OwnerSettleDetector.observe(state, OVERWORLD, new MovementPoint(5.9D, 64D, 0D), STANDING, now).settled());
        assertFalse(OwnerSettleDetector.observe(state, OVERWORLD, new MovementPoint(6D, 64D, 0D), STANDING, now).settled());
        assertFalse(OwnerSettleDetector.observe(state, OVERWORLD, new MovementPoint(4D, 64D, 4.5D), STANDING, now).settled());
        assertTrue(OwnerSettleDetector.observe(state, OVERWORLD, new MovementPoint(0D, 200D, 0D), STANDING, now).settled());
    }

    @Test
    void glidingOrFallingIsNeverSettledAndSettlingAgainReportsAgain() {
        var state = still(OwnerSettleDetector.WINDOW, STANDING);
        int now = OwnerSettleDetector.WINDOW;
        var origin = new MovementPoint(0D, 64D, 0D);
        assertFalse(OwnerSettleDetector.observe(state, OVERWORLD, origin, new OwnerSettleDetector.Facts(true, true), now).settled());
        assertFalse(OwnerSettleDetector.observe(state, OVERWORLD, origin, new OwnerSettleDetector.Facts(false, false), now).settled());
        var settled = OwnerSettleDetector.observe(state, OVERWORLD, origin, STANDING, now);
        assertTrue(settled.becameSettled());
        var falling = OwnerSettleDetector.observe(settled.state(), OVERWORLD, origin,
                new OwnerSettleDetector.Facts(false, false), now + 1);
        assertFalse(falling.settled());
        var again = OwnerSettleDetector.observe(falling.state(), OVERWORLD, origin, STANDING, now + 2);
        assertTrue(again.settled());
        assertTrue(again.becameSettled());
    }

    @Test
    void dimensionChangeAndGapsDiscardTheWindow() {
        var state = still(OwnerSettleDetector.WINDOW, STANDING);
        var nether = OwnerSettleDetector.observe(state, NETHER, new MovementPoint(0D, 64D, 0D), STANDING,
                OwnerSettleDetector.WINDOW);
        assertFalse(nether.settled());
        assertEquals(1, nether.state().history().size());
        var gap = OwnerSettleDetector.observe(state, OVERWORLD, new MovementPoint(0D, 64D, 0D), STANDING,
                OwnerSettleDetector.WINDOW + 5);
        assertFalse(gap.settled());
        assertEquals(1, gap.state().history().size());
    }

    @Test
    void duplicateTickChangesNothing() {
        var state = still(OwnerSettleDetector.WINDOW + 1, STANDING);
        var duplicate = OwnerSettleDetector.observe(state, OVERWORLD, new MovementPoint(500D, 64D, 0D), STANDING,
                OwnerSettleDetector.WINDOW);
        assertEquals(state, duplicate.state());
        assertFalse(duplicate.becameSettled());
    }
}
