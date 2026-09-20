package com.lulan.shincolle.ai.domain;

import com.lulan.shincolle.ai.observation.NearbyObservationProducer;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TargetPerceptionDomainTest {
    private static final DimensionKey OVERWORLD = new DimensionKey("minecraft", "overworld");
    private static final PerceptionFreshnessPolicy FRESHNESS =
            PerceptionFreshnessPolicy.AUTOMATIC_TARGETING;

    @Test
    void nearbyAndSnapshotDefensivelyCopyInCurrentThenProviderOrder() {
        RawEntityObservation currentRaw = raw(1);
        RawEntityObservation first = raw(2);
        RawEntityObservation second = raw(3);
        List<RawEntityObservation> mutable = new ArrayList<>(List.of(first, second));
        NearbyEntitiesObservation nearby = NearbyEntitiesObservation.observed(mutable, 10);
        mutable.clear();
        TargetClassificationObservation current = classified(currentRaw.handle(), 10, false);
        TargetClassificationObservation firstClassified = classified(first.handle(), 10, false);
        TargetClassificationObservation secondClassified = classified(second.handle(), 10, false);
        Map<TargetHandle, TargetClassificationObservation> unordered = new LinkedHashMap<>();
        unordered.put(second.handle(), secondClassified);
        unordered.put(currentRaw.handle(), current);
        unordered.put(first.handle(), firstClassified);

        ShipTargetPerceptionSnapshot snapshot = new ShipTargetPerceptionSnapshot(
                context(10), CurrentTargetRawObservation.observed(currentRaw, 10), nearby, unordered);

        assertEquals(List.of(first, second), nearby.observation().value());
        assertEquals(List.of(currentRaw.handle(), first.handle(), second.handle()),
                List.copyOf(snapshot.classifications().keySet()));
        assertThrows(UnsupportedOperationException.class,
                () -> snapshot.classifications().clear());
    }

    @Test
    void constructorsRejectNullElementsFutureTicksAndMapMismatches() {
        RawEntityObservation first = raw(1);
        List<RawEntityObservation> withNull = new ArrayList<>();
        withNull.add(null);
        assertThrows(NullPointerException.class,
                () -> NearbyEntitiesObservation.observed(withNull, 1));
        assertThrows(IllegalArgumentException.class, () -> new ShipTargetPerceptionSnapshot(
                context(10), CurrentTargetRawObservation.absent(9),
                NearbyEntitiesObservation.observed(List.of(), 10), Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new ShipTargetPerceptionSnapshot(
                context(10), CurrentTargetRawObservation.absent(10),
                NearbyEntitiesObservation.observed(List.of(), 11), Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new ShipTargetPerceptionSnapshot(
                context(10), CurrentTargetRawObservation.absent(10),
                NearbyEntitiesObservation.observed(List.of(first), 10),
                Map.of(first.handle(), classified(raw(2).handle(), 10, false))));
        assertThrows(IllegalArgumentException.class, () -> new ShipTargetPerceptionSnapshot(
                context(10), CurrentTargetRawObservation.absent(10),
                NearbyEntitiesObservation.observed(List.of(first), 10),
                Map.of(raw(2).handle(), classified(raw(2).handle(), 10, false))));
        assertThrows(IllegalArgumentException.class, () -> new ShipTargetPerceptionSnapshot(
                context(10), CurrentTargetRawObservation.absent(10),
                NearbyEntitiesObservation.observed(List.of(first), 10),
                Map.of(first.handle(), classified(first.handle(), 11, false))));
    }

    @Test
    void emptyCurrentAndEmptyNearbyRemainFreshObservations() {
        CurrentTargetRawObservation current = CurrentTargetRawObservation.absent(40);
        NearbyEntitiesObservation nearby = NearbyEntitiesObservation.observed(List.of(), 40);
        ShipTargetPerceptionSnapshot snapshot = new ShipTargetPerceptionSnapshot(
                context(40), current, nearby, Map.of());

        assertTrue(current.observation().value().isEmpty());
        assertTrue(nearby.observation().value().isEmpty());
        assertTrue(current.isFreshAt(40, FRESHNESS.maxCurrentTargetAge()));
        assertTrue(nearby.isFreshAt(48, FRESHNESS.maxCandidateAge()));
        assertTrue(snapshot.classifications().isEmpty());
    }

    @Test
    void freshnessUsesCurrentAgeZeroAndInclusiveCandidateAndLosAgeEight() {
        TargetHandle target = raw(1).handle();
        CurrentTargetRawObservation current = CurrentTargetRawObservation.absent(100);
        TargetClassificationObservation classified = classified(target, 100, true);

        assertTrue(current.isFreshAt(100, FRESHNESS.maxCurrentTargetAge()));
        assertFalse(current.isFreshAt(101, FRESHNESS.maxCurrentTargetAge()));
        assertTrue(classified.isFreshAt(108, FRESHNESS));
        assertFalse(classified.isFreshAt(109, FRESHNESS));
    }

    @Test
    void lineOfSightPresenceExactlyMatchesMeasurementRequirement() {
        TargetHandle target = raw(1).handle();
        assertTrue(TargetLineOfSightObservation.notMeasured(target).result().isEmpty());
        assertEquals(Boolean.TRUE,
                TargetLineOfSightObservation.measured(target, true, 5)
                        .result().orElseThrow().value());
        assertThrows(IllegalArgumentException.class, () -> new TargetLineOfSightObservation(
                target, false, Optional.of(new TimedObservation<>(true, 5))));
        assertThrows(IllegalArgumentException.class, () -> new TargetLineOfSightObservation(
                target, true, Optional.empty()));
    }

    @Test
    void scheduleOffsetMatchesFixedHashAlgorithmAndIsStable() {
        TargetHandle source = new TargetHandle(
                UUID.fromString("12345678-1234-5678-9abc-def012345678"), OVERWORLD);
        int dimensionHash = 31 * "minecraft".hashCode() + "overworld".hashCode();
        int expected = Math.floorMod(31 * dimensionHash + source.uuid().hashCode(), 8);

        assertEquals(expected, TargetScanSchedule.phaseOffset(source, 8));
        assertEquals(expected, TargetScanSchedule.phaseOffset(source, 8));
        assertTrue(expected >= 0 && expected <= 7);
        assertEquals(100 + expected,
                TargetScanSchedule.initial(source, 100, 8).nextScanTick());
    }

    @Test
    void missedDeadlineFiresOnFirstLaterTickWithoutModuloAlignment() {
        TargetScanSchedule schedule = new TargetScanSchedule(104);

        assertFalse(schedule.isDue(103));
        assertTrue(schedule.isDue(105));
        assertEquals(113, schedule.advanceFrom(105, 8).nextScanTick());
        assertTrue(new TargetScanSchedule(104).isDue(111));
    }

    @Test
    void nearbyProducerQueriesAtDeadlineAndAgainAtDeadlineOrStaleAgeNine() {
        TargetHandle source = handleWithOffset(3);
        AtomicInteger queries = new AtomicInteger();
        NearbyObservationProducer producer = new NearbyObservationProducer(
                source, query -> {
                    queries.incrementAndGet();
                    return List.of(raw(2));
                }, 100, 8, FRESHNESS);
        SpatialQuery query = query(source);

        assertTrue(producer.observe(query, context(102)).isEmpty());
        assertEquals(0, queries.get());
        assertTrue(producer.observe(query, context(103)).isPresent());
        assertEquals(1, queries.get());
        assertTrue(producer.observe(query, context(110)).isPresent());
        assertEquals(1, queries.get());
        assertTrue(producer.observe(query, context(111)).isPresent());
        assertEquals(2, queries.get());

        TargetHandle slowSource = handleWithOffset(10, 100);
        NearbyObservationProducer staleProducer = new NearbyObservationProducer(
                slowSource, ignored -> {
                    queries.incrementAndGet();
                    return List.of();
                }, 200, 100, FRESHNESS);
        SpatialQuery slowQuery = query(slowSource);
        staleProducer.observe(slowQuery, context(210));
        int afterInitial = queries.get();
        staleProducer.observe(slowQuery, context(219));
        assertEquals(afterInitial + 1, queries.get());
        assertThrows(IllegalArgumentException.class,
                () -> producer.observe(query(raw(99).handle()), context(120)));
    }

    @Test
    void producerInitialPhasesAreDistributedAndAdvanceFromActualScanTick() {
        List<Integer> offsets = new ArrayList<>();
        for (long id = 1; id <= 32; id++) {
            offsets.add(TargetScanSchedule.phaseOffset(
                    new TargetHandle(new UUID(0, id), OVERWORLD), 8));
        }
        assertTrue(offsets.stream().distinct().count() > 1);

        TargetHandle source = handleWithOffset(4);
        NearbyObservationProducer producer = new NearbyObservationProducer(
                source, ignored -> List.of(), 50, 8, FRESHNESS);
        producer.observe(query(source), context(57));
        assertEquals(65, producer.schedule().nextScanTick());
    }

    @Test
    void mutableProviderResultCannotChangeCachedObservation() {
        TargetHandle source = handleWithOffset(0);
        List<RawEntityObservation> providerResult = new ArrayList<>(List.of(raw(2)));
        NearbyObservationProducer producer = new NearbyObservationProducer(
                source, ignored -> providerResult, 10, 8, FRESHNESS);
        NearbyEntitiesObservation observed = producer.observe(query(source), context(10)).orElseThrow();
        providerResult.add(raw(3));

        assertEquals(1, observed.observation().value().size());
        assertSame(observed, producer.observe(query(source), context(11)).orElseThrow());
    }

    private static TargetClassificationObservation classified(
            TargetHandle handle, long tick, boolean measuredLos) {
        TargetPredicateFacts facts = new TargetPredicateFacts(
                true, true, true, false, false, false, false, false, false,
                measuredLos, true, false, false, false, false, false, true,
                true, false, false, false);
        ClassifiedTargetObservation value = ClassifiedTargetObservation.classify(
                facts, new RelationClassification(false, false, false));
        TargetLineOfSightObservation los = measuredLos
                ? TargetLineOfSightObservation.measured(handle, true, tick)
                : TargetLineOfSightObservation.notMeasured(handle);
        return new TargetClassificationObservation(
                handle, new TimedObservation<>(value, tick), los);
    }

    private static RawEntityObservation raw(long id) {
        return new RawEntityObservation(
                new TargetHandle(new UUID(0, id), OVERWORLD),
                new EntityTypeKey("minecraft", "pig"),
                RelationIdentity.unaffiliated(),
                new ObservationPosition(id, 0, 0),
                true);
    }

    private static SpatialQuery query(TargetHandle source) {
        return new SpatialQuery(source, new ObservationPosition(0, 0, 0),
                new ObservationBounds(1, 1), 16, 12);
    }

    private static AiTickContext context(long tick) {
        return new AiTickContext(tick, new AiDecisionId(tick, 0));
    }

    private static TargetHandle handleWithOffset(int expected) {
        return handleWithOffset(expected, 8);
    }

    private static TargetHandle handleWithOffset(int expected, int interval) {
        for (long id = 0; id < 10_000; id++) {
            TargetHandle candidate = new TargetHandle(new UUID(0, id), OVERWORLD);
            if (TargetScanSchedule.phaseOffset(candidate, interval) == expected) {
                return candidate;
            }
        }
        throw new AssertionError("Could not find deterministic phase fixture");
    }
}
