package com.lulan.shincolle.ai.domain.movement;

import com.lulan.shincolle.ai.domain.DimensionKey;
import com.lulan.shincolle.ai.domain.LegacyDecodeResult;
import com.lulan.shincolle.ai.domain.TargetHandle;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MovementPlannerTest {
    private static final DimensionKey OVERWORLD = new DimensionKey("minecraft", "overworld");
    private static final TargetHandle OWNER = new TargetHandle(new UUID(0L, 1L), OVERWORLD);
    private static final MovementPoint HOME = new MovementPoint(0D, 64D, 0D);
    private static final MovementPoint AWAY = new MovementPoint(10D, 64D, 0D);
    private static final TeleportRule RULE = new TeleportRule(true, 200, 256);
    private static final FollowRange RANGE = new FollowRange(4D, 9D);

    private static MovementStep.Stop stop(MovementReason reason) {
        return new MovementStep.Stop(MovementBody.SELF, reason);
    }

    private static MovementStep.PathTo pathTo(MovementPoint point, MovementReason reason) {
        return new MovementStep.PathTo(MovementBody.SELF, new MovementTarget.Point(point), 1D, reason);
    }

    private static MovementStep.Teleport teleport(MovementPoint point, double yOffset, MovementReason reason) {
        return new MovementStep.Teleport(MovementBody.SELF, new MovementPoint(point.x(), point.y() + yOffset,
                point.z()), reason, OVERWORLD);
    }

    // ---------- follow ----------

    /** A formation slot as the goal reads it: a raw value outside the table is placed as the flagship's. */
    private static FormationSlot slot(int raw) {
        return FormationSlot.placedAs(FormationSlot.fromLegacy(raw));
    }

    private static MovementState.Follow follow(int repathIn, int timeTimer, int farTimer) {
        return new MovementState.Follow(repathIn, timeTimer, farTimer, 0, AWAY, HOME);
    }

    private static PlannedMove<MovementState.Follow> followMove(MovementState.Follow state, double distanceSq,
                                                               TeleportRule rule) {
        return FollowMovePlanner.move(state, new FollowMovePlanner.Facts(distanceSq, RANGE, Optional.of(AWAY), OVERWORLD,
                rule, StuckState.NONE));
    }

    @Test
    void followStartsWithTheOriginalTimers() {
        MovementState.Follow initial = FollowMovePlanner.initial(HOME);
        assertEquals(new MovementState.Follow(0, 0, 0, 0, HOME, HOME), initial);
        assertEquals(new MovementState.Follow(10, 0, 0, 77, HOME, HOME),
                FollowMovePlanner.start(new MovementState.Follow(3, 50, 40, 5, HOME, HOME), 77));
    }

    @Test
    void followCountsOneGoalTickAtATimeAndStuckTimeOnlyWhileStuck() {
        assertEquals(follow(9, 1, 0), FollowMovePlanner.count(follow(10, 0, 0), true));
        assertEquals(follow(9, 8, 0), FollowMovePlanner.count(follow(10, 7, 0), true));
        // getting anywhere starts the stuck time over
        assertEquals(follow(9, 0, 0), FollowMovePlanner.count(follow(10, 7, 0), false));
        MovementState.Follow resolved = FollowMovePlanner.ownerResolved(follow(1, 1, 1), 100);
        assertFalse(FollowMovePlanner.ownerResolveDue(resolved, 131));
        assertTrue(FollowMovePlanner.ownerResolveDue(resolved, 132));
    }

    @Test
    void followRangeKeepsTheFloatArithmetic() {
        float min = 1 + 0.6F * 0.75F;
        float max = 2 + 0.6F * 0.75F + 5F;
        assertEquals(new FollowRange(min * min, max * max), FollowMovePlanner.range(false, true, 1, 2, 0.6F));
        assertEquals(new FollowRange(4D, 7D), FollowMovePlanner.range(true, false, 1, 2, 0.6F));
        assertEquals(new FollowRange(4D, 64D), FollowMovePlanner.range(true, true, 1, 2, 0.6F));
    }

    @Test
    void followFormationPlaceIsWorkedOutAgainBeyondSeven() {
        MovementState.Follow state = follow(0, 0, 0);
        assertFalse(FollowMovePlanner.formationStale(state, new MovementPoint(2.64D, 64D, 0D)));
        assertTrue(FollowMovePlanner.formationStale(state, new MovementPoint(2.65D, 64D, 0D)));
        MovementState.Follow placed = FollowMovePlanner.formationPlace(state, HOME, AWAY, slot(2));
        assertEquals(HOME, placed.destination());
        assertEquals(AWAY, placed.anchorMemory());
        assertEquals(AWAY, FollowMovePlanner.toOwner(placed, AWAY).destination());
    }

    @Test
    void formationSlotDecodesTheTableAndKeepsWhatItDoesNotKnow() {
        for (int raw = 0; raw <= 5; raw++) {
            assertEquals(new LegacyDecodeResult.Known<>(new FormationSlot(raw)), FormationSlot.fromLegacy(raw));
            assertEquals(raw == 0, FollowMovePlanner.flagshipSlot(slot(raw)), "slot " + raw);
        }
        assertEquals(FormationSlot.FLAGSHIP, slot(0));
        for (int raw : new int[] {6, -1, 100, Integer.MAX_VALUE, Integer.MIN_VALUE}) {
            assertEquals(new LegacyDecodeResult.Unknown<FormationSlot>(raw), FormationSlot.fromLegacy(raw));
            // what the place calculation does with a slot outside its table
            assertEquals(FormationSlot.FLAGSHIP, slot(raw), "raw " + raw);
            assertTrue(FollowMovePlanner.flagshipSlot(slot(raw)), "raw " + raw);
            assertThrows(IllegalArgumentException.class, () -> new FormationSlot(raw));
        }
    }

    @Test
    void followDestinationKindIsWrittenWithTheDestination() {
        MovementState.Follow state = follow(0, 0, 0);
        assertEquals(FollowDestination.OWNER, state.destinationKind());
        assertEquals(FollowDestination.OWNER, FollowMovePlanner.initial(HOME).destinationKind());
        for (int slot = 1; slot <= 5; slot++) {
            assertEquals(FollowDestination.FORMATION_PLACE,
                    FollowMovePlanner.formationPlace(state, HOME, AWAY, slot(slot)).destinationKind(), "slot " + slot);
        }
        // the flagship's place is a block beside the owner, not the owner, and so is a slot outside the table,
        // which is worked out as the flagship
        for (int slot : new int[] {0, -1, 6}) {
            assertEquals(FollowDestination.FLAGSHIP_PLACE,
                    FollowMovePlanner.formationPlace(state, HOME, AWAY, slot(slot)).destinationKind(), "slot " + slot);
        }
        MovementState.Follow placed = FollowMovePlanner.formationPlace(state, HOME, AWAY, slot(3));
        assertEquals(FollowDestination.OWNER, FollowMovePlanner.toOwner(placed, AWAY).destinationKind());
        assertEquals(FollowDestination.FORMATION_PLACE,
                FollowMovePlanner.formationPlace(FollowMovePlanner.toOwner(placed, AWAY), HOME, AWAY, slot(3))
                        .destinationKind());
    }

    @Test
    void followDestinationKindSurvivesEveryUpdateThatDoesNotWriteTheDestination() {
        MovementState.Follow placed = FollowMovePlanner.formationPlace(follow(20, 200, 0), HOME, AWAY, slot(4));
        assertEquals(FollowDestination.FORMATION_PLACE, FollowMovePlanner.count(placed, true).destinationKind());
        assertEquals(FollowDestination.FORMATION_PLACE, FollowMovePlanner.ownerResolved(placed, 9).destinationKind());
        assertEquals(FollowDestination.FORMATION_PLACE, FollowMovePlanner.start(placed, 9).destinationKind());
        assertEquals(FollowDestination.FORMATION_PLACE, followMove(placed, 64D, RULE).state().destinationKind());
        MovementState.Follow owner = FollowMovePlanner.toOwner(placed, AWAY);
        assertEquals(FollowDestination.OWNER, FollowMovePlanner.count(owner, true).destinationKind());
        assertEquals(FollowDestination.OWNER, FollowMovePlanner.ownerResolved(owner, 9).destinationKind());
        assertEquals(FollowDestination.OWNER, FollowMovePlanner.start(owner, 9).destinationKind());
        assertEquals(FollowDestination.OWNER, followMove(owner, 64D, RULE).state().destinationKind());
    }

    @Test
    void followStopsInsideTheInnerDistanceAndRepathsWhenDue() {
        PlannedMove<MovementState.Follow> arrived = followMove(follow(0, 0, 0), 4D, RULE);
        assertEquals(List.of(stop(MovementReason.FOLLOW_ARRIVED), pathTo(AWAY, MovementReason.FOLLOW_OWNER)),
                arrived.plan().steps());
        assertEquals(32, arrived.state().repathIn());
        PlannedMove<MovementState.Follow> walking = followMove(follow(1, 0, 0), 4.01D, RULE);
        assertTrue(walking.plan().isEmpty());
        assertEquals(1, walking.state().repathIn());
    }

    @Test
    void followTeleportsWhenFarForLongerThanTheCooldown() {
        PlannedMove<MovementState.Follow> counting = followMove(follow(5, 0, 199), 257D, RULE);
        assertEquals(200, counting.state().farTimer());
        assertTrue(counting.plan().isEmpty());
        PlannedMove<MovementState.Follow> far = followMove(follow(5, 250, 200), 257D, RULE);
        // the distance teleport comes first and ends the tick, leaving the time timer running
        assertEquals(List.of(teleport(AWAY, 0.75D, MovementReason.TELEPORT_FAR)), far.plan().steps());
        assertEquals(0, far.state().farTimer());
        assertEquals(250, far.state().timeTimer());
        PlannedMove<MovementState.Follow> near = followMove(follow(5, 0, 200), 256D, RULE);
        assertEquals(200, near.state().farTimer());
        assertTrue(near.plan().isEmpty());
    }

    @Test
    void followTeleportsWhenStuckForLongerThanTheCooldown() {
        assertTrue(followMove(follow(5, 200, 0), 100D, RULE).plan().isEmpty());
        PlannedMove<MovementState.Follow> late = followMove(follow(5, 201, 0), 100D, RULE);
        assertEquals(List.of(teleport(AWAY, 0.75D, MovementReason.TELEPORT_TIME)), late.plan().steps());
        assertEquals(0, late.state().timeTimer());
    }

    @Test
    void followNeverTeleportsWithTeleportingOffOrNoOwner() {
        TeleportRule off = new TeleportRule(false, 200, 256);
        PlannedMove<MovementState.Follow> disabled = followMove(follow(5, 500, 500), 1_000D, off);
        assertTrue(disabled.plan().isEmpty());
        assertEquals(500, disabled.state().farTimer());
        PlannedMove<MovementState.Follow> noOwner = FollowMovePlanner.move(follow(5, 500, 500),
                new FollowMovePlanner.Facts(1_000D, RANGE, Optional.empty(), OVERWORLD, RULE, StuckState.NONE));
        assertTrue(noOwner.plan().isEmpty());
    }

    // ---------- guard ----------

    private static MovementState.Guard guard(int repathIn, int stillTimer, int farTimer, boolean moving) {
        return new MovementState.Guard(repathIn, stillTimer, farTimer, 0, AWAY, HOME, moving);
    }

    private static PlannedMove<MovementState.Guard> guardMove(MovementState.Guard state, double distanceSq,
                                                             boolean temporary) {
        return GuardMovePlanner.move(state, new GuardMovePlanner.Facts(distanceSq, RANGE, temporary, OVERWORLD, RULE,
                StuckState.NONE));
    }

    @Test
    void guardStartsAndStopsWithTheOriginalValues() {
        MovementState.Guard initial = GuardMovePlanner.initial();
        assertEquals(new MovementPoint(-1D, -1D, -1D), initial.destination());
        assertEquals(new MovementPoint(-1D, -100D, -1D), initial.anchorMemory());
        assertEquals(new MovementState.Guard(10, 0, 0, 40, AWAY, HOME, true),
                GuardMovePlanner.start(guard(3, 7, 8, true), 40));
        assertEquals(new MovementState.Guard(10, 7, 8, 0, AWAY, HOME, false),
                GuardMovePlanner.stopped(guard(3, 7, 8, true)));
    }

    @Test
    void guardCountsOnlyStuckTicksInARow() {
        assertEquals(guard(4, 1, 0, false), GuardMovePlanner.count(guard(5, 0, 0, false), true));
        assertEquals(guard(4, 0, 0, false), GuardMovePlanner.count(guard(5, 6, 0, false), false));
        MovementState.Guard resolved = GuardMovePlanner.anchorResolved(guard(1, 1, 1, false), 10);
        assertFalse(GuardMovePlanner.anchorResolveDue(resolved, 17));
        assertTrue(GuardMovePlanner.anchorResolveDue(resolved, 18));
    }

    @Test
    void guardRangeDependsOnFormationAndWhatIsGuarded() {
        assertEquals(new FollowRange(5D, 9D), GuardMovePlanner.range(true, true, false, 1, 2, 0.6F));
        assertEquals(new FollowRange(4D, 7D), GuardMovePlanner.range(true, false, false, 1, 2, 0.6F));
        assertEquals(new FollowRange(5D, 64D), GuardMovePlanner.range(true, true, true, 1, 2, 0.6F));
        assertEquals(FollowMovePlanner.range(false, false, 3, 4, 1F), GuardMovePlanner.range(false, true, false, 3, 4, 1F));
        MovementState.Guard state = guard(0, 0, 0, false);
        assertFalse(GuardMovePlanner.formationStale(state, new MovementPoint(2.44D, 64D, 0D)));
        assertTrue(GuardMovePlanner.formationStale(state, new MovementPoint(2.45D, 64D, 0D)));
    }

    @Test
    void guardStopsOnArrivalUnlessAMoveOrderRunsToTheEnd() {
        PlannedMove<MovementState.Guard> arrived = guardMove(guard(3, 0, 0, true), 4D, false);
        assertEquals(List.of(stop(MovementReason.GUARD_ARRIVED)), arrived.plan().steps());
        assertFalse(arrived.state().moving());
        PlannedMove<MovementState.Guard> moveOrder = guardMove(guard(3, 0, 0, true), 4D, true);
        assertTrue(moveOrder.plan().isEmpty());
        assertTrue(moveOrder.state().moving());
    }

    @Test
    void guardRepathsWhenDueAndRemembersWhetherThePathWasTaken() {
        PlannedMove<MovementState.Guard> due = guardMove(guard(0, 0, 0, false), 50D, false);
        assertEquals(List.of(pathTo(AWAY, MovementReason.GUARD)), due.plan().steps());
        assertEquals(32, due.state().repathIn());
        assertTrue(GuardMovePlanner.pathed(due.state(), PathOutcome.ISSUED).moving());
        assertFalse(GuardMovePlanner.pathed(guard(0, 0, 0, true), PathOutcome.FAILED).moving());
        assertTrue(GuardMovePlanner.pathed(guard(0, 0, 0, true), PathOutcome.NONE).moving());
    }

    @Test
    void guardTeleportsWhenFarOrStuckForLongerThanTheCooldown() {
        PlannedMove<MovementState.Guard> far = guardMove(guard(5, 300, 200, false), 257D, false);
        assertEquals(List.of(teleport(AWAY, 0.75D, MovementReason.TELEPORT_FAR)), far.plan().steps());
        assertEquals(300, far.state().stillTimer());
        PlannedMove<MovementState.Guard> still = guardMove(guard(5, 201, 0, false), 50D, false);
        assertEquals(List.of(teleport(AWAY, 0.75D, MovementReason.TELEPORT_TIME)), still.plan().steps());
        assertEquals(0, still.state().stillTimer());
        assertTrue(GuardMovePlanner.move(guard(5, 500, 500, false),
                new GuardMovePlanner.Facts(1_000D, RANGE, false, OVERWORLD, new TeleportRule(false, 200, 256),
                        StuckState.NONE)).plan().isEmpty());
    }

    // ---------- flee ----------

    private static FleeMovePlanner.Facts flee(double distanceSq, boolean sameLevel, boolean canTeleport,
                                              MovementBody body) {
        return new FleeMovePlanner.Facts(Optional.of(OWNER), body, AWAY, distanceSq, sameLevel, canTeleport,
                StuckState.NONE);
    }

    @Test
    void fleeRepathsEveryTwentyGoalTicks() {
        PlannedMove<MovementState.Flee> first = FleeMovePlanner.tick(FleeMovePlanner.start(),
                flee(50D, true, true, MovementBody.SELF));
        assertTrue(first.plan().hasPath());
        assertEquals(20, first.state().repathIn());
        PlannedMove<MovementState.Flee> waiting = FleeMovePlanner.tick(first.state(), flee(50D, true, true,
                MovementBody.SELF));
        assertTrue(waiting.plan().isEmpty());
        assertEquals(19, waiting.state().repathIn());
        PlannedMove<MovementState.Flee> noOwner = FleeMovePlanner.tick(new MovementState.Flee(1),
                new FleeMovePlanner.Facts(Optional.empty(), MovementBody.SELF, AWAY, 50D, true, true, StuckState.NONE));
        assertTrue(noOwner.plan().isEmpty());
        assertEquals(20, noOwner.state().repathIn());
    }

    @Test
    void fleeTeleportsOnlyWhenThePathFailsFarFromTheOwnerInTheSameDimension() {
        MovementStep.Teleport jump = new MovementStep.Teleport(MovementBody.VEHICLE,
                new MovementPoint(AWAY.x(), AWAY.y() + 0.5D, AWAY.z()), MovementReason.TELEPORT_PATH_FAILED,
                OVERWORLD);
        MovementStep.PathTo path = (MovementStep.PathTo) FleeMovePlanner.tick(new MovementState.Flee(0),
                flee(101D, true, true, MovementBody.VEHICLE)).plan().steps().get(0);
        assertEquals(new MovementStep.PathTo(MovementBody.VEHICLE, new MovementTarget.Entity(OWNER), 1.2D,
                MovementReason.FLEE_TO_OWNER, Optional.of(jump)), path);
        for (FleeMovePlanner.Facts facts : List.of(flee(100D, true, true, MovementBody.VEHICLE),
                flee(101D, false, true, MovementBody.VEHICLE), flee(101D, true, false, MovementBody.VEHICLE))) {
            MovementStep.PathTo without = (MovementStep.PathTo) FleeMovePlanner.tick(new MovementState.Flee(0), facts)
                    .plan().steps().get(0);
            assertEquals(Optional.empty(), without.onFailure(), facts.toString());
        }
    }

    // ---------- pick item ----------

    @Test
    void pickItemRescansEverySixteenTicks() {
        MovementState.PickItem started = PickItemMovePlanner.start(PickItemMovePlanner.initial(), 30);
        assertTrue(PickItemMovePlanner.scanDue(started, 30));
        MovementState.PickItem scanned = PickItemMovePlanner.scanned(started, 30);
        assertFalse(PickItemMovePlanner.scanDue(scanned, 45));
        assertTrue(PickItemMovePlanner.scanDue(scanned, 46));
        assertEquals(List.of(new MovementStep.PathTo(MovementBody.SELF, new MovementTarget.Entity(OWNER), 1D,
                MovementReason.PICK_ITEM)), PickItemMovePlanner.toItem(OWNER).steps());
        assertEquals(List.of(stop(MovementReason.PICK_ITEM_REACHED)), PickItemMovePlanner.reached().steps());
    }

    // ---------- combat ----------

    private static PlannedMove<MovementState.Combat> combat(MovementState.Combat state, CombatMove move, boolean due,
                                                           boolean stopEveryTick) {
        return CombatMovePlanner.plan(state, new CombatMovePlanner.Facts(move, OWNER, AWAY, 1D, due, stopEveryTick,
                StuckState.NONE));
    }

    private static MovementState.Combat after(CombatMove.Reason reason) {
        return new MovementState.Combat(Optional.of(reason));
    }

    @Test
    void combatApproachesOnlyWhenDueOrOnARegionSwitch() {
        CombatMove toward = new CombatMove.Approach(AWAY, CombatMove.Reason.TOWARD_TARGET);
        assertEquals(List.of(new MovementStep.PathTo(MovementBody.SELF, new MovementTarget.Entity(OWNER), 1D,
                MovementReason.COMBAT_TOWARD_TARGET)), combat(MovementState.Combat.NONE, toward, true, true).plan().steps());
        // a first move that is not regional waits for its own time
        assertTrue(combat(MovementState.Combat.NONE, toward, false, true).plan().isEmpty());
        assertTrue(combat(after(CombatMove.Reason.IN_POSITION), toward, false, true).plan().isEmpty());
        // leaving the region rules re-paths at once
        assertTrue(combat(after(CombatMove.Reason.AT_REGION_EDGE), toward, false, true).plan().hasPath());
        CombatMove edge = new CombatMove.Approach(HOME, CombatMove.Reason.TO_REGION_EDGE);
        assertEquals(List.of(new MovementStep.PathTo(MovementBody.SELF, new MovementTarget.Point(HOME), 1D,
                MovementReason.COMBAT_TO_REGION_EDGE)), combat(after(CombatMove.Reason.TOWARD_TARGET), edge, false, true)
                .plan().steps());
        assertTrue(combat(after(CombatMove.Reason.TO_REGION_EDGE), edge, false, true).plan().isEmpty());
        assertEquals(after(CombatMove.Reason.TO_REGION_EDGE),
                combat(after(CombatMove.Reason.TOWARD_TARGET), edge, false, true).state());
    }

    @Test
    void combatHoldStopsWhenDueOrEveryTickIfAsked() {
        CombatMove hold = new CombatMove.Hold(CombatMove.Reason.IN_POSITION);
        List<MovementStep> stop = List.of(stop(MovementReason.COMBAT_HOLD));
        assertEquals(stop, combat(after(CombatMove.Reason.IN_POSITION), hold, false, true).plan().steps());
        assertTrue(combat(after(CombatMove.Reason.IN_POSITION), hold, false, false).plan().isEmpty());
        assertEquals(stop, combat(after(CombatMove.Reason.IN_POSITION), hold, true, false).plan().steps());
        assertEquals(stop, combat(after(CombatMove.Reason.TOWARD_TARGET),
                new CombatMove.Hold(CombatMove.Reason.NO_COMBAT_MOVEMENT), false, false).plan().steps());
    }

    /** The ship measured 25 at the last look, has been stuck up to the cooldown, and the owner has since come close. */
    private static PlannedMove<MovementState.Follow> stuckThenOwnerComesClose(double distanceSq,
                                                                             double ownerDistanceSq) {
        MovementState.Follow counted = FollowMovePlanner.count(follow(20, 200, 0), true);
        assertEquals(201, counted.timeTimer());
        return FollowMovePlanner.move(counted, new FollowMovePlanner.Facts(distanceSq, RANGE, Optional.of(AWAY),
                OVERWORLD, RULE, new StuckState(true, HOME, 0, 1, false), ownerDistanceSq));
    }

    @Test
    void stuckFollowerDoesNotTeleportByTimeToAnOwnerAlreadyInsideTheMinimumRange() {
        PlannedMove<MovementState.Follow> planned = stuckThenOwnerComesClose(25D, 1D);
        assertFalse(planned.plan().steps().contains(teleport(AWAY, 0.75D, MovementReason.TELEPORT_TIME)),
                "the owner is one block away, but the stale distance teleported");
        assertEquals(0, planned.state().timeTimer());
    }

    @Test
    void stuckFollowerStillTeleportsByTimeWhileTheOwnerIsOutsideTheMinimumRange() {
        PlannedMove<MovementState.Follow> planned = stuckThenOwnerComesClose(25D, 25D);
        assertTrue(planned.plan().steps().contains(teleport(AWAY, 0.75D, MovementReason.TELEPORT_TIME)));
        assertEquals(0, planned.state().timeTimer());
    }

    /** Stuck for the cooldown, with the owner now one block away, for a destination stored as {@code placed}. */
    private static PlannedMove<MovementState.Follow> stuckNearOwner(MovementState.Follow placed) {
        MovementState.Follow counted = FollowMovePlanner.count(withTimers(placed, 20, 200, 0), true);
        assertEquals(201, counted.timeTimer());
        return FollowMovePlanner.move(counted, new FollowMovePlanner.Facts(64D, new FollowRange(4D, 7D),
                Optional.of(AWAY), OVERWORLD, RULE, new StuckState(true, HOME, 0, 1, false), 1D));
    }

    private static MovementState.Follow withTimers(MovementState.Follow state, int repathIn, int timeTimer,
                                                   int farTimer) {
        return new MovementState.Follow(repathIn, timeTimer, farTimer, state.ownerResolveAt(), state.destination(),
                state.anchorMemory(), state.destinationKind());
    }

    @Test
    void stuckFormationShipStillTeleportsByTimeWhileItsPlaceIsFarEvenIfTheOwnerIsNear() {
        PlannedMove<MovementState.Follow> planned = stuckNearOwner(
                FollowMovePlanner.formationPlace(follow(20, 200, 0), HOME, AWAY, slot(5)));
        assertTrue(planned.plan().steps().contains(teleport(AWAY, 0.75D, MovementReason.TELEPORT_TIME)),
                "the formation place is eight blocks away, but the near owner discarded the recovery");
        assertEquals(0, planned.state().timeTimer());
    }

    /**
     * Stuck for the cooldown with a flagship slot stored at (100, 64, 100) and the ship beside it: the stored
     * slot is 8.65 away (squared), the owner 3.61 away, and the minimum distance squared is 4.
     */
    private static PlannedMove<MovementState.Follow> stuckFlagship(double flagshipPlaceDistanceSq) {
        MovementPoint place = new MovementPoint(100D, 64D, 100D);
        MovementPoint owner = new MovementPoint(100.9D, 64D, 100.9D);
        MovementPoint ship = new MovementPoint(102.8D, 64D, 100.9D);
        MovementState.Follow placed = FollowMovePlanner.formationPlace(follow(20, 200, 0), place, owner, slot(0));
        assertEquals(FollowDestination.FLAGSHIP_PLACE, placed.destinationKind());
        MovementState.Follow counted = FollowMovePlanner.count(placed, true);
        assertEquals(201, counted.timeTimer());
        double storedSq = place.distanceSq(ship);
        double ownerSq = owner.distanceSq(ship);
        assertTrue(storedSq > 4D && ownerSq <= 4D, "stored " + storedSq + " owner " + ownerSq);
        return FollowMovePlanner.move(counted, new FollowMovePlanner.Facts(storedSq, new FollowRange(4D, 7D),
                Optional.of(owner), OVERWORLD, RULE, new StuckState(true, ship, 0, 1, false), ownerSq,
                flagshipPlaceDistanceSq));
    }

    private static final MovementStep.Teleport FLAGSHIP_TIME_TELEPORT = teleport(
            new MovementPoint(100.9D, 64D, 100.9D), 0.75D, MovementReason.TELEPORT_TIME);

    @Test
    void stuckFlagshipTeleportsByTimeWhenItsRoundedPlaceIsOutsideTheMinimumRangeAndTheOwnerIsInside() {
        PlannedMove<MovementState.Follow> planned = stuckFlagship(8.65D);
        assertTrue(planned.plan().steps().contains(FLAGSHIP_TIME_TELEPORT),
                "the rounded flagship place is 2.9 blocks away, but the near owner discarded the recovery");
        assertEquals(0, planned.state().timeTimer());
    }

    @Test
    void stuckFlagshipDoesNotTeleportByTimeOnceItsPlaceAsWorkedOutNowIsInsideTheMinimumRange() {
        for (double placeSq : new double[] {0D, 3.99D, 4D}) {
            PlannedMove<MovementState.Follow> planned = stuckFlagship(placeSq);
            assertFalse(planned.plan().steps().contains(FLAGSHIP_TIME_TELEPORT), "place " + placeSq);
            assertEquals(0, planned.state().timeTimer(), "place " + placeSq);
        }
        assertTrue(stuckFlagship(4.01D).plan().steps().contains(FLAGSHIP_TIME_TELEPORT));
    }

    @Test
    void stuckFlagshipTeleportsByTimeWhenThePlaceWasNotWorkedOut() {
        assertTrue(stuckFlagship(Double.POSITIVE_INFINITY).plan().steps().contains(FLAGSHIP_TIME_TELEPORT));
        // the facts without a flagship distance default to the same
        MovementState.Follow counted = FollowMovePlanner.count(FollowMovePlanner.formationPlace(follow(20, 200, 0),
                HOME, AWAY, slot(0)), true);
        FollowMovePlanner.Facts without = new FollowMovePlanner.Facts(64D, new FollowRange(4D, 7D),
                Optional.of(AWAY), OVERWORLD, RULE, new StuckState(true, HOME, 0, 1, false), 1D);
        assertEquals(Double.POSITIVE_INFINITY, without.flagshipPlaceDistanceSq());
        assertEquals(Double.POSITIVE_INFINITY, new FollowMovePlanner.Facts(64D, new FollowRange(4D, 7D),
                Optional.of(AWAY), OVERWORLD, RULE, new StuckState(true, HOME, 0, 1, false))
                .flagshipPlaceDistanceSq());
        assertTrue(FollowMovePlanner.move(counted, without).plan().steps()
                .contains(teleport(AWAY, 0.75D, MovementReason.TELEPORT_TIME)));
    }

    @Test
    void theFlagshipPlaceDistanceDoesNotChangeTheOtherKindsOfDestination() {
        // the owner is judged by how near the owner is, the formation place never is, whatever the flagship distance
        for (double flagshipSq : new double[] {0D, 100D, Double.POSITIVE_INFINITY}) {
            MovementState.Follow owner = FollowMovePlanner.count(FollowMovePlanner.toOwner(follow(20, 200, 0), AWAY),
                    true);
            MovementState.Follow place = FollowMovePlanner.count(FollowMovePlanner.formationPlace(
                    follow(20, 200, 0), HOME, AWAY, slot(3)), true);
            FollowMovePlanner.Facts near = new FollowMovePlanner.Facts(64D, new FollowRange(4D, 7D),
                    Optional.of(AWAY), OVERWORLD, RULE, new StuckState(true, HOME, 0, 1, false), 1D, flagshipSq);
            assertFalse(FollowMovePlanner.move(owner, near).plan().steps()
                    .contains(teleport(AWAY, 0.75D, MovementReason.TELEPORT_TIME)), "owner, flagship " + flagshipSq);
            assertTrue(FollowMovePlanner.move(place, near).plan().steps()
                    .contains(teleport(AWAY, 0.75D, MovementReason.TELEPORT_TIME)), "place, flagship " + flagshipSq);
        }
    }

    @Test
    void theFlagshipPlaceIsWantedOnlyWhenTheStuckTeleportCouldJudgeAStoredFlagshipPlace() {
        MovementState.Follow flagship = FollowMovePlanner.formationPlace(follow(20, 200, 0), HOME, AWAY, slot(0));
        assertFalse(FollowMovePlanner.flagshipPlaceDistanceWanted(flagship, RULE), "timeTimer 200 is not past 200");
        assertTrue(FollowMovePlanner.flagshipPlaceDistanceWanted(FollowMovePlanner.count(flagship, true), RULE));
        assertFalse(FollowMovePlanner.flagshipPlaceDistanceWanted(FollowMovePlanner.count(flagship, false), RULE));
        assertFalse(FollowMovePlanner.flagshipPlaceDistanceWanted(FollowMovePlanner.count(flagship, true),
                new TeleportRule(false, 200, 256)));
        for (MovementState.Follow other : new MovementState.Follow[] {
                FollowMovePlanner.count(FollowMovePlanner.toOwner(follow(20, 200, 0), AWAY), true),
                FollowMovePlanner.count(FollowMovePlanner.formationPlace(follow(20, 200, 0), HOME, AWAY, slot(2)), true)}) {
            assertFalse(FollowMovePlanner.flagshipPlaceDistanceWanted(other, RULE), other.destinationKind().name());
        }
    }

    @Test
    void stuckShipKeepsTheKindOfTheDestinationItWasWrittenWithAcrossAFormationSwitch() {
        // formation switched on: the stored destination is still the owner, written before the switch
        PlannedMove<MovementState.Follow> switchedOn = stuckNearOwner(FollowMovePlanner.toOwner(follow(20, 200, 0), AWAY));
        assertFalse(switchedOn.plan().steps().contains(teleport(AWAY, 0.75D, MovementReason.TELEPORT_TIME)));
        // formation switched off: the stored destination is still the formation place, written before the switch
        PlannedMove<MovementState.Follow> switchedOff = stuckNearOwner(
                FollowMovePlanner.formationPlace(follow(20, 200, 0), HOME, AWAY, slot(2)));
        assertTrue(switchedOff.plan().steps().contains(teleport(AWAY, 0.75D, MovementReason.TELEPORT_TIME)));
        // the next update rewrites both together: a formation place, then the owner
        PlannedMove<MovementState.Follow> rewrittenAsPlace = stuckNearOwner(FollowMovePlanner.formationPlace(
                FollowMovePlanner.toOwner(follow(20, 200, 0), AWAY), HOME, AWAY, slot(2)));
        assertTrue(rewrittenAsPlace.plan().steps().contains(teleport(AWAY, 0.75D, MovementReason.TELEPORT_TIME)));
        PlannedMove<MovementState.Follow> rewrittenAsOwner = stuckNearOwner(FollowMovePlanner.toOwner(
                FollowMovePlanner.formationPlace(follow(20, 200, 0), HOME, AWAY, slot(2)), AWAY));
        assertFalse(rewrittenAsOwner.plan().steps().contains(teleport(AWAY, 0.75D, MovementReason.TELEPORT_TIME)));
    }

    @Test
    void farTeleportKeepsReadingTheStoredDistance() {
        MovementState.Follow far = FollowMovePlanner.count(follow(20, 0, 200), false);
        PlannedMove<MovementState.Follow> planned = FollowMovePlanner.move(far, new FollowMovePlanner.Facts(300D,
                RANGE, Optional.of(AWAY), OVERWORLD, RULE, StuckState.NONE, 1D));
        assertTrue(planned.plan().steps().contains(teleport(AWAY, 0.75D, MovementReason.TELEPORT_FAR)));
    }

    @Test
    void plannersAreDeterministic() {
        MovementState.Follow state = follow(0, 201, 199);
        FollowMovePlanner.Facts facts = new FollowMovePlanner.Facts(300D, RANGE, Optional.of(AWAY), OVERWORLD, RULE,
                StuckState.NONE);
        assertEquals(FollowMovePlanner.move(state, facts), FollowMovePlanner.move(state, facts));
        MovementState.Guard guard = guard(0, 300, 0, true);
        GuardMovePlanner.Facts guardFacts = new GuardMovePlanner.Facts(3D, RANGE, false, OVERWORLD, RULE,
                StuckState.NONE);
        assertEquals(GuardMovePlanner.move(guard, guardFacts), GuardMovePlanner.move(guard, guardFacts));
    }
}
