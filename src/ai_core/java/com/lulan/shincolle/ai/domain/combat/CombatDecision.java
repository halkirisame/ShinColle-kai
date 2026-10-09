package com.lulan.shincolle.ai.domain.combat;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Set;

/**
 * Chooses the weapons to fire and each attack goal's movement. Each weapon keeps the conditions
 * of the goal that fired it: the range goal (light, heavy), the carrier goal (aircraft) and the
 * melee goal.
 */
public final class CombatDecision {
    private CombatDecision() { }

    public static AttackPlan decide(CombatContext ctx) {
        if (!(ctx.intent() instanceof CombatIntent.Engage)) return AttackPlan.HOLD;
        WeaponReadiness r = ctx.readiness();
        CombatTimingState t = ctx.timing();
        EnumSet<WeaponChannel> fire = EnumSet.noneOf(WeaponChannel.class);
        EnumMap<WeaponChannel, Set<AttackHoldReason>> holds = new EnumMap<>(WeaponChannel.class);

        boolean inRange = ctx.distanceSq() <= ctx.rangeSq();
        cannon(ctx, WeaponChannel.LIGHT, r.useLight(), r.hasAmmoLight(), ctx.aimTime(), inRange, fire, holds);
        cannon(ctx, WeaponChannel.HEAVY, r.useHeavy(), r.hasAmmoHeavy(), ctx.aimTime(), inRange, fire, holds);

        boolean airLaunchDue = false;
        boolean airHeavy = false;
        if (ctx.loadout().has(WeaponChannel.AIR)) {
            EnumSet<AttackHoldReason> why = EnumSet.noneOf(AttackHoldReason.class);
            if (!r.aircraftEnabled()) why.add(AttackHoldReason.NOT_ENABLED);
            if (r.crane()) why.add(AttackHoldReason.CRANE);
            if (!ctx.onSight()) why.add(AttackHoldReason.NO_LINE_OF_SIGHT);
            if (!inRange) why.add(AttackHoldReason.OUT_OF_RANGE);
            if (ctx.now() < t.readyAt(WeaponChannel.AIR)) why.add(AttackHoldReason.NOT_READY);
            if (why.isEmpty()) {
                airLaunchDue = true;
                airHeavy = !r.useAirLight() || r.useAirHeavy() && t.nextAirHeavy();
                if (airHeavy ? r.airHeavyStocked() : r.airLightStocked()) {
                    fire.add(WeaponChannel.AIR);
                } else {
                    why.add(AttackHoldReason.NO_AMMO);
                }
            }
            if (!why.isEmpty()) holds.put(WeaponChannel.AIR, why);
        } else {
            holds.put(WeaponChannel.AIR, EnumSet.of(AttackHoldReason.NOT_IN_LOADOUT));
        }

        boolean inReach = ctx.meleeDistanceSq() <= ctx.meleeReachSq();
        if (ctx.loadout().has(WeaponChannel.MELEE)) {
            EnumSet<AttackHoldReason> why = EnumSet.noneOf(AttackHoldReason.class);
            if (!r.meleeEnabled()) why.add(AttackHoldReason.NOT_ENABLED);
            if (r.passenger()) why.add(AttackHoldReason.PASSENGER);
            if (!inReach) why.add(AttackHoldReason.OUT_OF_RANGE);
            if (ctx.now() < t.readyAt(WeaponChannel.MELEE)) why.add(AttackHoldReason.NOT_READY);
            if (why.isEmpty()) fire.add(WeaponChannel.MELEE);
            else holds.put(WeaponChannel.MELEE, why);
        } else {
            holds.put(WeaponChannel.MELEE, EnumSet.of(AttackHoldReason.NOT_IN_LOADOUT));
        }

        double engage = ctx.engageFactor();
        boolean cannonHold = ctx.distanceSq() < ctx.rangeSq() * engage * engage && ctx.onSight() && !ctx.useMelee();
        boolean carrierHold = ctx.distanceSq() < ctx.rangeSq() && ctx.onSight() && !ctx.useMelee();
        return new AttackPlan(fire, airLaunchDue, airHeavy,
                cannonHold ? CombatMovementDirective.STOP : CombatMovementDirective.APPROACH,
                carrierHold ? CombatMovementDirective.STOP : CombatMovementDirective.APPROACH,
                inReach ? CombatMovementDirective.STOP : CombatMovementDirective.APPROACH,
                holds);
    }

    private static void cannon(CombatContext ctx, WeaponChannel channel, boolean use, boolean ammo, int aimTime,
                               boolean inRange, Set<WeaponChannel> fire,
                               EnumMap<WeaponChannel, Set<AttackHoldReason>> holds) {
        if (!ctx.loadout().has(channel)) {
            holds.put(channel, EnumSet.of(AttackHoldReason.NOT_IN_LOADOUT));
            return;
        }
        WeaponReadiness r = ctx.readiness();
        EnumSet<AttackHoldReason> why = EnumSet.noneOf(AttackHoldReason.class);
        if (!r.cannonsEnabled() || !use) why.add(AttackHoldReason.NOT_ENABLED);
        if (!ammo) why.add(AttackHoldReason.NO_AMMO);
        if (r.crane()) why.add(AttackHoldReason.CRANE);
        if (!ctx.onSight()) why.add(AttackHoldReason.NO_LINE_OF_SIGHT);
        if (!inRange) why.add(AttackHoldReason.OUT_OF_RANGE);
        if (ctx.timing().aimTicks() < aimTime) why.add(AttackHoldReason.NOT_AIMED);
        if (ctx.now() < ctx.timing().readyAt(channel)) why.add(AttackHoldReason.NOT_READY);
        if (why.isEmpty()) fire.add(channel);
        else holds.put(channel, why);
    }
}
