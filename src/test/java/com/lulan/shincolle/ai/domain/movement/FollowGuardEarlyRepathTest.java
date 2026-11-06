package com.lulan.shincolle.ai.domain.movement;

import com.lulan.shincolle.ai.domain.DimensionKey;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FollowGuardEarlyRepathTest {
    private static final DimensionKey OVERWORLD = new DimensionKey("minecraft", "overworld");
    private static final MovementPoint DESTINATION = new MovementPoint(10D, 64D, 0D);
    private static final FollowRange RANGE = new FollowRange(4D, 9D);
    private static final TeleportRule NO_TELEPORT = new TeleportRule(false, 200, 256);

    private static MovementPoint shifted(double blocks) {
        return new MovementPoint(DESTINATION.x() + blocks, DESTINATION.y(), DESTINATION.z());
    }

    private static MovementState.Follow follow() {
        return FollowMovePlanner.start(FollowMovePlanner.initial(DESTINATION), 100);
    }

    private static MovementState.Guard guard() {
        return GuardMovePlanner.start(GuardMovePlanner.toEntity(GuardMovePlanner.initial(), DESTINATION), 100);
    }

    private static PlannedMove<MovementState.Follow> followMove(MovementState.Follow state) {
        return FollowMovePlanner.move(state, new FollowMovePlanner.Facts(100D, RANGE,
                Optional.of(state.destination()), OVERWORLD, NO_TELEPORT, StuckState.NONE));
    }

    private static PlannedMove<MovementState.Guard> guardMove(MovementState.Guard state) {
        return guardMove(state, true, StuckState.NONE);
    }

    private static PlannedMove<MovementState.Guard> guardMove(MovementState.Guard state, boolean guardEntity,
                                                             StuckState stuck) {
        return GuardMovePlanner.move(state, new GuardMovePlanner.Facts(100D, RANGE, false, OVERWORLD,
                NO_TELEPORT, stuck, guardEntity));
    }

    private static MovementStep.PathTo path(MovementPoint destination, MovementReason reason) {
        return new MovementStep.PathTo(MovementBody.SELF, new MovementTarget.Point(destination), 1D, reason);
    }

    @Test
    void travellingFollowerResolvesOwnerAfterEightEntityTicks() {
        assertTrue(FollowMovePlanner.travelling(100D, RANGE));
        MovementState.Follow resolved = FollowMovePlanner.ownerResolved(follow(), 100, 100D, RANGE);
        assertFalse(FollowMovePlanner.ownerResolveDue(resolved, 107));
        assertTrue(FollowMovePlanner.ownerResolveDue(resolved, 108));
    }

    @Test
    void stationaryFollowerStillResolvesOwnerAfterThirtyTwoEntityTicks() {
        assertFalse(FollowMovePlanner.travelling(4D, RANGE));
        MovementState.Follow resolved = FollowMovePlanner.ownerResolved(follow(), 100, 4D, RANGE);
        assertFalse(FollowMovePlanner.ownerResolveDue(resolved, 131));
        assertTrue(FollowMovePlanner.ownerResolveDue(resolved, 132));
    }

    @Test
    void followerRepathsImmediatelyWhenOwnerMovesThreeBlocks() {
        MovementState.Follow changed = FollowMovePlanner.toOwner(follow(), shifted(3D));
        assertTrue(changed.repathIn() > 0);
        PlannedMove<MovementState.Follow> planned = followMove(changed);
        assertEquals(List.of(path(shifted(3D), MovementReason.FOLLOW_OWNER)), planned.plan().steps());
        assertEquals(32, planned.state().repathIn());
        assertEquals(shifted(3D), planned.state().pathedDestination());
        assertTrue(followMove(FollowMovePlanner.count(planned.state(), false)).plan().isEmpty());
    }

    @Test
    void followerWaitsWhenOwnerMovesOnlyOneAndAHalfBlocks() {
        MovementState.Follow changed = FollowMovePlanner.toOwner(follow(), shifted(1.5D));
        PlannedMove<MovementState.Follow> planned = followMove(changed);
        assertTrue(planned.plan().isEmpty());
        assertEquals(changed.repathIn(), planned.state().repathIn());
        assertEquals(DESTINATION, planned.state().pathedDestination());
    }

    @Test
    void guardRepathsImmediatelyWhenGuardedEntityMovesThreeBlocks() {
        MovementState.Guard changed = GuardMovePlanner.toEntity(guard(), shifted(3D));
        assertTrue(changed.repathIn() > 0);
        PlannedMove<MovementState.Guard> planned = guardMove(changed);
        assertEquals(List.of(path(shifted(3D), MovementReason.GUARD)), planned.plan().steps());
        assertEquals(32, planned.state().repathIn());
        assertEquals(shifted(3D), planned.state().pathedDestination());
        assertTrue(guardMove(GuardMovePlanner.count(planned.state(), false)).plan().isEmpty());
    }

    @Test
    void pointGuardDoesNotRepathBeforeItsTimer() {
        MovementState.Guard changed = GuardMovePlanner.toPoint(guard(), DESTINATION);
        PlannedMove<MovementState.Guard> planned = guardMove(changed, false, StuckState.NONE);
        assertTrue(planned.plan().isEmpty());
        assertEquals(changed.repathIn(), planned.state().repathIn());
    }

    @Test
    void waypointChangeDoesNotAddAnEarlyPathToTheCommandsPath() {
        MovementState.Guard changed = GuardMovePlanner.toPoint(guard(), shifted(30D));
        PlannedMove<MovementState.Guard> planned = guardMove(changed, false, StuckState.NONE);
        assertEquals(shifted(30D), planned.state().destination());
        assertEquals(DESTINATION, planned.state().pathedDestination());
        assertTrue(planned.plan().isEmpty());
        assertEquals(changed.repathIn(), planned.state().repathIn());
    }

    @Test
    void earlyRepathIncludesExactlyTwoBlocksButNotJustBelow() {
        for (double delta : new double[] {1.999D, 2D}) {
            PlannedMove<MovementState.Follow> followed = followMove(FollowMovePlanner.toOwner(follow(), shifted(delta)));
            PlannedMove<MovementState.Guard> guarded = guardMove(GuardMovePlanner.toEntity(guard(), shifted(delta)));
            assertEquals(delta == 2D, followed.plan().hasPath(), "follow " + delta);
            assertEquals(delta == 2D, guarded.plan().hasPath(), "guard " + delta);
        }
    }

    @Test
    void smallDestinationUpdatesAccumulateAgainstTheLastPathNotTheLastObservation() {
        MovementState.Follow followed = followMove(FollowMovePlanner.toOwner(follow(), shifted(1.5D))).state();
        MovementState.Guard guarded = guardMove(GuardMovePlanner.toEntity(guard(), shifted(1.5D))).state();
        assertEquals(DESTINATION, followed.pathedDestination());
        assertEquals(DESTINATION, guarded.pathedDestination());
        assertEquals(List.of(path(shifted(3D), MovementReason.FOLLOW_OWNER)),
                followMove(FollowMovePlanner.toOwner(followed, shifted(3D))).plan().steps());
        assertEquals(List.of(path(shifted(3D), MovementReason.GUARD)),
                guardMove(GuardMovePlanner.toEntity(guarded, shifted(3D))).plan().steps());
    }

    @Test
    void formationRecalculationAlsoUsesTheLastPathedDestination() {
        MovementPoint owner = shifted(4D);
        assertTrue(FollowMovePlanner.formationStale(follow(), owner));
        assertTrue(GuardMovePlanner.formationStale(guard(), owner));
        MovementState.Follow followed = FollowMovePlanner.formationPlace(follow(), shifted(3D), owner,
                FormationSlot.FLAGSHIP);
        MovementState.Guard guarded = GuardMovePlanner.formationPlace(guard(), shifted(3D), owner);
        assertEquals(List.of(path(shifted(3D), MovementReason.FOLLOW_OWNER)), followMove(followed).plan().steps());
        assertEquals(List.of(path(shifted(3D), MovementReason.GUARD)), guardMove(guarded).plan().steps());
    }

    @Test
    void ordinaryTimedPathsRememberTheNewDestinationEvenBelowTheThreshold() {
        MovementState.Follow followed = FollowMovePlanner.toOwner(follow(), shifted(1.5D));
        MovementState.Guard guarded = GuardMovePlanner.toEntity(guard(), shifted(1.5D));
        for (int tick = 0; tick < 10; tick++) {
            followed = FollowMovePlanner.count(followed, false);
            guarded = GuardMovePlanner.count(guarded, false);
        }
        PlannedMove<MovementState.Follow> followPath = followMove(followed);
        PlannedMove<MovementState.Guard> guardPath = guardMove(guarded);
        assertEquals(List.of(path(shifted(1.5D), MovementReason.FOLLOW_OWNER)), followPath.plan().steps());
        assertEquals(List.of(path(shifted(1.5D), MovementReason.GUARD)), guardPath.plan().steps());
        assertEquals(shifted(1.5D), followPath.state().pathedDestination());
        assertEquals(shifted(1.5D), guardPath.state().pathedDestination());
        assertTrue(followMove(FollowMovePlanner.toOwner(followPath.state(), shifted(3D))).plan().isEmpty());
        assertTrue(guardMove(GuardMovePlanner.toEntity(guardPath.state(), shifted(3D))).plan().isEmpty());
    }

    @Test
    void recoveryWinsOverEarlyRepathAndRemembersDestinationRatherThanDetourPoint() {
        for (int windows : new int[] {1, 2}) {
            StuckState stuck = new StuckState(true, DESTINATION, 100, windows, true);
            MovementState.Follow followed = FollowMovePlanner.toOwner(follow(), shifted(3D));
            MovementState.Guard guarded = GuardMovePlanner.toEntity(guard(), shifted(3D));
            PlannedMove<MovementState.Follow> followPath = FollowMovePlanner.move(followed,
                    new FollowMovePlanner.Facts(100D, RANGE, Optional.of(shifted(3D)), OVERWORLD, NO_TELEPORT, stuck));
            PlannedMove<MovementState.Guard> guardPath = guardMove(guarded, true, stuck);
            MovementTarget target = windows == 1 ? new MovementTarget.Point(shifted(3D))
                    : new MovementTarget.Around(shifted(3D));
            MovementReason reason = windows == 1 ? MovementReason.STUCK_REPATH : MovementReason.STUCK_DETOUR;
            List<MovementStep> expected = List.of(new MovementStep.PathTo(MovementBody.SELF, target, 1D, reason));
            assertEquals(expected, followPath.plan().steps());
            assertEquals(expected, guardPath.plan().steps());
            assertEquals(32, followPath.state().repathIn());
            assertEquals(32, guardPath.state().repathIn());
            assertEquals(shifted(3D), followPath.state().pathedDestination());
            assertEquals(shifted(3D), guardPath.state().pathedDestination());
            assertTrue(followMove(followPath.state()).plan().isEmpty());
            assertTrue(guardMove(guardPath.state()).plan().isEmpty());
        }
    }

    @Test
    void nonPathUpdatesPreserveMemoryAndStartResetsItToCurrentDestination() {
        MovementState.Follow followed = FollowMovePlanner.toOwner(follow(), shifted(1D));
        MovementState.Guard guarded = GuardMovePlanner.toEntity(guard(), shifted(1D));
        assertEquals(DESTINATION, FollowMovePlanner.count(followed, true).pathedDestination());
        assertEquals(DESTINATION, FollowMovePlanner.ownerResolved(followed, 110, 100D, RANGE).pathedDestination());
        assertEquals(DESTINATION, FollowMovePlanner.afterOwnerJump(followed, 300D, 300D,
                new TeleportRule(true, 200, 256), 0L).pathedDestination());
        assertEquals(DESTINATION, GuardMovePlanner.count(guarded, true).pathedDestination());
        assertEquals(DESTINATION, GuardMovePlanner.anchorResolved(guarded, 110).pathedDestination());
        assertEquals(DESTINATION, GuardMovePlanner.stopped(guarded).pathedDestination());
        for (PathOutcome outcome : PathOutcome.values()) {
            assertEquals(DESTINATION, GuardMovePlanner.pathed(guarded, outcome).pathedDestination());
        }
        assertEquals(shifted(1D), FollowMovePlanner.start(followed, 120).pathedDestination());
        assertEquals(shifted(1D), GuardMovePlanner.start(guarded, 120).pathedDestination());
        assertEquals(DESTINATION, FollowMovePlanner.initial(DESTINATION).pathedDestination());
        assertEquals(GuardMovePlanner.initial().destination(), GuardMovePlanner.initial().pathedDestination());
    }

    @Test
    void guardAnchorResolveIntervalRemainsEightEntityTicks() {
        MovementState.Guard resolved = GuardMovePlanner.anchorResolved(guard(), 100);
        assertFalse(GuardMovePlanner.anchorResolveDue(resolved, 107));
        assertTrue(GuardMovePlanner.anchorResolveDue(resolved, 108));
    }
}
