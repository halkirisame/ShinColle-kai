package com.lulan.shincolle.ai.domain.movement;

import com.lulan.shincolle.ai.domain.DimensionKey;
import com.lulan.shincolle.ai.domain.TargetHandle;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VerticalSeparationTest {
    private static final TargetHandle OWNER = new TargetHandle(new UUID(1L, 2L), new DimensionKey("minecraft", "overworld"));

    private static VerticalSeparation.State separated(double difference) {
        var baseline = VerticalSeparation.State.empty();
        for (int tick = 0; tick < 20; tick++) baseline = VerticalSeparation.observe(baseline, OWNER, 0D, tick).state();
        return VerticalSeparation.observe(baseline, OWNER, difference, 20).state();
    }

    @Test void exactThresholdInBothDirections() {
        assertFalse(VerticalSeparation.canRecall(separated(9.9D), OWNER, 9.9D, 20));
        for (double dy : new double[]{10D, 10.1D, -10D, -10.1D}) {
            assertTrue(VerticalSeparation.canRecall(separated(dy), OWNER, dy, 20));
        }
    }

    @Test void simultaneousFallAndConstantLargeDifferenceDoNotOpenWindow() {
        var state = VerticalSeparation.State.empty();
        for (int tick = 0; tick <= 50; tick++) {
            state = VerticalSeparation.observe(state, OWNER, 12D, tick).state();
        }
        assertFalse(VerticalSeparation.canRecall(state, OWNER, 12D, 50));
    }

    @Test void comparesIncreaseRatherThanTheAbsoluteHeightOrSign() {
        var state = VerticalSeparation.observe(VerticalSeparation.State.empty(), OWNER, -15D, 0).state();
        state = VerticalSeparation.observe(state, OWNER, 15D, 1).state();
        assertFalse(VerticalSeparation.canRecall(state, OWNER, 15D, 1));
        state = VerticalSeparation.observe(state, OWNER, 25D, 2).state();
        assertTrue(VerticalSeparation.canRecall(state, OWNER, 25D, 2));
    }

    @Test void remembersAnyBaselineWithinTwentyTicks() {
        var state = VerticalSeparation.observe(VerticalSeparation.State.empty(), OWNER, 0D, 0).state();
        state = VerticalSeparation.observe(state, OWNER, 10D, 21).state();
        assertFalse(VerticalSeparation.canRecall(state, OWNER, 10D, 21));
        assertTrue(VerticalSeparation.canRecall(separated(10D), OWNER, 10D, 20));
    }

    @Test void windowLastsSixHundredTicksAndRepeatedObservationDoesNotExtendIt() {
        var state = separated(12D);
        for (int tick = 21; tick < 620; tick++) {
            state = VerticalSeparation.observe(state, OWNER, 12D, tick).state();
        }
        assertTrue(VerticalSeparation.canRecall(state, OWNER, 12D, 619));
        assertFalse(VerticalSeparation.canRecall(state, OWNER, 12D, 620));
    }

    @Test void returnClosesWindowAndSuccessConsumesIt() {
        var state = separated(12D);
        assertFalse(VerticalSeparation.canRecall(state, OWNER, 9.9D, 21));
        assertFalse(VerticalSeparation.canRecall(VerticalSeparation.atCommand(state, OWNER, 9.9D, 21), OWNER, 12D, 21));
        state = VerticalSeparation.observe(state, OWNER, 9.9D, 21).state();
        assertFalse(VerticalSeparation.canRecall(state, OWNER, 12D, 22));
        state = VerticalSeparation.consume(separated(12D));
        state = VerticalSeparation.observe(state, OWNER, 12D, 21).state();
        assertFalse(VerticalSeparation.canRecall(state, OWNER, 12D, 21));
    }

    @Test void notificationWaitsSixtyTicksAndOccursOncePerWindow() {
        var state = separated(12D);
        for (int tick = 21; tick < 80; tick++) {
            var observed = VerticalSeparation.observe(state, OWNER, 12D, tick);
            assertFalse(observed.notifyOwner());
            state = observed.state();
        }
        var notified = VerticalSeparation.observe(state, OWNER, 12D, 80);
        assertTrue(notified.notifyOwner());
        assertFalse(VerticalSeparation.observe(notified.state(), OWNER, 12D, 81).notifyOwner());
        assertFalse(VerticalSeparation.observe(state, OWNER, 9D, 80).notifyOwner());
    }

    @Test void differentOwnerOrDimensionAndInterruptedObservationDiscardWindow() {
        var other = new TargetHandle(new UUID(3L, 4L), OWNER.dimension());
        assertFalse(VerticalSeparation.canRecall(separated(12D), other, 12D, 21));
        var dimension = new TargetHandle(OWNER.uuid(), new DimensionKey("minecraft", "the_end"));
        assertFalse(VerticalSeparation.observe(separated(12D), dimension, 12D, 21).state().window().isPresent());
        assertFalse(VerticalSeparation.observe(separated(12D), OWNER, 12D, 41).state().window().isPresent());
    }
}
