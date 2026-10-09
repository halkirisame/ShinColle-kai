package com.lulan.shincolle.ai.domain.movement;

import com.lulan.shincolle.ai.domain.DimensionKey;
import com.lulan.shincolle.ai.domain.TargetHandle;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StuckRecoveryTest {
    private static final DimensionKey OVERWORLD = new DimensionKey("minecraft", "overworld");
    private static final TargetHandle ITEM = new TargetHandle(new UUID(0L, 2L), OVERWORLD);
    private static final MovementPoint HOME = new MovementPoint(0D, 64D, 0D);
    private static final MovementPoint AWAY = new MovementPoint(10D, 64D, 0D);
    private static final TeleportRule RULE = new TeleportRule(true, 200, 256);
    private static final FollowRange RANGE = new FollowRange(4D, 9D);

    /** A state observed as stuck for {@code windows} windows in a row, the last just closing. */
    private static StuckState stuck(int windows) {
        return new StuckState(true, HOME, 0, windows, true);
    }

    private static MovementPoint at(double x) {
        return new MovementPoint(x, 64D, 0D);
    }

    // ---------- detector ----------

    @Test
    void detectorMeasuresOnlyWhileOnTheWay() {
        assertEquals(StuckState.NONE, StuckDetector.observe(stuck(2), false, HOME, 100));
        StuckState opened = StuckDetector.observe(StuckState.NONE, true, HOME, 100);
        assertEquals(new StuckState(true, HOME, 100, 0, false), opened);
        // inside a window nothing changes
        assertEquals(opened, StuckDetector.observe(opened, true, at(0.1D), 139));
    }

    @Test
    void detectorClosesAWindowEveryFortyTicks() {
        StuckState opened = StuckDetector.observe(StuckState.NONE, true, HOME, 100);
        StuckState moved = StuckDetector.observe(opened, true, at(1D), 140);
        assertEquals(new StuckState(true, at(1D), 140, 0, false), moved);
        StuckState stuck = StuckDetector.observe(opened, true, at(0.99D), 140);
        assertEquals(new StuckState(true, at(0.99D), 140, 1, true), stuck);
        assertEquals(StuckStage.REPATH, stuck.due());
        // the stage is due on the closing observation only
        StuckState next = StuckDetector.observe(stuck, true, at(0.99D), 142);
        assertFalse(next.fresh());
        assertEquals(StuckStage.NONE, next.due());
        assertTrue(next.stuck());
        // getting anywhere in a later window starts over
        assertEquals(0, StuckDetector.observe(next, true, at(2D), 180).stuckWindows());
    }

    @Test
    void stagesGoRepathDetourGiveUp() {
        assertEquals(StuckStage.REPATH, stuck(1).due());
        assertEquals(StuckStage.DETOUR, stuck(2).due());
        assertEquals(StuckStage.GIVE_UP, stuck(3).due());
        assertEquals(StuckStage.GIVE_UP, stuck(4).due());
        assertFalse(stuck(2).gaveUp());
        assertTrue(stuck(3).gaveUp());
        assertEquals(stuck(5), StuckDetector.retryAfterGivingUp(stuck(5)));
        assertEquals(StuckState.NONE, StuckDetector.retryAfterGivingUp(stuck(6)));
    }

    @Test
    void recoveryRepathsThenDetours() {
        MovementTarget target = new MovementTarget.Point(AWAY);
        assertEquals(Optional.of(new MovementStep.PathTo(MovementBody.SELF, target, 1D, MovementReason.STUCK_REPATH)),
                StuckDetector.recovery(stuck(1), MovementBody.SELF, target, AWAY, 1D, Optional.empty()));
        assertEquals(Optional.of(new MovementStep.PathTo(MovementBody.SELF, new MovementTarget.Around(AWAY), 1D,
                        MovementReason.STUCK_DETOUR)),
                StuckDetector.recovery(stuck(2), MovementBody.SELF, target, AWAY, 1D, Optional.empty()));
        assertEquals(Optional.empty(), StuckDetector.recovery(stuck(3), MovementBody.SELF, target, AWAY, 1D,
                Optional.empty()));
        assertEquals(Optional.empty(), StuckDetector.recovery(new StuckState(true, HOME, 0, 1, false),
                MovementBody.SELF, target, AWAY, 1D, Optional.empty()));
    }

    @Test
    void detourPointsGoInAFixedOrder() {
        List<MovementPoint> points = StuckDetector.detourPoints(HOME);
        assertEquals(16, points.size());
        assertEquals(new MovementPoint(2D, 64D, 0D), points.get(0));
        assertEquals(new MovementPoint(0D, 64D, 2D), points.get(1));
        assertEquals(new MovementPoint(2D, 64D, -2D), points.get(7));
        assertEquals(new MovementPoint(3D, 64D, 0D), points.get(8));
        assertEquals(points, StuckDetector.detourPoints(HOME));
    }

    // ---------- follow and guard ----------

    @Test
    void stuckFollowerRecoversInsteadOfItsRegularRepath() {
        MovementState.Follow due = new MovementState.Follow(0, 3, 0, 0, AWAY, HOME);
        PlannedMove<MovementState.Follow> repath = FollowMovePlanner.move(due, new FollowMovePlanner.Facts(50D,
                RANGE, Optional.of(AWAY), OVERWORLD, RULE, stuck(1)));
        assertEquals(List.of(new MovementStep.PathTo(MovementBody.SELF, new MovementTarget.Point(AWAY), 1D,
                MovementReason.STUCK_REPATH)), repath.plan().steps());
        assertEquals(32, repath.state().repathIn());
        PlannedMove<MovementState.Follow> detour = FollowMovePlanner.move(due, new FollowMovePlanner.Facts(50D,
                RANGE, Optional.of(AWAY), OVERWORLD, RULE, stuck(2)));
        assertEquals(List.of(new MovementStep.PathTo(MovementBody.SELF, new MovementTarget.Around(AWAY), 1D,
                MovementReason.STUCK_DETOUR)), detour.plan().steps());
        assertTrue(FollowMovePlanner.travelling(4.01D, RANGE));
        assertFalse(FollowMovePlanner.travelling(4D, RANGE));
    }

    @Test
    void stuckGuardRecoversAndAMoveOrderIsOnItsWayUntilItArrives() {
        MovementState.Guard state = new MovementState.Guard(5, 0, 0, 0, AWAY, HOME, true);
        PlannedMove<MovementState.Guard> detour = GuardMovePlanner.move(state, new GuardMovePlanner.Facts(50D, RANGE,
                false, OVERWORLD, RULE, stuck(2)));
        assertEquals(List.of(new MovementStep.PathTo(MovementBody.SELF, new MovementTarget.Around(AWAY), 1D,
                MovementReason.STUCK_DETOUR)), detour.plan().steps());
        assertEquals(32, detour.state().repathIn());
        assertTrue(GuardMovePlanner.travelling(1D, RANGE, true));
        assertFalse(GuardMovePlanner.travelling(4D, RANGE, false));
    }

    // ---------- flee ----------

    private static FleeMovePlanner.Facts flee(double distanceSq, StuckState stuck) {
        return new FleeMovePlanner.Facts(Optional.of(ITEM), MovementBody.SELF, AWAY, distanceSq, true, true, stuck);
    }

    @Test
    void stuckFleeRepathsWithItsFailureTeleportAndTeleportsWhenItGivesUp() {
        MovementStep.Teleport failed = new MovementStep.Teleport(MovementBody.SELF, new MovementPoint(10D, 64.5D, 0D),
                MovementReason.TELEPORT_PATH_FAILED, OVERWORLD);
        PlannedMove<MovementState.Flee> repath = FleeMovePlanner.tick(new MovementState.Flee(9), flee(200D, stuck(1)));
        assertEquals(List.of(new MovementStep.PathTo(MovementBody.SELF, new MovementTarget.Entity(ITEM), 1.2D,
                MovementReason.STUCK_REPATH, Optional.of(failed))), repath.plan().steps());
        assertEquals(20, repath.state().repathIn());
        PlannedMove<MovementState.Flee> jump = FleeMovePlanner.tick(new MovementState.Flee(9), flee(200D, stuck(3)));
        assertEquals(List.of(new MovementStep.Teleport(MovementBody.SELF, new MovementPoint(10D, 64.5D, 0D),
                MovementReason.TELEPORT_STUCK, OVERWORLD)), jump.plan().steps());
        // near the owner it keeps walking
        PlannedMove<MovementState.Flee> near = FleeMovePlanner.tick(new MovementState.Flee(9), flee(100D, stuck(3)));
        assertTrue(near.plan().isEmpty());
        assertEquals(8, near.state().repathIn());
    }

    // ---------- pick item ----------

    @Test
    void pickItemGivesTheItemUpForAWhile() {
        MovementState.PickItem state = PickItemMovePlanner.start(PickItemMovePlanner.initial(), 10);
        assertTrue(PickItemMovePlanner.recover(state, stuck(1), ITEM, AWAY, 50).plan().hasPath());
        PlannedMove<MovementState.PickItem> gaveUp = PickItemMovePlanner.recover(state, stuck(3), ITEM, AWAY, 50);
        assertEquals(List.of(new MovementStep.Stop(MovementBody.SELF, MovementReason.STUCK_GIVE_UP)),
                gaveUp.plan().steps());
        MovementState.PickItem after = gaveUp.state();
        assertFalse(PickItemMovePlanner.choosable(after, ITEM, 649));
        assertTrue(PickItemMovePlanner.choosable(after, ITEM, 650));
        assertTrue(PickItemMovePlanner.choosable(after, new TargetHandle(new UUID(0L, 3L), OVERWORLD), 100));
        // a new start keeps it given up on
        assertFalse(PickItemMovePlanner.choosable(PickItemMovePlanner.start(after, 60), ITEM, 100));
        assertTrue(PickItemMovePlanner.recover(state, new StuckState(true, HOME, 0, 3, false), ITEM, AWAY, 50)
                .plan().isEmpty());
        assertTrue(PickItemMovePlanner.travelling(true, 9D));
        assertFalse(PickItemMovePlanner.travelling(true, 8.99D));
        assertFalse(PickItemMovePlanner.travelling(false, 100D));
    }

    // ---------- combat ----------

    private static PlannedMove<MovementState.Combat> combat(CombatMove move, boolean due, StuckState stuck) {
        return CombatMovePlanner.plan(new MovementState.Combat(Optional.of(move.reason())),
                new CombatMovePlanner.Facts(move, ITEM, AWAY, 1D, due, false, stuck));
    }

    @Test
    void stuckCombatRecoversThenHoldsWhereItStands() {
        CombatMove toward = new CombatMove.Approach(HOME, CombatMove.Reason.TOWARD_TARGET);
        assertEquals(List.of(new MovementStep.PathTo(MovementBody.SELF, new MovementTarget.Entity(ITEM), 1D,
                MovementReason.STUCK_REPATH)), combat(toward, false, stuck(1)).plan().steps());
        // beside the target, not beside the approach point
        assertEquals(List.of(new MovementStep.PathTo(MovementBody.SELF, new MovementTarget.Around(AWAY), 1D,
                MovementReason.STUCK_DETOUR)), combat(toward, false, stuck(2)).plan().steps());
        List<MovementStep> hold = List.of(new MovementStep.Stop(MovementBody.SELF, MovementReason.STUCK_GIVE_UP));
        assertEquals(hold, combat(toward, false, stuck(3)).plan().steps());
        StuckState holding = new StuckState(true, HOME, 0, 4, false);
        assertTrue(combat(toward, false, holding).plan().isEmpty());
        // its own re-path time holds instead of walking back into the wall
        assertEquals(hold, combat(toward, true, holding).plan().steps());
        CombatMove edge = new CombatMove.Approach(HOME, CombatMove.Reason.TO_REGION_EDGE);
        assertEquals(List.of(new MovementStep.PathTo(MovementBody.SELF, new MovementTarget.Around(HOME), 1D,
                MovementReason.STUCK_DETOUR)), combat(edge, false, stuck(2)).plan().steps());
        assertTrue(CombatMovePlanner.travelling(toward));
        assertFalse(CombatMovePlanner.travelling(new CombatMove.Hold(CombatMove.Reason.IN_POSITION)));
    }

    // ---------- teleport safety ----------

    private static Set<TeleportDenial> denials(boolean same, int since, boolean loaded, boolean inside, boolean free) {
        return TeleportSafety.denials(new TeleportSafety.Facts(same, since, loaded, inside, free));
    }

    @Test
    void teleportNeedsEveryConditionAtOnce() {
        assertEquals(Set.of(), denials(true, 100, true, true, true));
        assertEquals(Set.of(), denials(true, Integer.MAX_VALUE, true, true, true));
        assertEquals(EnumSet.of(TeleportDenial.OTHER_DIMENSION), denials(false, 100, true, true, true));
        assertEquals(EnumSet.of(TeleportDenial.COOLDOWN), denials(true, 99, true, true, true));
        assertEquals(EnumSet.of(TeleportDenial.CHUNK_NOT_LOADED), denials(true, 100, false, true, true));
        assertEquals(EnumSet.of(TeleportDenial.OUTSIDE_WORLD_BORDER), denials(true, 100, true, false, true));
        assertEquals(EnumSet.of(TeleportDenial.NO_FREE_SPACE), denials(true, 100, true, true, false));
        assertEquals(EnumSet.of(TeleportDenial.OTHER_DIMENSION, TeleportDenial.COOLDOWN,
                TeleportDenial.CHUNK_NOT_LOADED, TeleportDenial.OUTSIDE_WORLD_BORDER,
                TeleportDenial.NO_FREE_SPACE), denials(false, 0, false, false, false));
    }

    @Test
    void landingsTryTheDestinationFirstThenNearestAround() {
        List<MovementPoint> landings = TeleportSafety.landings(HOME);
        assertEquals(50, landings.size());
        assertEquals(HOME, landings.get(0));
        assertEquals(new MovementPoint(0D, 65D, 0D), landings.get(1));
        assertEquals(new MovementPoint(-1D, 64D, 0D), landings.get(2));
        assertEquals(new MovementPoint(0D, 64D, -1D), landings.get(4));
        // the diagonals one block out come before anything two blocks out
        assertEquals(new MovementPoint(-1D, 64D, -1D), landings.get(10));
        assertEquals(new MovementPoint(-2D, 64D, 0D), landings.get(18));
        assertEquals(landings, TeleportSafety.landings(HOME));
    }
}
