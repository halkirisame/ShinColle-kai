package com.lulan.shincolle.ai.domain;

import com.lulan.shincolle.api.target.TargetTrait;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TargetLockReducerTest {
    private static final DimensionKey DIMENSION = new DimensionKey("minecraft", "overworld");
    private static final TargetHandle SOURCE = handle(1);
    private static final TargetHandle MANUAL = handle(2);
    private static final TargetHandle REVENGE = handle(3);
    private static final TargetHandle AUTO = handle(4);
    private static final SourceGate OPEN = new SourceGate(true, false, false, false);
    private static final CurrentLockObservation VALID_CURRENT =
            new CurrentLockObservation(true, true, true, false);

    @Test
    void manualWinsEverySourceCombinationAndKeepsMatchingAcquisitionTick() {
        for (TargetSource existingSource : TargetSource.values()) {
            TargetHandle existingTarget = existingSource == TargetSource.MANUAL ? MANUAL : AUTO;
            ShipTargetAuthorityState previous = state(existingTarget, existingSource, 7L, 0L, 4);
            TargetAuthorityDecision decision = reduce(20, previous, OPEN,
                    Optional.of(new ManualOrderObservation(MANUAL, true, true)),
                    Optional.of(new RevengeObservation(REVENGE, 9, true)),
                    Optional.of(VALID_CURRENT), Optional.of(autoState(20, AUTO)));

            assertLock(decision, MANUAL, TargetSource.MANUAL,
                    existingSource == TargetSource.MANUAL ? 7L : 20L);
            assertFalse(decision.consumeRevenge());
            assertFalse(decision.autoScanPerformed());
            assertEquals(4, decision.next().lastConsumedRevengeTick());
        }
    }

    @Test
    void deferredRevengeFiresAfterManualEnds() {
        ShipTargetAuthorityState manual = state(MANUAL, TargetSource.MANUAL, 5L, 0L, 0);
        RevengeObservation revenge = new RevengeObservation(REVENGE, 12, true);
        TargetAuthorityDecision held = reduce(10, manual, OPEN,
                Optional.of(new ManualOrderObservation(MANUAL, true, true)),
                Optional.of(revenge), Optional.of(VALID_CURRENT), Optional.empty());
        assertLock(held, MANUAL, TargetSource.MANUAL, 5L);
        assertFalse(held.consumeRevenge());

        TargetAuthorityDecision released = reduce(12, held.next(), OPEN,
                Optional.empty(), Optional.of(revenge), Optional.of(VALID_CURRENT), Optional.empty());
        assertLock(released, REVENGE, TargetSource.REVENGE, 12L);
        assertTrue(released.consumeRevenge());
        assertEquals(12, released.next().lastConsumedRevengeTick());
    }

    @Test
    void revengeWinsAutoAndDoesNotRetriggerConsumedTick() {
        ShipTargetAuthorityState previous = state(AUTO, TargetSource.AUTO, 4L, 0L, 2);
        TargetAuthorityDecision revenge = reduce(15, previous, OPEN, Optional.empty(),
                Optional.of(new RevengeObservation(REVENGE, 3, true)),
                Optional.of(VALID_CURRENT), Optional.of(autoState(15, AUTO)));
        assertLock(revenge, REVENGE, TargetSource.REVENGE, 15L);
        assertTrue(revenge.consumeRevenge());
        assertFalse(revenge.autoScanPerformed());

        TargetAuthorityDecision retained = reduce(16, revenge.next(), OPEN, Optional.empty(),
                Optional.of(new RevengeObservation(REVENGE, 3, true)),
                Optional.of(VALID_CURRENT), Optional.of(autoState(16, AUTO)));
        assertLock(retained, REVENGE, TargetSource.REVENGE, 15L);
        assertFalse(retained.consumeRevenge());
        assertFalse(retained.autoScanPerformed());
    }

    @Test
    void validRevengeLockDefersLaterRevengeUntilCurrentBecomesInvalid() {
        TargetHandle first = handle(5);
        TargetHandle second = handle(6);
        ShipTargetAuthorityState held = state(first, TargetSource.REVENGE, 10L, 18L, 10);
        RevengeObservation later = new RevengeObservation(second, 20, true);

        TargetAuthorityDecision retained = reduce(20, held, OPEN, Optional.empty(),
                Optional.of(later), Optional.of(VALID_CURRENT), Optional.empty());
        assertLock(retained, first, TargetSource.REVENGE, 10L);
        assertFalse(retained.consumeRevenge());
        assertEquals(10, retained.next().lastConsumedRevengeTick());

        CurrentLockObservation invalid = new CurrentLockObservation(true, false, true, false);
        TargetAuthorityDecision released = reduce(22, retained.next(), OPEN, Optional.empty(),
                Optional.of(later), Optional.of(invalid), Optional.empty());
        assertLock(released, second, TargetSource.REVENGE, 22L);
        assertTrue(released.consumeRevenge());
        assertEquals(20, released.next().lastConsumedRevengeTick());

        ShipTargetAuthorityState auto = state(AUTO, TargetSource.AUTO, 10L, 18L, 10);
        TargetAuthorityDecision preempted = reduce(20, auto, OPEN, Optional.empty(),
                Optional.of(later), Optional.of(VALID_CURRENT), Optional.empty());
        assertLock(preempted, second, TargetSource.REVENGE, 20L);
        assertTrue(preempted.consumeRevenge());
    }

    @Test
    void currentRevengeAndAutoLocksSuppressScanning() {
        for (TargetSource source : List.of(TargetSource.REVENGE, TargetSource.AUTO)) {
            ShipTargetAuthorityState previous = state(
                    source == TargetSource.REVENGE ? REVENGE : AUTO, source, 6L, 0L, 0);
            TargetAuthorityDecision decision = reduce(20, previous, OPEN,
                    Optional.empty(), Optional.empty(), Optional.of(VALID_CURRENT),
                    Optional.of(autoState(20, MANUAL)));
            assertEquals(previous.lock(), decision.next().lock());
            assertFalse(decision.autoScanPerformed());
            assertEquals(0L, decision.next().autoScan().nextScanTick());
        }
    }

    @Test
    void nonLivingAutoLockContinuesWhileAliveAndDropsWhenDestroyed() {
        ShipTargetAuthorityState previous = state(AUTO, TargetSource.AUTO, 10L, 30L, 0);
        CurrentLockObservation alive = new CurrentLockObservation(true, true, true, false);
        TargetAuthorityDecision held = reduce(20, previous, OPEN, Optional.empty(), Optional.empty(),
                Optional.of(alive), Optional.empty());
        assertLock(held, AUTO, TargetSource.AUTO, 10L);

        CurrentLockObservation destroyed = new CurrentLockObservation(true, false, true, false);
        TargetAuthorityDecision released = reduce(22, held.next(), OPEN, Optional.empty(), Optional.empty(),
                Optional.of(destroyed), Optional.empty());
        assertTrue(released.next().lock().isEmpty());
    }

    @Test
    void everyInvalidContinuationFlagDropsRevengeAndAuto() {
        List<CurrentLockObservation> invalid = List.of(
                new CurrentLockObservation(false, true, true, false),
                new CurrentLockObservation(true, false, true, false),
                new CurrentLockObservation(true, true, false, false),
                new CurrentLockObservation(true, true, true, true));
        for (TargetSource source : List.of(TargetSource.REVENGE, TargetSource.AUTO)) {
            for (CurrentLockObservation observation : invalid) {
                TargetAuthorityDecision decision = reduce(1,
                        state(source == TargetSource.REVENGE ? REVENGE : AUTO, source, 0L, 8L, 0),
                        OPEN, Optional.empty(), Optional.empty(), Optional.of(observation), Optional.empty());
                assertTrue(decision.next().lock().isEmpty(), source + " retained invalid current lock");
                assertFalse(decision.autoScanPerformed());
            }
        }
    }

    @Test
    void autoScansOnlyWhenDueAndAdvancesEightTicks() {
        ShipTargetAuthorityState beforeDue = new ShipTargetAuthorityState(
                Optional.empty(), new TargetScanSchedule(10L), 0);
        assertFalse(TargetLockReducer.autoScanDue(beforeDue, OPEN, 9));
        TargetAuthorityDecision early = reduce(9, beforeDue, OPEN, Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.of(autoState(9, AUTO)));
        assertTrue(early.next().lock().isEmpty());
        assertFalse(early.autoScanPerformed());

        assertTrue(TargetLockReducer.autoScanDue(beforeDue, OPEN, 10));
        TargetAuthorityDecision due = reduce(10, beforeDue, OPEN, Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.of(autoState(10, AUTO)));
        assertLock(due, AUTO, TargetSource.AUTO, 10L);
        assertTrue(due.autoScanPerformed());
        assertEquals(18L, due.next().autoScan().nextScanTick());
    }

    @Test
    void emptyAutoResultStillAdvancesSchedule() {
        ShipTargetAuthorityState previous = ShipTargetAuthorityState.initial();
        TargetAuthorityDecision decision = reduce(4, previous, OPEN, Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.of(TargetState.empty(4L, SOURCE)));
        assertTrue(decision.next().lock().isEmpty());
        assertTrue(decision.autoScanPerformed());
        assertEquals(12L, decision.next().autoScan().nextScanTick());
    }

    @Test
    void sittingBlocksManualAndAutoButNotRevenge() {
        SourceGate sitting = new SourceGate(true, true, false, false);
        TargetAuthorityDecision manual = reduce(2, ShipTargetAuthorityState.initial(), sitting,
                Optional.of(new ManualOrderObservation(MANUAL, true, true)), Optional.empty(),
                Optional.empty(), Optional.of(autoState(2, AUTO)));
        assertTrue(manual.next().lock().isEmpty());
        assertFalse(manual.autoScanPerformed());

        TargetAuthorityDecision revenge = reduce(2, ShipTargetAuthorityState.initial(), sitting,
                Optional.empty(), Optional.of(new RevengeObservation(REVENGE, 2, true)),
                Optional.empty(), Optional.of(autoState(2, AUTO)));
        assertLock(revenge, REVENGE, TargetSource.REVENGE, 2L);
    }

    @Test
    void craneBlocksOnlyAutoAndNoFuelBlocksOnlyManual() {
        SourceGate crane = new SourceGate(true, false, true, false);
        TargetAuthorityDecision revengeAtCrane = reduce(3, ShipTargetAuthorityState.initial(), crane,
                Optional.empty(), Optional.of(new RevengeObservation(REVENGE, 3, true)),
                Optional.empty(), Optional.of(autoState(3, AUTO)));
        assertLock(revengeAtCrane, REVENGE, TargetSource.REVENGE, 3L);

        SourceGate noFuel = new SourceGate(true, false, false, true);
        TargetAuthorityDecision autoWithoutFuel = reduce(3, ShipTargetAuthorityState.initial(), noFuel,
                Optional.of(new ManualOrderObservation(MANUAL, true, true)), Optional.empty(),
                Optional.empty(), Optional.of(autoState(3, AUTO)));
        assertLock(autoWithoutFuel, AUTO, TargetSource.AUTO, 3L);
        assertTrue(autoWithoutFuel.autoScanPerformed());
    }

    @Test
    void craneKeepsAnAlreadyHeldAutoLock() {
        SourceGate crane = new SourceGate(true, false, true, false);
        ShipTargetAuthorityState previous = state(AUTO, TargetSource.AUTO, 1L, 0L, 0);
        TargetAuthorityDecision decision = reduce(2, previous, crane, Optional.empty(), Optional.empty(),
                Optional.of(VALID_CURRENT), Optional.empty());
        assertLock(decision, AUTO, TargetSource.AUTO, 1L);
        assertTrue(!decision.autoScanPerformed());
    }

    @Test
    void disabledAutoNeverScansAndDropsAnExistingAutoLock() {
        SourceGate disabled = new SourceGate(false, false, false, false);
        ShipTargetAuthorityState previous = state(AUTO, TargetSource.AUTO, 1L, 0L, 0);
        TargetAuthorityDecision decision = reduce(2, previous, disabled, Optional.empty(), Optional.empty(),
                Optional.of(VALID_CURRENT), Optional.of(autoState(2, MANUAL)));
        assertTrue(decision.next().lock().isEmpty());
        assertFalse(decision.autoScanPerformed());
        assertFalse(TargetLockReducer.autoScanDue(ShipTargetAuthorityState.initial(), disabled, 2));
    }

    @Test
    void rejectedRevengeFallsThroughToAuto() {
        TargetAuthorityDecision decision = reduce(5, ShipTargetAuthorityState.initial(), OPEN,
                Optional.empty(), Optional.of(new RevengeObservation(REVENGE, 5, false)),
                Optional.empty(), Optional.of(autoState(5, AUTO)));
        assertLock(decision, AUTO, TargetSource.AUTO, 5L);
        assertFalse(decision.consumeRevenge());
        assertTrue(decision.autoScanPerformed());
    }

    private static TargetAuthorityDecision reduce(
            int tick,
            ShipTargetAuthorityState previous,
            SourceGate gate,
            Optional<ManualOrderObservation> manual,
            Optional<RevengeObservation> revenge,
            Optional<CurrentLockObservation> current,
            Optional<TargetState> autoScan) {
        return TargetLockReducer.reduce(new TargetAuthorityInput(
                tick, previous, gate, manual, revenge, current, autoScan));
    }

    private static ShipTargetAuthorityState state(
            TargetHandle target, TargetSource source, long acquiredAt, long nextScan, int consumedRevenge) {
        return new ShipTargetAuthorityState(
                Optional.of(new TargetLock(target, source, acquiredAt)),
                new TargetScanSchedule(nextScan), consumedRevenge);
    }

    private static TargetState autoState(long tick, TargetHandle selected) {
        ClassifiedTargetObservation observation = new ClassifiedTargetObservation(
                new EntityClassification(true, false, false, false, false, true, true),
                new RelationClassification(false, false, false),
                new TargetTraitClassification(false, false, false, false, false,
                        false, false, false, false, false, Set.<TargetTrait>of()));
        TargetCandidate candidate = new TargetCandidate(selected, observation, 1D);
        return new TargetState(tick, SOURCE, List.of(candidate), Optional.of(selected));
    }

    private static void assertLock(
            TargetAuthorityDecision decision, TargetHandle target, TargetSource source, long acquiredAt) {
        assertEquals(Optional.of(new TargetLock(target, source, acquiredAt)), decision.next().lock());
    }

    private static TargetHandle handle(long value) {
        return new TargetHandle(new UUID(0L, value), DIMENSION);
    }
}
