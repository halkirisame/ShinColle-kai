package com.lulan.shincolle.visualdata;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShipAuraTest {
    private static ShipAuraSelection.Candidate candidate(int id, double distance) {
        return new ShipAuraSelection.Candidate(id, distance * distance, true, true, false, false, true, true);
    }

    @Test
    void strict32BlockBoundaryAndEveryEligibilityFlagAreIndependent() {
        var selection = new ShipAuraSelection();
        List<ShipAuraSelection.Candidate> candidates = new ArrayList<>(List.of(
                candidate(1, 31.99), candidate(2, 32), candidate(3, 32.01), candidate(4, Double.NaN)));
        candidates.add(new ShipAuraSelection.Candidate(5, 0, false, true, false, false, true, true));
        candidates.add(new ShipAuraSelection.Candidate(6, 0, true, false, false, false, true, true));
        candidates.add(new ShipAuraSelection.Candidate(7, 0, true, true, true, false, true, true));
        candidates.add(new ShipAuraSelection.Candidate(8, 0, true, true, false, true, true, true));
        candidates.add(new ShipAuraSelection.Candidate(9, 0, true, true, false, false, false, true));
        candidates.add(new ShipAuraSelection.Candidate(10, 0, true, true, false, false, true, false));
        selection.begin(1, candidates);
        selection.complete(1);
        assertEquals(List.of(1), selection.completed());
    }

    @Test
    void allVisibleCandidatesAreSortedBeforeTaking16RegardlessOfInputOrder() {
        for (int count : new int[]{0, 1, 16, 17, 64, 128}) {
            var candidates = new ArrayList<>(IntStream.range(0, count)
                    .mapToObj(i -> candidate(i, 1 + i / 10.0)).toList());
            var expected = IntStream.range(0, Math.min(count, 16)).boxed().toList();
            for (int order = 0; order < 3; order++) {
                if (order == 1) Collections.reverse(candidates);
                if (order == 2) Collections.shuffle(candidates, new Random(27));
                var selection = new ShipAuraSelection();
                selection.begin(1, candidates);
                selection.complete(1);
                assertEquals(expected, selection.completed());
            }
        }
        var selection = new ShipAuraSelection();
        selection.begin(1, List.of(candidate(8, 1), candidate(2, 1), candidate(5, 1)));
        selection.complete(1);
        assertEquals(List.of(2, 5, 8), selection.completed());
    }

    @Test
    void frameReentryCannotAddSlotsAndOnlyCompletedFramesFeedParticles() {
        var selection = new ShipAuraSelection();
        selection.begin(1, List.of(candidate(1, 2)));
        assertTrue(selection.completed().isEmpty());
        selection.begin(1, List.of(candidate(2, 1)));
        assertTrue(selection.contains(1));
        assertFalse(selection.contains(2));
        selection.complete(1);
        selection.begin(2, List.of(candidate(2, 1)));
        assertEquals(List.of(1), selection.completed());
        selection.complete(1);
        assertEquals(List.of(1), selection.completed());
        selection.complete(2);
        assertEquals(List.of(2), selection.completed());
        selection.reset();
        assertFalse(selection.contains(2));
        assertTrue(selection.completed().isEmpty());
        selection.begin(2, List.of());
        selection.complete(2);
        assertTrue(selection.completed().isEmpty());
    }

    private record Spawn(int tick, int id) { }

    private static List<Spawn> simulate(int framesPerTick) {
        var selection = new ShipAuraSelection();
        var budget = new ShipAuraBudget();
        List<Spawn> result = new ArrayList<>();
        List<Spawn> living = new ArrayList<>();
        var candidates = IntStream.range(0, 128).mapToObj(i -> candidate(i, i / 10.0)).toList();
        for (int tick = 0; tick < 101; tick++) {
            for (int frame = 0; frame < framesPerTick; frame++) {
                long sequence = (long) tick * framesPerTick + frame;
                selection.begin(sequence, candidates);
                selection.complete(sequence);
            }
            int now = tick;
            living.removeIf(spawn -> now - spawn.tick() >= 20);
            for (int id : selection.completed()) {
                if (budget.emit(tick, id, living.size())) {
                    var spawn = new Spawn(tick, id);
                    living.add(spawn);
                    result.add(spawn);
                }
            }
            assertTrue(living.size() <= 64);
            var window = result.stream().filter(spawn -> now - spawn.tick() < 20).toList();
            assertTrue(window.size() <= 64);
            for (int id = 0; id < 16; id++) {
                int ship = id;
                assertTrue(window.stream().filter(spawn -> spawn.id() == ship).count() <= 4);
            }
        }
        return result;
    }

    @Test
    void equalTicksAt20And120FpsHaveIdenticalRollingAndLiveBudgets() {
        var slow = simulate(1);
        assertFalse(slow.isEmpty());
        assertEquals(slow, simulate(6));
        for (int id = 0; id < 16; id++) {
            int ship = id;
            var ticks = slow.stream().filter(spawn -> spawn.id() == ship).map(Spawn::tick).toList();
            for (int i = 1; i < ticks.size(); i++) assertTrue(ticks.get(i) - ticks.get(i - 1) >= 5);
        }
    }

    @Test
    void exhaustedBudgetsDiscardRatherThanBankAndResetReleasesAllHistory() {
        var budget = new ShipAuraBudget();
        assertFalse(budget.emit(0, 1, 64));
        assertTrue(budget.emit(0, 1, 63));
        assertFalse(budget.emit(0, 1, 0));
        assertFalse(budget.emit(4, 1, 0));
        assertTrue(budget.emit(5, 1, 0));
        budget.reset();
        for (int id = 0; id < 64; id++) assertTrue(budget.emit(5, id, 0));
        assertFalse(budget.emit(24, 100, 0));
        assertTrue(budget.emit(25, 100, 0));
        budget.reset();
        assertTrue(budget.emit(25, 100, 0));
        budget.advance(1000);
        assertTrue(budget.emit(1000, 100, 0));
    }
}
