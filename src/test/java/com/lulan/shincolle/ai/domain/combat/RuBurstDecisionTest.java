package com.lulan.shincolle.ai.domain.combat;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuBurstDecisionTest {
    private static final RuBurstDecision.Permission ALLOWED = new RuBurstDecision.Permission(false, false, true, false, false, false, true);
    private static final RuBurstState.Aim AIM = new RuBurstState.Aim(-7, 9, 12);

    @Test
    void ruBurstUsesAbsoluteFourTickBoundaries() {
        for (int level : new int[]{1, 100}) {
            for (int start = 100; start < 104; start++) {
                RuBurstState state = RuBurstDecision.start(RuBurstState.initial(), level, AIM, ALLOWED, true).state();
                List<Integer> ticks = new ArrayList<>();
                for (int now = start; now <= 140; now++) {
                    var plan = RuBurstDecision.tick(state, now, ALLOWED, true);
                    if (plan.shot().isPresent()) ticks.add(now);
                    state = plan.state();
                    assertTrue(RuBurstDecision.tick(state, now, ALLOWED, true).effects().isEmpty());
                }
                int first = start == 100 ? 100 : 104;
                int count = level == 1 ? 5 : 8;
                assertEquals(java.util.stream.IntStream.range(0, count).map(i -> first + 4 * i).boxed().toList(), ticks);
                assertFalse(state.busy());
            }
        }
    }

    @Test
    void finalEmissionThenSameTickRestartCannotEmitTwice() {
        RuBurstState state = new RuBurstState(new RuBurstState.Burst(1, AIM), java.util.OptionalInt.empty());
        var finalShot = RuBurstDecision.tick(state, 104, ALLOWED, true);
        assertEquals(Set.of(RuBurstDecision.Effect.EMIT, RuBurstDecision.Effect.FINISH), finalShot.effects());
        state = RuBurstDecision.start(finalShot.state(), 1, AIM, ALLOWED, true).state();
        assertTrue(RuBurstDecision.tick(state, 104, ALLOWED, true).shot().isEmpty());
        assertTrue(RuBurstDecision.tick(state, 108, ALLOWED, true).shot().isPresent());
        var idleEvaluated = RuBurstDecision.tick(RuBurstState.initial(), 100, ALLOWED, true).state();
        state = RuBurstDecision.start(idleEvaluated, 1, AIM, ALLOWED, true).state();
        assertTrue(RuBurstDecision.tick(state, 100, ALLOWED, true).shot().isEmpty());
    }

    @Test
    void skippedTicksDoNotCatchUpOrAddChargeTime() {
        var state = RuBurstDecision.start(RuBurstState.initial(), 1, AIM, ALLOWED, true).state();
        var skipped = RuBurstDecision.tick(state, 120, ALLOWED, true);
        assertEquals(4, ((RuBurstState.Burst) skipped.state().phase()).remaining());
        assertEquals(Set.of(RuBurstDecision.Effect.EMIT), skipped.effects());
    }

    @Test
    void ruBurstRejectsBusyAndInsufficientAmmoWithoutEffects() {
        var state = RuBurstDecision.start(RuBurstState.initial(), 1, AIM, ALLOWED, true).state();
        for (RuBurstState.Aim other : List.of(AIM, new RuBurstState.Aim(1, 2, 3))) {
            var rejected = RuBurstDecision.start(state, 100, other, ALLOWED, true);
            assertEquals(Set.of(RuBurstDecision.Effect.REJECT), rejected.effects());
            assertEquals(Set.of(RuBurstDecision.Reason.BUSY), rejected.reasons());
            assertSame(state, rejected.state());
        }
        var rejected = RuBurstDecision.start(RuBurstState.initial(), 1, AIM, ALLOWED, false);
        assertFalse(rejected.state().busy());
        assertEquals(Set.of(RuBurstDecision.Reason.NO_AMMO), rejected.reasons());
        assertFalse(rejected.effects().contains(RuBurstDecision.Effect.START));
    }

    @Test
    void ruBurstCancelsWithoutResume() {
        var state = RuBurstDecision.start(RuBurstState.initial(), 1, AIM, ALLOWED, true).state();
        List<RuBurstDecision.Permission> forbidden = List.of(
                new RuBurstDecision.Permission(true, false, true, false, false, false, true),
                new RuBurstDecision.Permission(false, true, true, false, false, false, true),
                new RuBurstDecision.Permission(false, false, false, false, false, false, true),
                new RuBurstDecision.Permission(false, false, true, true, false, false, true),
                new RuBurstDecision.Permission(false, false, true, false, true, false, true),
                new RuBurstDecision.Permission(false, false, true, false, false, true, true),
                new RuBurstDecision.Permission(false, false, true, false, false, false, false));
        for (var permission : forbidden) {
            var cancelled = RuBurstDecision.tick(state, 104, permission, true);
            assertEquals(Set.of(RuBurstDecision.Effect.CANCEL), cancelled.effects());
            assertEquals(permission.reasons(), cancelled.reasons());
            assertTrue(cancelled.shot().isEmpty());
            assertFalse(RuBurstDecision.tick(cancelled.state(), 108, ALLOWED, true).state().busy());
            assertEquals(Set.of(RuBurstDecision.Effect.REJECT), RuBurstDecision.start(RuBurstState.initial(), 1, AIM, permission, true).effects());
        }
        var dimension = RuBurstDecision.tick(state, 104, ALLOWED, false);
        assertEquals(Set.of(RuBurstDecision.Reason.DIMENSION_CHANGED), dimension.reasons());
        assertFalse(dimension.state().busy());
    }

    @Test
    void ruBurstKeepsFixedAimButSamplesCurrentShotDamage() {
        var state = RuBurstDecision.start(RuBurstState.initial(), 100, AIM, ALLOWED, true).state();
        for (int now = 104; now <= 132; now += 4) {
            var plan = RuBurstDecision.tick(state, now, ALLOWED, true);
            assertEquals(AIM, plan.shot().orElseThrow());
            state = plan.state();
        }
        assertFalse(state.busy());
        // Neither start nor tick carries a target, damage, equipment, LOS or lock snapshot.
        assertEquals(List.of("remaining", "fixedAim"), java.util.Arrays.stream(RuBurstState.Burst.class.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName).toList());
    }
}
