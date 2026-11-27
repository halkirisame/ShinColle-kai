package com.lulan.shincolle.ai.domain;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AntiAirPreemptionTest {
    private static final TargetHandle SOURCE = handle(1);
    private static final TargetHandle CURRENT = handle(2);
    private static final TargetHandle AIR = handle(3);
    private static final SourceGate ANTI_AIR = new SourceGate(true, false, false, false, true);
    private static final SourceGate ORDINARY = new SourceGate(true, false, false, false);
    private static final TargetPredicatePolicy POLICY = new TargetPredicatePolicy(true, true, true, 1, true);
    private static final CurrentLockObservation NORMAL = current(TargetPriorityTier.NORMAL);

    @Test
    void heldPreemptionRespectsAcquisitionGatesAndSchedule() {
        ShipTargetAuthorityState state = held(TargetSource.AUTO);
        for (SourceGate gate : List.of(new SourceGate(true, true, false, false, true),
                new SourceGate(true, false, true, false, true))) {
            assertFalse(TargetLockReducer.autoScanDue(state, gate, 8, Optional.of(NORMAL)));
            assertEquals(state, reduce(8, state, gate, NORMAL, airScan(8)).next());
        }
        assertFalse(TargetLockReducer.autoScanDue(state, ANTI_AIR, 7, Optional.of(NORMAL)));
        assertEquals(state, reduce(7, state, ANTI_AIR, NORMAL, airScan(7)).next());
        for (TargetPriorityTier tier : List.of(TargetPriorityTier.ANTI_SUBMARINE, TargetPriorityTier.PVP_FIRST)) {
            TargetAuthorityDecision decision = reduce(8, state, ANTI_AIR, current(tier), airScan(8));
            assertEquals(AIR, decision.next().lock().orElseThrow().target());
        }
    }

    @Test
    void antiAirOffDoesNotPreemptOrScanHeldAuto() {
        ShipTargetAuthorityState state = held(TargetSource.AUTO);
        assertFalse(TargetLockReducer.autoScanDue(state, ORDINARY, 8, Optional.of(NORMAL)));
        TargetAuthorityDecision decision = reduce(8, state, ORDINARY, NORMAL, airScan(8));
        assertEquals(state, decision.next());
        assertFalse(decision.autoScanPerformed());
    }

    @Test
    void manualOwnerAssistAndRevengeAreNeverPreempted() {
        for (TargetSource source : List.of(TargetSource.MANUAL, TargetSource.OWNER_ASSIST, TargetSource.REVENGE)) {
            ShipTargetAuthorityState state = held(source);
            Optional<ManualOrderObservation> manual = source == TargetSource.MANUAL
                    ? Optional.of(new ManualOrderObservation(CURRENT, true, true)) : Optional.empty();
            Optional<OwnerAssistObservation> assist = source == TargetSource.OWNER_ASSIST
                    ? Optional.of(new OwnerAssistObservation(
                            new RecentOwnerAttack(CURRENT, 4, 1), 8, 1D, 64D, true, true, true, true))
                    : Optional.empty();
            TargetAuthorityDecision decision = TargetLockReducer.reduce(new TargetAuthorityInput(
                    8, state, ANTI_AIR, manual, Optional.empty(), Optional.of(NORMAL),
                    Optional.of(airScan(8)), assist));
            assertEquals(state, decision.next(), source.toString());
            assertFalse(decision.autoScanPerformed());
            assertFalse(TargetLockReducer.autoScanDue(state, ANTI_AIR, 8, Optional.of(NORMAL)));
        }
    }

    @Test
    void antiAirLockNeverSwitchesToAnotherAircraft() {
        ShipTargetAuthorityState state = held(TargetSource.AUTO);
        CurrentLockObservation current = current(TargetPriorityTier.ANTI_AIR);
        assertFalse(TargetLockReducer.autoScanDue(state, ANTI_AIR, 8, Optional.of(current)));
        assertEquals(state, reduce(8, state, ANTI_AIR, current, airScan(8)).next());
    }

    @Test
    void lowerPriorityCandidatesCannotPreemptAndStillAdvanceSchedule() {
        for (TargetPriorityTier tier : List.of(TargetPriorityTier.ANTI_SUBMARINE, TargetPriorityTier.PVP_FIRST)) {
            TargetCandidate candidate = candidate(AIR, tier, true);
            TargetState scan = new TargetState(8, SOURCE, List.of(candidate), Optional.of(AIR));
            TargetAuthorityDecision decision = reduce(8, held(TargetSource.AUTO), ANTI_AIR, NORMAL, scan);
            assertEquals(held(TargetSource.AUTO).lock(), decision.next().lock());
            assertTrue(decision.autoScanPerformed());
            assertEquals(16, decision.next().autoScan().nextScanTick());
        }
    }

    @Test
    void emptyHeldObservationsAdvanceAtEightTickIntervalsWithoutDraws() {
        ShipTargetAuthorityState state = held(TargetSource.AUTO);
        int observations = 0;
        for (int tick = 8; tick <= 32; tick++) {
            if (TargetLockReducer.autoScanDue(state, ANTI_AIR, tick, Optional.of(NORMAL))) {
                TargetState scan = select(tick, state, normalCandidates(), bound -> {
                    throw new AssertionError("No aircraft must mean no random draw");
                });
                TargetAuthorityDecision decision = reduce(tick, state, ANTI_AIR, NORMAL, scan);
                assertTrue(decision.autoScanPerformed());
                assertEquals(tick + 8, decision.next().autoScan().nextScanTick());
                state = decision.next();
                observations++;
            }
            assertEquals(held(TargetSource.AUTO).lock(), state.lock());
        }
        assertEquals(4, observations);
    }

    @Test
    void ineligibleAircraftCannotTriggerSelectionOfThreeNormalCandidates() {
        ShipTargetAuthorityState state = held(TargetSource.AUTO);
        List<TargetCandidate> candidates = new java.util.ArrayList<>(normalCandidates());
        candidates.add(candidate(AIR, TargetPriorityTier.ANTI_AIR, false));
        TargetState scan = select(8, state, candidates, bound -> {
            throw new AssertionError("Allied aircraft must not cause normal target reselection");
        });
        assertTrue(scan.selectedTarget().isEmpty());
        assertEquals(state.lock(), reduce(8, state, ANTI_AIR, NORMAL, scan).next().lock());
    }

    @Test
    void threeAircraftDrawOnceAndThenKeepTheirLockWithoutFurtherScans() {
        ShipTargetAuthorityState state = held(TargetSource.AUTO);
        int[] draws = {0};
        List<TargetCandidate> aircraft = List.of(
                candidate(AIR, TargetPriorityTier.ANTI_AIR, true),
                candidate(handle(4), TargetPriorityTier.ANTI_AIR, true),
                candidate(handle(5), TargetPriorityTier.ANTI_AIR, true));
        TargetState scan = select(8, state, aircraft, bound -> {
            assertEquals(3, bound);
            draws[0]++;
            return 1;
        });
        TargetAuthorityDecision decision = reduce(8, state, ANTI_AIR, NORMAL, scan);
        assertEquals(handle(4), decision.next().lock().orElseThrow().target());
        assertEquals(8, decision.next().lock().orElseThrow().acquiredAtTick());
        for (int tick = 9; tick <= 40; tick++) {
            assertFalse(TargetLockReducer.autoScanDue(
                    decision.next(), ANTI_AIR, tick, Optional.of(current(TargetPriorityTier.ANTI_AIR))));
        }
        assertEquals(1, draws[0]);
    }

    @Test
    void simultaneousRevengeWinsBeforeAnyPreemptionScan() {
        ShipTargetAuthorityState state = held(TargetSource.AUTO);
        TargetHandle attacker = handle(9);
        TargetAuthorityDecision decision = TargetLockReducer.reduce(new TargetAuthorityInput(
                8, state, ANTI_AIR, Optional.empty(),
                Optional.of(new RevengeObservation(attacker, 8, true)),
                Optional.of(NORMAL), Optional.of(airScan(8))));
        assertEquals(TargetSource.REVENGE, decision.next().lock().orElseThrow().source());
        assertEquals(attacker, decision.next().lock().orElseThrow().target());
        assertTrue(decision.consumeRevenge());
        assertFalse(decision.autoScanPerformed());
        assertFalse(TargetLockReducer.autoScanDue(decision.next(), ANTI_AIR, 8, Optional.of(NORMAL)));
    }

    @Test
    void invalidAircraftReturnsToOrdinaryAcquisitionWithoutRememberingOldTarget() {
        ShipTargetAuthorityState state = reduce(8, held(TargetSource.AUTO), ANTI_AIR, NORMAL, airScan(8)).next();
        TargetState scan = select(16, ShipTargetAuthorityState.initial(), normalCandidates(), bound -> 2);
        TargetAuthorityDecision decision = reduce(16, state, ANTI_AIR,
                new CurrentLockObservation(true, false, true, false, TargetPriorityTier.ANTI_AIR), scan);
        assertEquals(handle(8), decision.next().lock().orElseThrow().target());
        assertEquals(16, decision.next().lock().orElseThrow().acquiredAtTick());
    }

    private static TargetAuthorityDecision reduce(int tick, ShipTargetAuthorityState state, SourceGate gate,
            CurrentLockObservation current, TargetState scan) {
        return TargetLockReducer.reduce(new TargetAuthorityInput(tick, state, gate,
                Optional.empty(), Optional.empty(), Optional.of(current), Optional.of(scan)));
    }

    private static ShipTargetAuthorityState held(TargetSource source) {
        return new ShipTargetAuthorityState(Optional.of(new TargetLock(CURRENT, source, 4)),
                new TargetScanSchedule(8), 0);
    }

    private static CurrentLockObservation current(TargetPriorityTier tier) {
        return new CurrentLockObservation(true, true, true, false, tier);
    }

    private static TargetState select(int tick, ShipTargetAuthorityState state,
            List<TargetCandidate> candidates, AiRandom random) {
        return TargetCandidateSelector.selectAutomatic(tick, SOURCE, candidates,
                TargetPredicateKind.FRIENDLY_AUTOMATIC, POLICY, state.lock(), random);
    }

    private static TargetState airScan(int tick) {
        return select(tick, held(TargetSource.AUTO),
                List.of(candidate(AIR, TargetPriorityTier.ANTI_AIR, true)), bound -> {
                    throw new AssertionError("One aircraft needs no draw");
                });
    }

    private static List<TargetCandidate> normalCandidates() {
        return List.of(candidate(handle(6), TargetPriorityTier.NORMAL, true),
                candidate(handle(7), TargetPriorityTier.NORMAL, true),
                candidate(handle(8), TargetPriorityTier.NORMAL, true));
    }

    private static TargetCandidate candidate(TargetHandle handle, TargetPriorityTier tier, boolean banned) {
        Set<TargetTraitKind> traits = switch (tier) {
            case ANTI_AIR -> Set.of(TargetTraitKind.ANTI_AIR_ELIGIBLE);
            case ANTI_SUBMARINE -> Set.of(TargetTraitKind.ANTI_SUBMARINE_ELIGIBLE);
            default -> Set.of();
        };
        return new TargetCandidate(handle, new ClassifiedTargetObservation(
                new EntityClassification(true, false, false, false, false, true, true),
                new RelationClassification(false, false, banned),
                new TargetTraitClassification(tier == TargetPriorityTier.ANTI_AIR, false, false,
                        tier == TargetPriorityTier.PVP_FIRST, false, tier == TargetPriorityTier.NORMAL,
                        false, false, false, false, traits)), handle.uuid().getLeastSignificantBits());
    }

    private static TargetHandle handle(long id) {
        return new TargetHandle(new UUID(0, id), new DimensionKey("minecraft", "overworld"));
    }
}
