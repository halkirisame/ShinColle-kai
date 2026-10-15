package com.lulan.shincolle.ai.domain.movement;

import com.lulan.shincolle.ai.domain.DimensionKey;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.OptionalLong;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DepartureRosterTest {
    private static final DimensionKey OVERWORLD = new DimensionKey("minecraft", "overworld");
    private static final DimensionKey NETHER = new DimensionKey("minecraft", "the_nether");

    private static DepartureRoster.Entry entry(long at) {
        return DepartureRoster.Entry.departed(UUID.randomUUID(), 7, OVERWORLD, 3, 4, at);
    }

    @Test
    void firstDepartureOfAShipIsKept() {
        var first = entry(10);
        var again = new DepartureRoster.Entry(first.ship(), 7, OVERWORLD, 99, 99, 500, OptionalLong.empty(), OptionalLong.empty());
        var roster = DepartureRoster.record(DepartureRoster.record(List.of(), first), again);
        assertEquals(List.of(first), roster);
    }

    @Test
    void theOldestEntriesGoPastTheLimit() {
        List<DepartureRoster.Entry> roster = List.of();
        var oldest = entry(0);
        roster = DepartureRoster.record(roster, oldest);
        for (int i = 0; i < DepartureRoster.MAX_ENTRIES; i++) roster = DepartureRoster.record(roster, entry(i + 1));
        assertEquals(DepartureRoster.MAX_ENTRIES, roster.size());
        assertEquals(false, roster.contains(oldest));
    }

    @Test
    void anUnloadedFollowerIsLoadedOnceThenWaitedOnThenDropped() {
        var entry = entry(0);
        assertEquals(DepartureRoster.Step.START_LOAD,
                DepartureRoster.decide(entry, DepartureRoster.Found.NOT_LOADED, 100, OVERWORLD));
        var loading = entry.loadStarted(100);
        assertEquals(DepartureRoster.Step.WAIT,
                DepartureRoster.decide(loading, DepartureRoster.Found.NOT_LOADED, 299, OVERWORLD));
        assertEquals(DepartureRoster.Step.DROP,
                DepartureRoster.decide(loading, DepartureRoster.Found.NOT_LOADED, 300, OVERWORLD));
    }

    @Test
    void aFarFollowerIsRequestedAgainOnlyAfterTheRequestLapses() {
        var entry = entry(0);
        assertEquals(DepartureRoster.Step.REQUEST, DepartureRoster.decide(entry, DepartureRoster.Found.FAR, 100, OVERWORLD));
        var requested = entry.requested(5, 6, 100);
        assertEquals(5, requested.chunkX());
        assertEquals(OptionalLong.empty(), requested.loadStartedAt());
        assertEquals(OptionalLong.of(100), requested.requestedAt());
        assertEquals(OptionalLong.of(100), entry.loadStarted(100).loadStartedAt());
        assertEquals(OptionalLong.empty(), entry.requestedAt());
        assertEquals(DepartureRoster.Step.WAIT, DepartureRoster.decide(requested, DepartureRoster.Found.FAR, 499, OVERWORLD));
        assertEquals(DepartureRoster.Step.REQUEST, DepartureRoster.decide(requested, DepartureRoster.Found.FAR, 500, OVERWORLD));
    }

    @Test
    void seenInMovesOnlyTheChunkAndStaleCoversDimensionAndLifetime() {
        var entry = entry(0).loadStarted(50);
        var moved = entry.seenIn(8, 9);
        assertEquals(8, moved.chunkX());
        assertEquals(9, moved.chunkZ());
        assertEquals(entry.loadStartedAt(), moved.loadStartedAt());
        assertEquals(entry.recordedAt(), moved.recordedAt());
        assertEquals(false, DepartureRoster.stale(entry, DepartureRoster.LIFETIME_TICKS - 1, OVERWORLD));
        assertEquals(true, DepartureRoster.stale(entry, DepartureRoster.LIFETIME_TICKS, OVERWORLD));
        assertEquals(true, DepartureRoster.stale(entry, 1, NETHER));
    }

    @Test
    void aFollowerWithARequestAlreadyWaitingIsNotAskedAgain() {
        assertEquals(DepartureRoster.Step.WAIT,
                DepartureRoster.decide(entry(0), DepartureRoster.Found.REQUESTED, 100, OVERWORLD));
    }

    @Test
    void nearIneligibleOtherDimensionAndExpiredEntriesAreDropped() {
        var entry = entry(0);
        assertEquals(DepartureRoster.Step.DROP, DepartureRoster.decide(entry, DepartureRoster.Found.NEAR, 100, OVERWORLD));
        assertEquals(DepartureRoster.Step.DROP, DepartureRoster.decide(entry, DepartureRoster.Found.INELIGIBLE, 100, OVERWORLD));
        assertEquals(DepartureRoster.Step.DROP, DepartureRoster.decide(entry, DepartureRoster.Found.FAR, 100, NETHER));
        assertEquals(DepartureRoster.Step.REQUEST,
                DepartureRoster.decide(entry, DepartureRoster.Found.FAR, DepartureRoster.LIFETIME_TICKS - 1, OVERWORLD));
        assertEquals(DepartureRoster.Step.DROP,
                DepartureRoster.decide(entry, DepartureRoster.Found.FAR, DepartureRoster.LIFETIME_TICKS, OVERWORLD));
    }
}
