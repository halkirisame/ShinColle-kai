package com.lulan.shincolle.ai.domain.combat;

import com.lulan.shincolle.ai.domain.DimensionKey;
import com.lulan.shincolle.ai.domain.TargetHandle;
import com.lulan.shincolle.ai.domain.TargetSource;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CombatTimingReducerTest {
    private static final DimensionKey OVERWORLD = new DimensionKey("minecraft", "overworld");
    private static final TargetHandle A = new TargetHandle(new UUID(1L, 1L), OVERWORLD);
    private static final TargetHandle B = new TargetHandle(new UUID(2L, 2L), OVERWORLD);
    private static final CombatIntent HOLD = new CombatIntent.HoldFire(Set.of(HoldFireReason.NO_TARGET));
    private static final Set<WeaponChannel> CANNONS = EnumSet.of(WeaponChannel.LIGHT, WeaponChannel.HEAVY);

    private static CombatIntent engage(TargetHandle target) {
        return new CombatIntent.Engage(target, TargetSource.AUTO);
    }

    @Test
    void initialDelaysMatchAFreshAttackGoalCountingItsStartTick() {
        CombatTimingState s = CombatTimingReducer.initial(100);
        assertEquals(119, s.readyAt(WeaponChannel.LIGHT));
        assertEquals(139, s.readyAt(WeaponChannel.HEAVY));
        assertEquals(119, s.readyAt(WeaponChannel.AIR));
        assertEquals(119, s.readyAt(WeaponChannel.MELEE));
        assertTrue(s.nextAirHeavy(), "the carrier goal's first launch was heavy");
        assertEquals(Optional.empty(), s.aimTarget());
    }

    @Test
    void engagingKeepsCannonsBackForOneAndTwoAimTimes() {
        CombatTimingState s = CombatTimingReducer.onIntent(CombatTimingReducer.initial(0), engage(A), 100, 25);
        assertEquals(124, s.readyAt(WeaponChannel.LIGHT), "the start tick is the first of the aim time");
        assertEquals(149, s.readyAt(WeaponChannel.HEAVY));
        assertEquals(19, s.readyAt(WeaponChannel.AIR), "aircraft and melee take no aim");
        assertEquals(Optional.of(A), s.aimTarget());
        assertEquals(100, s.engagedAt());
    }

    @Test
    void aLaterReadyTickIsNotBroughtForward() {
        CombatTimingState s = CombatTimingReducer.onIntent(CombatTimingReducer.initial(100), engage(A), 100, 5);
        assertEquals(119, s.readyAt(WeaponChannel.LIGHT));
        assertEquals(139, s.readyAt(WeaponChannel.HEAVY));
    }

    @Test
    void sameTargetKeepsAimAndANewTargetRestartsIt() {
        CombatTimingState s = CombatTimingReducer.onIntent(CombatTimingReducer.initial(0), engage(A), 0, 10);
        s = CombatTimingReducer.onSight(CombatTimingReducer.onSight(s, true), true);
        assertSame(s, CombatTimingReducer.onIntent(s, engage(A), 5, 10));
        CombatTimingState switched = CombatTimingReducer.onIntent(s, engage(B), 5, 10);
        assertEquals(0, switched.aimTicks());
        assertEquals(Optional.of(B), switched.aimTarget());
        assertEquals(5, switched.engagedAt());
    }

    @Test
    void holdingFireDropsTheAimButKeepsTheTimers() {
        CombatTimingState s = CombatTimingReducer.onIntent(CombatTimingReducer.initial(0), engage(A), 0, 10);
        s = CombatTimingReducer.onSight(s, true);
        CombatTimingState held = CombatTimingReducer.onIntent(s, HOLD, 3, 10);
        assertEquals(Optional.empty(), held.aimTarget());
        assertEquals(0, held.aimTicks());
        assertEquals(s.readyAt(), held.readyAt());
    }

    @Test
    void lostSightResetsTheAim() {
        CombatTimingState s = CombatTimingReducer.onSight(CombatTimingReducer.initial(0), true);
        assertEquals(1, s.aimTicks());
        assertEquals(0, CombatTimingReducer.onSight(s, false).aimTicks());
    }

    @Test
    void firingSetsOnlyThatWeaponsReadyTick() {
        CombatTimingState s = CombatTimingReducer.onFired(CombatTimingReducer.initial(0), WeaponChannel.HEAVY, 33, 50);
        assertEquals(83, s.readyAt(WeaponChannel.HEAVY));
        assertEquals(19, s.readyAt(WeaponChannel.LIGHT));
    }

    @Test
    void aDueLaunchFlipsTheNextKind() {
        CombatTimingState s = CombatTimingReducer.initial(0);
        assertFalse(CombatTimingReducer.onAirLaunchDue(s, true).nextAirHeavy());
        assertTrue(CombatTimingReducer.onAirLaunchDue(s, false).nextAirHeavy());
    }

    @Test
    void stuckCountsFromTheLaterOfReadyAndEngaged() {
        CombatTimingState s = CombatTimingReducer.onIntent(CombatTimingReducer.initial(0), engage(A), 200, 0);
        assertFalse(CombatTimingReducer.stuck(s, CANNONS, 240, 40), "engaged 40 ticks ago");
        assertTrue(CombatTimingReducer.stuck(s, CANNONS, 241, 40));
        CombatTimingState oneFired = CombatTimingReducer.onFired(s, WeaponChannel.LIGHT, 10, 235);
        assertFalse(CombatTimingReducer.stuck(oneFired, CANNONS, 241, 40), "both cannons must be stuck");
        assertFalse(CombatTimingReducer.stuck(s, Set.of(), 1000, 40));
    }

    @Test
    void stuckResetWaitsTwentyTicksAndCannonsDropTheAim() {
        CombatTimingState s = CombatTimingReducer.onSight(CombatTimingReducer.initial(0), true);
        CombatTimingState cannons = CombatTimingReducer.onStuckReset(s, CANNONS, 300);
        assertEquals(320, cannons.readyAt(WeaponChannel.LIGHT));
        assertEquals(320, cannons.readyAt(WeaponChannel.HEAVY));
        assertEquals(0, cannons.aimTicks());
        CombatTimingState air = CombatTimingReducer.onStuckReset(s, Set.of(WeaponChannel.AIR), 300);
        assertEquals(320, air.readyAt(WeaponChannel.AIR));
        assertEquals(1, air.aimTicks());
    }

    private static WeaponReadiness readiness(boolean cannons, boolean crane) {
        return new WeaponReadiness(cannons, true, true, true, true, true, true, true, true, true, crane, true, false);
    }

    @Test
    void cannonsAimOnlyWhileTheRangeGoalCouldRun() {
        CombatLoadout all = CombatLoadout.of(WeaponChannel.values());
        assertTrue(CombatTimingReducer.cannonsCanAim(all, readiness(true, false)));
        assertTrue(CombatTimingReducer.cannonsCanAim(CombatLoadout.of(WeaponChannel.HEAVY), readiness(true, false)));
        assertFalse(CombatTimingReducer.cannonsCanAim(all, readiness(true, true)), "crane");
        assertFalse(CombatTimingReducer.cannonsCanAim(all, readiness(false, false)), "no cannon may fire");
        assertFalse(CombatTimingReducer.cannonsCanAim(CombatLoadout.of(WeaponChannel.AIR, WeaponChannel.MELEE),
                readiness(true, false)), "no cannon carried");
    }

    @Test
    void whileTheCannonsMayNotAimTheAimStaysAtZero() {
        CombatTimingState s = CombatTimingReducer.onIntent(CombatTimingReducer.initial(0), engage(A), 0, 20);
        for (int now = 0; now < 40; now++) s = CombatTimingReducer.onCannonSight(s, false, true, now, 20);
        assertEquals(0, s.aimTicks());
        assertTrue(s.cannonsIdle());
        assertEquals(Optional.of(A), s.aimTarget(), "the lock is kept");
        assertEquals(0, s.engagedAt(), "holding the cannons does not restart the engagement");
    }

    @Test
    void theTickTheCannonsMayAimAgainRestartsTheAimUnderTheStartFloors() {
        CombatTimingState s = CombatTimingReducer.onIntent(CombatTimingReducer.initial(0), engage(A), 0, 20);
        for (int now = 0; now < 40; now++) s = CombatTimingReducer.onCannonSight(s, false, true, now, 20);
        s = CombatTimingReducer.onCannonSight(s, true, true, 40, 20);
        assertEquals(1, s.aimTicks(), "the release tick counts as the first tick of sight");
        assertFalse(s.cannonsIdle());
        assertEquals(59, s.readyAt(WeaponChannel.LIGHT));
        assertEquals(79, s.readyAt(WeaponChannel.HEAVY));
        assertEquals(19, s.readyAt(WeaponChannel.AIR), "aircraft take no aim floor");
        CombatTimingState later = CombatTimingReducer.onCannonSight(s, true, true, 41, 20);
        assertEquals(2, later.aimTicks());
        assertEquals(59, later.readyAt(WeaponChannel.LIGHT), "the floor is set once");
    }

    /** The review's probe (G2-R1): on the crane from tick 0 to 39, released on tick 40. */
    @Test
    void craneReleaseDoesNotFireTheCannonsAtOnce() {
        CombatLoadout all = CombatLoadout.of(WeaponChannel.values());
        CombatTimingState s = CombatTimingReducer.initial(0);
        CombatIntent intent = engage(A);
        for (int now = 0; now <= 40; now++) {
            WeaponReadiness r = readiness(true, now < 40);
            s = CombatTimingReducer.onIntent(s, intent, now, 20);
            s = CombatTimingReducer.onCannonSight(s, CombatTimingReducer.cannonsCanAim(all, r), true, now, 20);
            AttackPlan plan = CombatDecision.decide(new CombatContext(intent, s, all, 16, 16, 100, 20, true, 20, r,
                    false, 1.0D, now));
            if (now == 40) {
                assertFalse(plan.fire().contains(WeaponChannel.LIGHT), "light fired on release, aim " + s.aimTicks());
                assertFalse(plan.fire().contains(WeaponChannel.HEAVY), "heavy fired on release, aim " + s.aimTicks());
            }
        }
    }
}
