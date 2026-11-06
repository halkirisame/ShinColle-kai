package com.lulan.shincolle.ai.domain.crane;

import com.lulan.shincolle.ai.domain.DimensionKey;
import com.lulan.shincolle.ai.domain.command.CommandPos;
import com.lulan.shincolle.ai.domain.movement.MovementIntent;
import com.lulan.shincolle.ai.domain.movement.MovementPoint;
import com.lulan.shincolle.ai.domain.movement.MovementReason;
import com.lulan.shincolle.ai.domain.movement.MovementStep;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CraneLifecycleTest {
    private static final DimensionKey DIMENSION = new DimensionKey("minecraft", "overworld");
    private static final MovementPoint CRANE = new MovementPoint(3D, 5D, 3D);
    private static final MovementIntent GUARD = new MovementIntent.GuardPosition(DIMENSION, new CommandPos(3, 5, 3));
    private static final CraneEntryApproach.Bounds CAPTURE = new CraneEntryApproach.Bounds(-4D, -3D, -4D, 10D, 5D, 10D);
    private static final CraneEntryApproach.Bounds INSIDE = new CraneEntryApproach.Bounds(3D, 3D, 3D, 4D, 5D, 4D);
    private static final CraneEntryApproach.Bounds OUTSIDE = new CraneEntryApproach.Bounds(7D, 5D, 3D, 8D, 7D, 4D);
    private static final CraneLifecycle.Work NONE = new CraneLifecycle.Work(false, false, false, false);
    private static final CraneLifecycle.InventoryCompletion COMPLETE =
            new CraneLifecycle.InventoryCompletion(true, true, true, true);

    private static CraneLifecycle.ArrivalFacts facts(double distance, CraneLifecycle.Phase phase) {
        return new CraneLifecycle.ArrivalFacts(true, true, false, false, false, false, false,
                true, true, distance, phase);
    }

    @Test
    void arrivalUsesStrictDistanceBoundaryAndNeverDowngradesWorkingShip() {
        assertEquals(new CraneLifecycle.Arrival(CraneLifecycle.Phase.WAITING, true),
                CraneLifecycle.arrive(facts(24.99D, CraneLifecycle.Phase.IDLE)));
        assertEquals(new CraneLifecycle.Arrival(CraneLifecycle.Phase.IDLE, false),
                CraneLifecycle.arrive(facts(25D, CraneLifecycle.Phase.IDLE)));
        assertEquals(new CraneLifecycle.Arrival(CraneLifecycle.Phase.WORKING, false),
                CraneLifecycle.arrive(facts(4D, CraneLifecycle.Phase.WORKING)));
    }

    @Test
    void invalidDestinationClearsItsCranePhase() {
        var other = new CraneLifecycle.ArrivalFacts(true, true, false, false, false, false, false,
                false, false, 0D, CraneLifecycle.Phase.WORKING);
        assertEquals(CraneLifecycle.Phase.IDLE, CraneLifecycle.arrive(other).phase());
    }

    @Test
    void sittingPausesPositioningWithoutCancelingExistingWork() {
        var sitting = new CraneLifecycle.ArrivalFacts(true, true, false, true, false, false, false,
                true, true, 4D, CraneLifecycle.Phase.WORKING);
        assertEquals(CraneLifecycle.Phase.WORKING, CraneLifecycle.arrive(sitting).phase());
        assertTrue(motion(sitting, false, true, true, GUARD, DIMENSION, CRANE).isEmpty());
    }

    @Test
    void queueRequiresAllConditionsAndPrioritizesWorkingShips() {
        UUID waiting = UUID.randomUUID();
        UUID working = UUID.randomUUID();
        List<CraneLifecycle.Candidate> candidates = List.of(
                new CraneLifecycle.Candidate(waiting, CraneLifecycle.Phase.WAITING, true, true, true),
                new CraneLifecycle.Candidate(UUID.randomUUID(), CraneLifecycle.Phase.WORKING, false, true, true),
                new CraneLifecycle.Candidate(UUID.randomUUID(), CraneLifecycle.Phase.WORKING, true, false, true),
                new CraneLifecycle.Candidate(UUID.randomUUID(), CraneLifecycle.Phase.WORKING, true, true, false),
                new CraneLifecycle.Candidate(working, CraneLifecycle.Phase.WORKING, true, true, true));
        assertEquals(working, CraneLifecycle.choose(candidates).orElseThrow());
        assertEquals(waiting, CraneLifecycle.choose(candidates.subList(0, 4)).orElseThrow());
    }

    @Test
    void approachPreservesSitAndFleeAndRequiresFuel() {
        var far = facts(100D, CraneLifecycle.Phase.IDLE);
        assertTrue(motion(far, false, true, false, new MovementIntent.Sit(), DIMENSION, CRANE).isEmpty());
        assertTrue(motion(far, false, true, false, new MovementIntent.Flee(), DIMENSION, CRANE).isEmpty());
        assertTrue(motion(far, false, false, false, GUARD, DIMENSION, CRANE).isEmpty());
        var intent = motion(far, false, true, false, GUARD, DIMENSION, CRANE).orElseThrow();
        assertEquals(new MovementPoint(3.5D, 3D, 3.5D), intent.destination());
        assertEquals(1D, intent.speed());
        assertEquals(MovementReason.CRANE_APPROACH, intent.reason());
        assertEquals(MovementReason.CRANE_APPROACH,
                CraneLifecycle.motion(facts(100D, CraneLifecycle.Phase.WAITING), false, true, false,
                        GUARD, DIMENSION, CRANE, OUTSIDE, CAPTURE).orElseThrow().reason());
    }

    @Test
    void dockingRequiresCaptureAndUsesHalfSpeed() {
        var working = facts(4D, CraneLifecycle.Phase.WORKING);
        assertTrue(motion(working, false, true, false, GUARD, DIMENSION, CRANE).isEmpty());
        var dock = motion(working, false, true, true, GUARD, DIMENSION, CRANE).orElseThrow();
        assertEquals(0.5D, dock.speed());
        assertEquals(MovementReason.CRANE_DOCK, dock.reason());
    }

    @Test
    void waitingOutsideCaptureAreaKeepsApproachingBelowCrane() {
        var entry = CraneLifecycle.motion(facts(16D, CraneLifecycle.Phase.WAITING), false, true, false,
                GUARD, DIMENSION, CRANE, OUTSIDE, CAPTURE).orElseThrow();
        assertEquals(new MovementPoint(3.5D, 3D, 3.5D), entry.destination());
        assertEquals(MovementReason.CRANE_APPROACH, entry.reason());
        assertEquals(1D, entry.speed());
    }

    @Test
    void waitingInsideCaptureAreaStopsEntryAndBoundaryContactIsNotInside() {
        var waiting = facts(4D, CraneLifecycle.Phase.WAITING);
        assertTrue(motion(waiting, false, true, false, GUARD, DIMENSION, CRANE).isEmpty());
        assertTrue(motion(facts(36D, CraneLifecycle.Phase.WAITING), false, true, false,
                GUARD, DIMENSION, CRANE).isEmpty());
        assertEquals(Set.of(CraneEntryApproach.StopReason.IN_CAPTURE_AREA),
                CraneEntryApproach.decide(waiting, false, true, false, GUARD, INSIDE, CAPTURE).stopReasons());
        assertFalse(OUTSIDE.intersects(CAPTURE));
        var barelyInside = new CraneEntryApproach.Bounds(7D, 4.999D, 3D, 8D, 7D, 4D);
        assertTrue(barelyInside.intersects(CAPTURE));
        assertTrue(CraneLifecycle.motion(waiting, false, true, false, GUARD, DIMENSION, CRANE,
                barelyInside, CAPTURE).isEmpty());
    }

    @Test
    void waitingEntryPreservesInhibitorsAndExplainsAllStopReasons() {
        var waiting = facts(16D, CraneLifecycle.Phase.WAITING);
        assertFalse(CraneEntryApproach.decide(waiting, false, true, true, GUARD, OUTSIDE, CAPTURE).approach());
        assertTrue(CraneEntryApproach.decide(waiting, true, false, false, new MovementIntent.Sit(), OUTSIDE, CAPTURE)
                .stopReasons().containsAll(Set.of(CraneEntryApproach.StopReason.RIDING,
                        CraneEntryApproach.StopReason.NO_FUEL, CraneEntryApproach.StopReason.OTHER_MOVEMENT_INTENT)));
        for (MovementIntent higher : List.of(new MovementIntent.Sit(), new MovementIntent.Flee())) {
            assertTrue(CraneLifecycle.motion(waiting, false, true, false, higher, DIMENSION, CRANE,
                    OUTSIDE, CAPTURE).isEmpty());
        }
        var invalid = new CraneLifecycle.ArrivalFacts(true, true, false, true, true, false, false,
                true, false, 16D, CraneLifecycle.Phase.WAITING);
        assertEquals(Set.of(CraneEntryApproach.StopReason.INVALID_DESTINATION,
                        CraneEntryApproach.StopReason.SITTING, CraneEntryApproach.StopReason.LEASHED),
                CraneEntryApproach.decide(invalid, false, true, false, GUARD, OUTSIDE, CAPTURE).stopReasons());
        assertTrue(CraneLifecycle.motion(invalid, false, true, false, GUARD, DIMENSION, CRANE,
                OUTSIDE, CAPTURE).isEmpty());
    }

    @Test
    void captureIntersectionUsesAllSixFacesRatherThanTheBodyCenter() {
        var capture = new CraneEntryApproach.Bounds(0D, 0D, 0D, 1D, 1D, 1D);
        for (var outside : List.of(
                new CraneEntryApproach.Bounds(-1D, 0D, 0D, 0D, 1D, 1D),
                new CraneEntryApproach.Bounds(1D, 0D, 0D, 2D, 1D, 1D),
                new CraneEntryApproach.Bounds(0D, -1D, 0D, 1D, 0D, 1D),
                new CraneEntryApproach.Bounds(0D, 1D, 0D, 1D, 2D, 1D),
                new CraneEntryApproach.Bounds(0D, 0D, -1D, 1D, 1D, 0D),
                new CraneEntryApproach.Bounds(0D, 0D, 1D, 1D, 1D, 2D))) {
            assertFalse(outside.intersects(capture));
            assertFalse(capture.intersects(outside));
        }
        assertTrue(new CraneEntryApproach.Bounds(-2D, -2D, -2D, 0.01D, 0.01D, 0.01D).intersects(capture));
    }

    private static Optional<MovementIntent.CranePosition> motion(CraneLifecycle.ArrivalFacts facts,
            boolean riding, boolean fueled, boolean captured, MovementIntent standing, DimensionKey dimension,
            MovementPoint crane) {
        return CraneLifecycle.motion(facts, riding, fueled, captured, standing, dimension, crane, INSIDE, CAPTURE);
    }

    @Test
    void completionAndExperienceCountSuccessfulWorkOnly() {
        var work = new CraneLifecycle.Work(true, false, true, false);
        assertEquals(2, work.successes());
        assertFalse(CraneLifecycle.complete(CraneLifecycle.WaitMode.NO_WORK, work, COMPLETE, 0, 0));
        assertTrue(CraneLifecycle.complete(CraneLifecycle.WaitMode.NO_WORK, NONE, COMPLETE, 0, 0));
        assertFalse(CraneLifecycle.complete(CraneLifecycle.WaitMode.EXCESS, work, COMPLETE, 0, 0));
        assertFalse(CraneLifecycle.complete(CraneLifecycle.WaitMode.REMAIN, work, COMPLETE, 0, 0));
        assertTrue(CraneLifecycle.complete(CraneLifecycle.WaitMode.EXCESS, NONE, COMPLETE, 0, 0));
        assertFalse(CraneLifecycle.complete(CraneLifecycle.WaitMode.TIMED, NONE, COMPLETE, 15, 16));
        assertTrue(CraneLifecycle.complete(CraneLifecycle.WaitMode.TIMED, NONE, COMPLETE, 16, 16));
    }

    @Test
    void fullEmptyAndAmountModesReadTheirOwnCompletionFact() {
        for (CraneLifecycle.WaitMode mode : List.of(CraneLifecycle.WaitMode.FULL, CraneLifecycle.WaitMode.EMPTY,
                CraneLifecycle.WaitMode.EXCESS, CraneLifecycle.WaitMode.REMAIN)) {
            assertTrue(CraneLifecycle.complete(mode, NONE, COMPLETE, 0, 0));
            assertFalse(CraneLifecycle.complete(mode, NONE,
                    new CraneLifecycle.InventoryCompletion(false, false, false, false), 0, 0));
        }
    }

    @Test
    void plannerThrottlesPathsAndDoesNotOfferTeleportFallback() {
        var intent = new MovementIntent.CranePosition(DIMENSION, CRANE, 0.5D, MovementReason.CRANE_DOCK);
        MovementPoint body = new MovementPoint(30D, 5D, 3D);
        var first = CranePositionPlanner.step(CranePositionPlanner.State.INITIAL, intent, body, 100);
        var path = assertInstanceOf(MovementStep.PathTo.class, first.plan().steps().get(0));
        assertTrue(path.onFailure().isEmpty());
        assertTrue(CranePositionPlanner.step(first.state(), intent, body, 101).plan().isEmpty());
        assertTrue(CranePositionPlanner.step(first.state(), intent, body, 116).plan().hasPath());
    }

    @Test
    void plannerStopsOnlyOnceOnArrival() {
        var intent = new MovementIntent.CranePosition(DIMENSION, CRANE, 0.5D, MovementReason.CRANE_DOCK);
        var first = CranePositionPlanner.step(CranePositionPlanner.State.INITIAL, intent,
                new MovementPoint(30D, 5D, 3D), 0);
        var arrived = CranePositionPlanner.step(first.state(), intent, CRANE, 1);
        assertInstanceOf(MovementStep.Stop.class, arrived.plan().steps().get(0));
        assertTrue(CranePositionPlanner.step(arrived.state(), intent, CRANE, 2).plan().isEmpty());
    }
}
