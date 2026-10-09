package com.lulan.shincolle.ai.domain.movement;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VoidRescueTest {
    private static VoidRescue.Facts fall(double y, boolean ownerGrounded) {
        return new VoidRescue.Facts(true, y, -1D, false, false, false, false, false, true, ownerGrounded);
    }

    @Test void onlyFortiethConsecutiveFallingTickIsDue() {
        var state = VoidRescue.State.empty();
        for (int tick = 1; tick <= 40; tick++) {
            var observed = VoidRescue.observe(state, fall(50D, true), tick);
            assertEquals(tick == 40, observed.rescue());
            state = observed.state();
        }
    }

    @Test void negativeHeightTriggersEarlyButZeroDoesNot() {
        assertTrue(VoidRescue.observe(VoidRescue.State.empty(), fall(-0.01D, true), 1).rescue());
        assertFalse(VoidRescue.observe(VoidRescue.State.empty(), fall(0D, true), 1).rescue());
    }

    @Test void exclusionsResetTheCountEvenAtNegativeHeight() {
        var state = VoidRescue.State.empty();
        for (int tick = 1; tick < 40; tick++) state = VoidRescue.observe(state, fall(50D, true), tick).state();
        for (int condition = 0; condition < 7; condition++) {
            var facts = new VoidRescue.Facts(condition != 0, -1D, condition == 1 ? 0D : -1D,
                    condition == 2, condition == 3, condition == 4, condition == 5, condition == 6, true, true);
            var reset = VoidRescue.observe(state, facts, 40);
            assertEquals(0, reset.state().fallingTicks());
            assertFalse(reset.rescue());
            assertFalse(VoidRescue.observe(reset.state(), fall(50D, true), 41).rescue());
        }
    }

    @Test void airborneOwnerAndFailedLandingRetryEveryTwentyServerTicks() {
        var waiting = VoidRescue.observe(VoidRescue.State.empty(), fall(-1D, false), 1);
        assertFalse(waiting.rescue());
        var state = waiting.state();
        for (int tick = 2; tick < 21; tick++) {
            var observed = VoidRescue.observe(state, fall(-1D, true), tick);
            assertFalse(observed.rescue());
            state = observed.state();
        }
        var retry = VoidRescue.observe(state, fall(-1D, true), 21);
        assertTrue(retry.rescue());
        assertFalse(VoidRescue.observe(retry.state(), fall(-1D, true), 22).rescue());
        assertTrue(VoidRescue.observe(retry.state(), fall(-1D, true), 41).rescue());
    }

    @Test void absentOwnerNeverRescuesAndObservationGapRestartsTheFallCount() {
        var facts = new VoidRescue.Facts(true, -1D, -1D, false, false, false, false, false, false, false);
        assertFalse(VoidRescue.observe(VoidRescue.State.empty(), facts, 1).rescue());
        var state = VoidRescue.State.empty();
        for (int tick = 1; tick < 40; tick++) state = VoidRescue.observe(state, fall(50D, true), tick).state();
        assertEquals(1, VoidRescue.observe(state, fall(50D, true), 41).state().fallingTicks());
    }

    @Test void emergencyBypassesOnlyCooldown() {
        var facts = new TeleportSafety.Facts(true, 0, true, true, true);
        assertFalse(TeleportSafety.denials(facts).isEmpty());
        assertTrue(TeleportSafety.denials(facts, MovementReason.VOID_RESCUE).isEmpty());
        var unsafe = new TeleportSafety.Facts(false, 0, false, false, false);
        assertEquals(java.util.Set.of(TeleportDenial.OTHER_DIMENSION, TeleportDenial.CHUNK_NOT_LOADED,
                TeleportDenial.OUTSIDE_WORLD_BORDER, TeleportDenial.NO_FREE_SPACE),
                TeleportSafety.denials(unsafe, MovementReason.VOID_RESCUE));
        assertFalse(TeleportSafety.denials(facts, MovementReason.VERTICAL_RECALL).isEmpty());
    }
}
