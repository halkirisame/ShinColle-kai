package com.lulan.shincolle.ai.domain.movement;

import com.lulan.shincolle.ai.domain.DimensionKey;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** No follow teleport is planned while the owner is gliding or falling; the timers wait for the landing. */
class FollowAirborneHoldTest {
    private static final DimensionKey OVERWORLD = new DimensionKey("minecraft", "overworld");
    private static final MovementPoint SELF = new MovementPoint(0, 64, 0);
    private static final MovementPoint OWNER = new MovementPoint(100, 104, 0);
    private static final FollowRange RANGE = new FollowRange(4, 16);
    private static final TeleportRule RULE = new TeleportRule(true, 200, 256);

    private static FollowMovePlanner.Facts facts(boolean airborne, StuckState stuck) {
        return new FollowMovePlanner.Facts(SELF.distanceSq(OWNER), RANGE, Optional.of(OWNER), OVERWORLD, RULE,
                stuck, SELF.distanceSq(OWNER), Double.POSITIVE_INFINITY, airborne);
    }

    private static MovementState.Follow following(int farTimer, int timeTimer) {
        return new MovementState.Follow(FollowMovePlanner.REPATH, timeTimer, farTimer, 0, OWNER, OWNER,
                FollowDestination.OWNER);
    }

    private static long teleports(PlannedMove<MovementState.Follow> planned) {
        return planned.plan().steps().stream().filter(step -> step instanceof MovementStep.Teleport).count();
    }

    @Test
    void anAlreadyRunningFarTimerDoesNotTeleportIntoTheAirAndDoesOnceTheOwnerHasLanded() {
        var state = following(150, 0);
        long teleports = 0;
        for (int n = 0; n < 60; n++) {
            var planned = FollowMovePlanner.move(FollowMovePlanner.count(state, false), facts(true, StuckState.NONE));
            state = planned.state();
            teleports += teleports(planned);
        }
        assertEquals(0, teleports, "gliding owner, far timer past the cooldown");
        assertTrue(state.farTimer() > RULE.cooldown(), "the timer is held, not reset: " + state.farTimer());

        var landed = FollowMovePlanner.move(FollowMovePlanner.count(state, false), facts(false, StuckState.NONE));
        assertEquals(1, teleports(landed));
        assertEquals(MovementReason.TELEPORT_FAR, landed.plan().steps().stream()
                .filter(step -> step instanceof MovementStep.Teleport)
                .map(step -> ((MovementStep.Teleport) step).reason()).findFirst().orElseThrow());
        assertEquals(0, landed.state().farTimer());
    }

    @Test
    void theOrdinaryFarTimerStillTeleportsWhenTheOwnerIsNotAirborne() {
        var state = following(0, 0);
        long teleports = 0;
        for (int n = 0; n < 201; n++) {
            var planned = FollowMovePlanner.move(FollowMovePlanner.count(state, false), facts(false, StuckState.NONE));
            state = planned.state();
            teleports += teleports(planned);
        }
        assertEquals(1, teleports);
    }

    @Test
    void aTimerRaisedByARecallRequestIsHeldWhileTheOwnerIsAirborne() {
        var state = FollowMovePlanner.afterOwnerJump(following(0, 0), SELF.distanceSq(OWNER), SELF.distanceSq(OWNER),
                RULE, 0L);
        assertEquals(RULE.cooldown(), state.farTimer());
        var held = FollowMovePlanner.move(FollowMovePlanner.count(state, false), facts(true, StuckState.NONE));
        assertEquals(0, teleports(held));
        var landed = FollowMovePlanner.move(FollowMovePlanner.count(held.state(), false), facts(false, StuckState.NONE));
        assertEquals(1, teleports(landed));
    }

    @Test
    void theStuckTimeTeleportIsHeldAndFiresAfterLanding() {
        var state = following(0, RULE.cooldown() + 1);
        // Near enough that the far timer stays out of it, so only the stuck-time path is under test.
        var near = new FollowMovePlanner.Facts(100D, RANGE, Optional.of(OWNER), OVERWORLD, RULE, StuckState.NONE,
                100D, Double.POSITIVE_INFINITY, true);
        var held = FollowMovePlanner.move(state, near);
        assertEquals(0, teleports(held));
        assertTrue(held.state().timeTimer() > RULE.cooldown(), "the stuck time is held: " + held.state().timeTimer());
        var landed = FollowMovePlanner.move(held.state(), new FollowMovePlanner.Facts(100D, RANGE, Optional.of(OWNER),
                OVERWORLD, RULE, StuckState.NONE, 100D, Double.POSITIVE_INFINITY, false));
        assertEquals(1, teleports(landed));
        assertEquals(0, landed.state().timeTimer());
    }

    @Test
    void anAirborneOwnerDoesNotStopPathingOrStopping() {
        var state = following(0, 0);
        var near = new FollowMovePlanner.Facts(1D, RANGE, Optional.of(OWNER), OVERWORLD, RULE, StuckState.NONE,
                1D, Double.POSITIVE_INFINITY, true);
        var planned = FollowMovePlanner.move(FollowMovePlanner.count(state, false), near);
        assertTrue(planned.plan().steps().stream().anyMatch(step -> step instanceof MovementStep.Stop),
                "arrival still stops the ship");
    }
}
