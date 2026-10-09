package com.lulan.shincolle.ai.domain.combat;

import com.lulan.shincolle.ai.domain.DimensionKey;
import com.lulan.shincolle.ai.domain.TargetHandle;
import com.lulan.shincolle.ai.domain.TargetSource;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CombatDecisionTest {
    private static final TargetHandle TARGET =
            new TargetHandle(new UUID(1L, 2L), new DimensionKey("minecraft", "overworld"));
    private static final CombatIntent ENGAGE = new CombatIntent.Engage(TARGET, TargetSource.AUTO);
    private static final CombatLoadout ALL = CombatLoadout.of(WeaponChannel.values());
    private static final WeaponReadiness READY =
            new WeaponReadiness(true, true, true, true, true, true, true, true, true, true, false, true, false);
    private static final int AIM = 20;
    private static final int NOW = 1000;

    /** Engaged, aimed, every weapon ready, target 4 blocks away in a 10 block range. */
    private static Ctx ctx() {
        return new Ctx();
    }

    private static final class Ctx {
        CombatIntent intent = ENGAGE;
        CombatTimingState timing = aimed(CombatTimingReducer.initial(0), AIM);
        CombatLoadout loadout = ALL;
        double distanceSq = 16;
        double meleeDistanceSq = 16;
        double rangeSq = 100;
        double reachSq = 20;
        boolean onSight = true;
        WeaponReadiness readiness = READY;
        boolean useMelee = false;
        double engage = 1.0D;

        AttackPlan decide() {
            return CombatDecision.decide(new CombatContext(intent, timing, loadout, distanceSq, meleeDistanceSq,
                    rangeSq, reachSq, onSight, AIM, readiness, useMelee, engage, NOW));
        }
    }

    private static CombatTimingState aimed(CombatTimingState s, int ticks) {
        for (int i = 0; i < ticks; i++) s = CombatTimingReducer.onSight(s, true);
        return s;
    }

    private static WeaponReadiness readiness(boolean cannons, boolean useLight, boolean ammoLight,
                                             boolean aircraft, boolean useAirLight, boolean useAirHeavy,
                                             boolean airLight, boolean airHeavy, boolean crane,
                                             boolean melee, boolean passenger) {
        return new WeaponReadiness(cannons, useLight, ammoLight, true, true, aircraft, useAirLight, useAirHeavy,
                airLight, airHeavy, crane, melee, passenger);
    }

    @Test
    void everyReadyWeaponFires() {
        AttackPlan plan = ctx().decide();
        assertEquals(EnumSet.allOf(WeaponChannel.class), EnumSet.copyOf(plan.fire()));
        assertTrue(plan.holds().isEmpty());
        assertTrue(plan.airLaunchDue());
    }

    @Test
    void holdingFireFiresNothingAndMovesNowhere() {
        Ctx c = ctx();
        c.intent = new CombatIntent.HoldFire(Set.of(HoldFireReason.SITTING));
        assertEquals(AttackPlan.HOLD, c.decide());
        assertEquals(CombatManeuver.NONE, c.decide().cannon().maneuver());
    }

    @Test
    void aWeaponOutsideTheLoadoutNeverFires() {
        Ctx c = ctx();
        c.loadout = CombatLoadout.of(WeaponChannel.AIR);
        AttackPlan plan = c.decide();
        assertEquals(Set.of(WeaponChannel.AIR), plan.fire());
        for (WeaponChannel channel : EnumSet.of(WeaponChannel.LIGHT, WeaponChannel.HEAVY, WeaponChannel.MELEE)) {
            assertEquals(Set.of(AttackHoldReason.NOT_IN_LOADOUT), plan.holds().get(channel), channel.name());
        }
    }

    /** The weapon flags and stores, every one ready unless changed. */
    private static final class R {
        boolean cannons = true, useLight = true, ammoLight = true, useHeavy = true, ammoHeavy = true;
        boolean aircraft = true, useAirLight = true, useAirHeavy = true, airLight = true, airHeavy = true;
        boolean crane = false, melee = true, passenger = false;

        static WeaponReadiness with(java.util.function.Consumer<R> change) {
            R r = new R();
            change.accept(r);
            return new WeaponReadiness(r.cannons, r.useLight, r.ammoLight, r.useHeavy, r.ammoHeavy, r.aircraft,
                    r.useAirLight, r.useAirHeavy, r.airLight, r.airHeavy, r.crane, r.melee, r.passenger);
        }
    }

    private static CombatLoadout allBut(WeaponChannel channel) {
        EnumSet<WeaponChannel> rest = EnumSet.allOf(WeaponChannel.class);
        rest.remove(channel);
        return new CombatLoadout(rest);
    }

    @Test
    void eachLightConditionAloneHoldsTheLight() {
        WeaponChannel w = WeaponChannel.LIGHT;
        assertHeld(w, c -> c.loadout = allBut(w), AttackHoldReason.NOT_IN_LOADOUT);
        assertHeld(w, c -> c.readiness = R.with(r -> r.cannons = false), AttackHoldReason.NOT_ENABLED);
        assertHeld(w, c -> c.readiness = R.with(r -> r.useLight = false), AttackHoldReason.NOT_ENABLED);
        assertHeld(w, c -> c.readiness = R.with(r -> r.ammoLight = false), AttackHoldReason.NO_AMMO);
        assertHeld(w, c -> c.readiness = R.with(r -> r.crane = true), AttackHoldReason.CRANE);
        assertHeld(w, c -> c.onSight = false, AttackHoldReason.NO_LINE_OF_SIGHT);
        assertHeld(w, c -> c.distanceSq = 100.01, AttackHoldReason.OUT_OF_RANGE);
        assertHeld(w, c -> c.timing = aimed(CombatTimingReducer.initial(0), AIM - 1), AttackHoldReason.NOT_AIMED);
        assertHeld(w, c -> c.timing = CombatTimingReducer.onFired(c.timing, w, 1, NOW), AttackHoldReason.NOT_READY);
    }

    @Test
    void eachHeavyConditionAloneHoldsTheHeavy() {
        WeaponChannel w = WeaponChannel.HEAVY;
        assertHeld(w, c -> c.loadout = allBut(w), AttackHoldReason.NOT_IN_LOADOUT);
        assertHeld(w, c -> c.readiness = R.with(r -> r.cannons = false), AttackHoldReason.NOT_ENABLED);
        assertHeld(w, c -> c.readiness = R.with(r -> r.useHeavy = false), AttackHoldReason.NOT_ENABLED);
        assertHeld(w, c -> c.readiness = R.with(r -> r.ammoHeavy = false), AttackHoldReason.NO_AMMO);
        assertHeld(w, c -> c.readiness = R.with(r -> r.crane = true), AttackHoldReason.CRANE);
        assertHeld(w, c -> c.onSight = false, AttackHoldReason.NO_LINE_OF_SIGHT);
        assertHeld(w, c -> c.distanceSq = 100.01, AttackHoldReason.OUT_OF_RANGE);
        assertHeld(w, c -> c.timing = aimed(CombatTimingReducer.initial(0), AIM - 1), AttackHoldReason.NOT_AIMED);
        assertHeld(w, c -> c.timing = CombatTimingReducer.onFired(c.timing, w, 1, NOW), AttackHoldReason.NOT_READY);
    }

    @Test
    void eachAircraftConditionAloneHoldsTheLaunch() {
        WeaponChannel w = WeaponChannel.AIR;
        assertHeld(w, c -> c.loadout = allBut(w), AttackHoldReason.NOT_IN_LOADOUT);
        assertHeld(w, c -> c.readiness = R.with(r -> r.aircraft = false), AttackHoldReason.NOT_ENABLED);
        assertHeld(w, c -> c.readiness = R.with(r -> r.crane = true), AttackHoldReason.CRANE);
        assertHeld(w, c -> c.onSight = false, AttackHoldReason.NO_LINE_OF_SIGHT);
        assertHeld(w, c -> c.distanceSq = 100.01, AttackHoldReason.OUT_OF_RANGE);
        assertHeld(w, c -> c.timing = CombatTimingReducer.onFired(c.timing, w, 1, NOW), AttackHoldReason.NOT_READY);
        // each kind's stock is checked from a context that launches that kind
        java.util.function.Consumer<Ctx> heavyNext = c -> { };
        java.util.function.Consumer<Ctx> lightNext = c -> c.timing = CombatTimingReducer.onAirLaunchDue(c.timing, true);
        assertAirKind(heavyNext, true);
        assertAirKind(lightNext, false);
        assertHeld(w, heavyNext, c -> c.readiness = R.with(r -> r.airHeavy = false), AttackHoldReason.NO_AMMO);
        assertHeld(w, lightNext, c -> c.readiness = R.with(r -> r.airLight = false), AttackHoldReason.NO_AMMO);
    }

    private static void assertAirKind(java.util.function.Consumer<Ctx> base, boolean heavy) {
        Ctx c = ctx();
        base.accept(c);
        AttackPlan plan = c.decide();
        assertTrue(plan.fire().contains(WeaponChannel.AIR), "the base context launches");
        assertEquals(heavy, plan.airHeavy(), "the base context launches " + (heavy ? "heavy" : "light"));
    }

    @Test
    void eachMeleeConditionAloneHoldsTheStrike() {
        WeaponChannel w = WeaponChannel.MELEE;
        assertHeld(w, c -> c.loadout = allBut(w), AttackHoldReason.NOT_IN_LOADOUT);
        assertHeld(w, c -> c.readiness = R.with(r -> r.melee = false), AttackHoldReason.NOT_ENABLED);
        assertHeld(w, c -> c.readiness = R.with(r -> r.passenger = true), AttackHoldReason.PASSENGER);
        assertHeld(w, c -> c.meleeDistanceSq = 20.01, AttackHoldReason.OUT_OF_RANGE);
        assertHeld(w, c -> c.timing = CombatTimingReducer.onFired(c.timing, w, 1, NOW), AttackHoldReason.NOT_READY);
    }

    /** The unchanged context fires the weapon; the one change holds it for exactly that reason. */
    private static void assertHeld(WeaponChannel weapon, java.util.function.Consumer<Ctx> change,
                                   AttackHoldReason reason) {
        assertHeld(weapon, c -> { }, change, reason);
    }

    /** The same, from the default context changed by {@code base}. */
    private static void assertHeld(WeaponChannel weapon, java.util.function.Consumer<Ctx> base,
                                   java.util.function.Consumer<Ctx> change, AttackHoldReason reason) {
        Ctx before = ctx();
        base.accept(before);
        assertTrue(before.decide().fire().contains(weapon), weapon + " must fire before the change");
        Ctx c = ctx();
        base.accept(c);
        change.accept(c);
        AttackPlan plan = c.decide();
        assertFalse(plan.fire().contains(weapon), weapon + " " + reason);
        assertEquals(Set.of(reason), plan.holds().get(weapon), weapon + " " + reason);
    }

    @Test
    void rangeIsInclusiveAndReadyTickFires() {
        Ctx c = ctx();
        c.distanceSq = 100;
        c.timing = CombatTimingReducer.onFired(c.timing, WeaponChannel.LIGHT, 0, NOW);
        assertTrue(c.decide().fire().contains(WeaponChannel.LIGHT));
    }

    @Test
    void aircraftTakeNoAimAndAlternateOnlyWhileBothAreInUse() {
        Ctx c = ctx();
        c.timing = CombatTimingReducer.initial(0);
        AttackPlan plan = c.decide();
        assertTrue(plan.fire().contains(WeaponChannel.AIR));
        assertTrue(plan.airHeavy(), "next kind is heavy");
        c.timing = CombatTimingReducer.onAirLaunchDue(c.timing, true);
        assertFalse(c.decide().airHeavy());
        c.readiness = readiness(true, true, true, true, false, true, true, true, false, true, false);
        assertTrue(c.decide().airHeavy(), "only heavy in use");
        c.readiness = readiness(true, true, true, true, true, false, true, true, false, true, false);
        c.timing = CombatTimingReducer.onAirLaunchDue(c.timing, false);
        assertFalse(c.decide().airHeavy(), "only light in use");
    }

    @Test
    void aDueLaunchWithoutThatKindsStockLaunchesNothingButIsStillDue() {
        Ctx c = ctx();
        c.timing = CombatTimingReducer.initial(0);
        c.readiness = readiness(true, true, true, true, true, true, true, false, false, true, false);
        AttackPlan plan = c.decide();
        assertTrue(plan.airLaunchDue());
        assertTrue(plan.airHeavy());
        assertFalse(plan.fire().contains(WeaponChannel.AIR));
        assertEquals(Set.of(AttackHoldReason.NO_AMMO), plan.holds().get(WeaponChannel.AIR));
    }

    @Test
    void aircraftNotDueAreNotFlipped() {
        Ctx c = ctx();
        c.onSight = false;
        AttackPlan plan = c.decide();
        assertFalse(plan.airLaunchDue());
        assertEquals(Set.of(AttackHoldReason.NO_LINE_OF_SIGHT), plan.holds().get(WeaponChannel.AIR));
    }

    @Test
    void meleeIgnoresSightAndCraneButNotRiding() {
        Ctx c = ctx();
        c.onSight = false;
        c.readiness = readiness(true, true, true, true, true, true, true, true, true, true, false);
        assertTrue(c.decide().fire().contains(WeaponChannel.MELEE));
        c.readiness = readiness(true, true, true, true, true, true, true, true, false, true, true);
        assertEquals(Set.of(AttackHoldReason.PASSENGER), c.decide().holds().get(WeaponChannel.MELEE));
        c.readiness = readiness(true, true, true, true, true, true, true, true, false, false, false);
        assertEquals(Set.of(AttackHoldReason.NOT_ENABLED), c.decide().holds().get(WeaponChannel.MELEE));
        c = ctx();
        c.meleeDistanceSq = 20.01;
        assertEquals(Set.of(AttackHoldReason.OUT_OF_RANGE), c.decide().holds().get(WeaponChannel.MELEE));
    }

    @Test
    void directivesKeepEachGoalsOwnHoldCondition() {
        Ctx c = ctx();
        c.engage = 0.5D;
        c.distanceSq = 30;
        AttackPlan plan = c.decide();
        assertEquals(CombatManeuver.APPROACH_TARGET, plan.cannon().maneuver(), "outside range times engage");
        assertEquals(CombatManeuver.STOP_FOR_FIRE, plan.carrier().maneuver(), "carrier ignores engageDistance");
        c.useMelee = true;
        c.distanceSq = 4;
        assertEquals(CombatManeuver.APPROACH_TARGET, c.decide().cannon().maneuver());
        assertEquals(CombatManeuver.APPROACH_TARGET, c.decide().carrier().maneuver());
        c = ctx();
        c.onSight = false;
        assertEquals(CombatManeuver.APPROACH_TARGET, c.decide().cannon().maneuver());
        assertEquals(CombatManeuver.STOP_FOR_FIRE, c.decide().melee().maneuver());
        c.meleeDistanceSq = 21;
        assertEquals(CombatManeuver.APPROACH_TARGET, c.decide().melee().maneuver());
    }

    @Test
    void theDecisionIsDeterministic() {
        assertEquals(ctx().decide(), ctx().decide());
    }
}
