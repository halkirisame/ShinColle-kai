package com.lulan.shincolle.ai.domain;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OwnerAssistTest {
    private static final DimensionKey DIMENSION = new DimensionKey("minecraft", "overworld");
    private static final TargetHandle A = new TargetHandle(new UUID(0L, 1L), DIMENSION);
    private static final TargetHandle B = new TargetHandle(new UUID(0L, 2L), DIMENSION);
    private static final SourceGate OPEN = new SourceGate(true, false, false, false);

    @Test
    void assistPreemptsLivingRevengeWithoutConsumingQueue() {
        TargetAuthorityDecision decision = reduce(state(A, TargetSource.REVENGE), Optional.empty(),
                Optional.of(observe(100, 0D, 20D)));
        assertEquals(TargetSource.OWNER_ASSIST, decision.next().lock().orElseThrow().source());
        assertEquals(B, decision.next().lock().orElseThrow().target());
        assertFalse(decision.consumeRevenge());
        assertEquals(4, decision.next().lastConsumedRevengeTick());
    }

    @Test
    void manualWinsAndReleaseReadsTheLatestAssist() {
        TargetAuthorityDecision manual = reduce(state(A, TargetSource.MANUAL),
                Optional.of(new ManualOrderObservation(A, true, true)), Optional.of(observe(100, 0D, 20D)));
        assertEquals(A, manual.next().lock().orElseThrow().target());
        assertFalse(manual.consumeRevenge());
        TargetAuthorityDecision released = reduce(manual.next(), Optional.empty(), Optional.of(observe(100, 0D, 20D)));
        assertEquals(TargetSource.OWNER_ASSIST, released.next().lock().orElseThrow().source());
        assertEquals(B, released.next().lock().orElseThrow().target());
        assertFalse(released.consumeRevenge());
    }

    @Test
    void assistExpiryReleasesPriorityAndResumesRevengeRatherThanRestoringAStack() {
        TargetAuthorityDecision expired = reduce(state(B, TargetSource.OWNER_ASSIST), Optional.empty(),
                Optional.of(observe(301, 0D, 20D)));
        assertEquals(TargetSource.REVENGE, expired.next().lock().orElseThrow().source());
        assertEquals(A, expired.next().lock().orElseThrow().target());
        assertTrue(expired.consumeRevenge());
    }

    @Test
    void assistOverridesAutoAndAcceptsPassiveButNotActionBlocks() {
        for (boolean auto : new boolean[]{true, false}) {
            TargetAuthorityDecision decision = TargetLockReducer.reduce(new TargetAuthorityInput(120,
                    state(A, TargetSource.AUTO), new SourceGate(auto, false, false, false), Optional.empty(),
                    Optional.empty(), Optional.of(new CurrentLockObservation(true, true, true, false)),
                    Optional.empty(), Optional.of(observe(120, 0D, 20D))));
            assertEquals(TargetSource.OWNER_ASSIST, decision.next().lock().orElseThrow().source());
        }
        OwnerAssistObservation blocked = new OwnerAssistObservation(attack(B, 100, 1), 100, 0D, 20D,
                true, true, true, false);
        assertEquals(Set.of(OwnerAssistRejection.ACTION_BLOCKED), blocked.rejections());
        assertEquals(TargetSource.REVENGE, reduce(state(A, TargetSource.REVENGE), Optional.empty(),
                Optional.of(blocked)).next().lock().orElseThrow().source());
    }

    @Test
    void ageUsesInclusiveZeroAndTwoHundredAndDoesNotRenew() {
        for (long now : new long[]{100, 300}) assertTrue(observe(now, 0D, 20D).valid());
        for (long now : new long[]{99, 301}) assertFalse(observe(now, 0D, 20D).valid());
        assertEquals(200L, observe(300, 0D, 20D).age());
        assertEquals(100L, observe(300, 0D, 20D).attack().occurredAt());
    }

    @Test
    void distanceUsesInclusiveThreeDimensionalRadiusAndExistingLongerRange() {
        for (double distance : new double[]{63.99D, 64D}) assertTrue(observe(100, distance * distance, 20D).valid());
        assertFalse(observe(100, 64.01D * 64.01D, 20D).valid());
        assertTrue(observe(100, 80D * 80D, 80D).valid());
        assertFalse(observe(100, 80.01D * 80.01D, 80D).valid());
        assertFalse(observe(100, Double.NaN, 20D).valid());
        assertFalse(observe(100, 64D * 64D + 1D, 20D).valid());
    }

    @Test
    void missingOwnerOtherDimensionAndIneligibleTargetAreExplainable() {
        OwnerAssistObservation rejected = new OwnerAssistObservation(attack(B, 100, 1), 100, 0D, 20D,
                false, false, false, true);
        assertEquals(Set.of(OwnerAssistRejection.OWNER_UNAVAILABLE, OwnerAssistRejection.DIMENSION,
                OwnerAssistRejection.TARGET_INELIGIBLE), rejected.rejections());
    }

    @Test
    void lastSuccessfulSequenceWinsAndLaterFailuresCannotEraseIt() {
        RecentOwnerAttack first = attack(A, 100, 1);
        RecentOwnerAttack second = attack(B, 100, 2);
        assertEquals(second, RecentOwnerAttack.confirm(Optional.of(first), second, 1F, false).orElseThrow());
        for (float amount : new float[]{0F, -1F, Float.NaN, Float.POSITIVE_INFINITY}) {
            assertEquals(Optional.of(first), RecentOwnerAttack.confirm(Optional.of(first), second, amount, false));
        }
        assertEquals(Optional.of(first), RecentOwnerAttack.confirm(Optional.of(first), second, 1F, true));
        assertEquals(Optional.of(second), RecentOwnerAttack.confirm(Optional.of(second), first, 1F, false));
    }

    @Test
    void aircraftSelectsOnlyManualThenAssistAndOtherwiseLeavesNormalRetentionAlone() {
        Optional<OwnerAssistObservation> assist = Optional.of(observe(100, 0D, 20D));
        Optional<TargetLock> manual = state(A, TargetSource.MANUAL).lock();
        assertEquals(A, AircraftTargetPolicy.select(true, manual, assist).orElseThrow().target());
        assertEquals(B, AircraftTargetPolicy.select(true, state(A, TargetSource.AUTO).lock(), assist)
                .orElseThrow().target());
        assertTrue(AircraftTargetPolicy.select(true, state(A, TargetSource.REVENGE).lock(), Optional.empty()).isEmpty());
        assertTrue(AircraftTargetPolicy.select(true, Optional.empty(), Optional.of(observe(301, 0D, 20D))).isEmpty());
        assertTrue(AircraftTargetPolicy.select(false, manual, assist).isEmpty());
    }

    @Test
    void priorityMatrixCoversEveryPriorSourceWithoutRenewingMatchingLocks() {
        for (TargetSource source : TargetSource.values()) {
            ShipTargetAuthorityState previous = state(B, source);
            TargetAuthorityDecision manual = reduce(previous, Optional.of(new ManualOrderObservation(A, true, true)),
                    Optional.of(observe(100, 0D, 20D)));
            assertEquals(TargetSource.MANUAL, manual.next().lock().orElseThrow().source());
            TargetAuthorityDecision assist = reduce(previous, Optional.empty(), Optional.of(observe(100, 0D, 20D)));
            assertEquals(TargetSource.OWNER_ASSIST, assist.next().lock().orElseThrow().source());
            assertFalse(assist.consumeRevenge());
            if (source == TargetSource.OWNER_ASSIST) assertEquals(10L, assist.next().lock().orElseThrow().acquiredAtTick());
        }
    }

    @Test
    void expiredAssistFallsBackToOrdinaryAutoRatherThanRetainingItsPriority() {
        ClassifiedTargetObservation facts = new ClassifiedTargetObservation(
                new EntityClassification(true, false, false, false, false, true, true),
                new RelationClassification(false, false, false),
                new TargetTraitClassification(false, false, false, false, false,
                        false, false, false, false, false, Set.of()));
        TargetState scan = new TargetState(120, B, List.of(new TargetCandidate(A, facts, 1D)), Optional.of(A));
        TargetAuthorityDecision decision = TargetLockReducer.reduce(new TargetAuthorityInput(120,
                state(B, TargetSource.OWNER_ASSIST), OPEN, Optional.empty(), Optional.empty(),
                Optional.of(new CurrentLockObservation(true, true, true, false)), Optional.of(scan),
                Optional.of(observe(301, 0D, 20D))));
        assertEquals(TargetSource.AUTO, decision.next().lock().orElseThrow().source());
        assertEquals(A, decision.next().lock().orElseThrow().target());
        assertTrue(decision.autoScanPerformed());
    }

    @Test
    void sourceGateCannotBeBypassedByAnOtherwiseValidAssistObservation() {
        for (SourceGate gate : List.of(new SourceGate(true, true, false, false),
                new SourceGate(true, false, true, false), new SourceGate(true, false, false, true))) {
            TargetAuthorityDecision decision = TargetLockReducer.reduce(new TargetAuthorityInput(120,
                    ShipTargetAuthorityState.initial(), gate, Optional.empty(), Optional.empty(), Optional.empty(),
                    Optional.empty(), Optional.of(observe(120, 0D, 20D))));
            assertTrue(decision.next().lock().isEmpty());
        }
    }

    @Test
    void consumersCannotSeeExpiredOrSupersededAssistBetweenScheduledReducerTicks() {
        Optional<TargetLock> lock = state(B, TargetSource.OWNER_ASSIST).lock();
        assertEquals(lock, TargetLockReducer.projectCurrent(lock, Optional.of(observe(300, 0D, 20D))));
        assertTrue(TargetLockReducer.projectCurrent(lock, Optional.of(observe(301, 0D, 20D))).isEmpty());
        assertTrue(TargetLockReducer.projectCurrent(lock, Optional.empty()).isEmpty());
        assertTrue(TargetLockReducer.projectCurrent(state(A, TargetSource.OWNER_ASSIST).lock(),
                Optional.of(observe(100, 0D, 20D))).isEmpty());
        Optional<TargetLock> ordinary = state(A, TargetSource.REVENGE).lock();
        assertEquals(ordinary, TargetLockReducer.projectCurrent(ordinary, Optional.empty()));
    }

    private static TargetAuthorityDecision reduce(ShipTargetAuthorityState state,
            Optional<ManualOrderObservation> manual, Optional<OwnerAssistObservation> assist) {
        return TargetLockReducer.reduce(new TargetAuthorityInput(120, state, OPEN, manual,
                Optional.of(new RevengeObservation(A, 10, true)),
                Optional.of(new CurrentLockObservation(true, true, true, false)), Optional.empty(), assist));
    }

    private static ShipTargetAuthorityState state(TargetHandle target, TargetSource source) {
        return new ShipTargetAuthorityState(Optional.of(new TargetLock(target, source, 10)), new TargetScanSchedule(0), 4);
    }

    private static RecentOwnerAttack attack(TargetHandle target, long tick, long sequence) {
        return new RecentOwnerAttack(target, tick, sequence);
    }

    private static OwnerAssistObservation observe(long now, double distanceSquared, double range) {
        return new OwnerAssistObservation(attack(B, 100, 1), now, distanceSquared, range, true, true, true, true);
    }
}
