package com.lulan.shincolle.ai.domain.combat;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Pure transitions of a host's attack timer. */
public final class CombatTimingReducer {
    /** The delays a fresh attack goal started with. */
    static final int INITIAL_LIGHT = 20;
    static final int INITIAL_HEAVY = 40;
    static final int INITIAL_AIR = 20;
    static final int INITIAL_MELEE = 20;
    /** What a stuck reset sets the delays back to. */
    static final int STUCK_RESET_DELAY = 20;

    private CombatTimingReducer() { }

    /** Continuous skills pause the ordinary weapon intervals rather than letting them expire. */
    public static CombatTimingState pauseForSkill(CombatTimingState state) {
        EnumMap<WeaponChannel, Integer> ready = new EnumMap<>(WeaponChannel.class);
        state.readyAt().forEach((channel, at) -> ready.put(channel, at + 1));
        return new CombatTimingState(ready, state.nextAirHeavy(), state.aimTarget(), state.aimTicks(),
                state.engagedAt() + 1, state.cannonsIdle());
    }

    /**
     * The timer of a host that starts to engage now, as a fresh attack goal starting now: a goal
     * counted its start tick as the first tick of a delay, so a delay set on start ends one tick
     * earlier than the same delay set after a shot. The first aircraft launch is heavy, as the
     * carrier goal's launch type started false.
     */
    public static CombatTimingState initial(int now) {
        EnumMap<WeaponChannel, Integer> ready = new EnumMap<>(WeaponChannel.class);
        ready.put(WeaponChannel.LIGHT, onStart(now, INITIAL_LIGHT));
        ready.put(WeaponChannel.HEAVY, onStart(now, INITIAL_HEAVY));
        ready.put(WeaponChannel.AIR, onStart(now, INITIAL_AIR));
        ready.put(WeaponChannel.MELEE, onStart(now, INITIAL_MELEE));
        return new CombatTimingState(ready, true, Optional.empty(), 0, now, false);
    }

    private static int onStart(int now, int delay) {
        return now + delay - 1;
    }

    /**
     * Holding fire drops the aim. Engaging a new target restarts the aim and, as the range goal's
     * start did, keeps the cannons from firing before one (light) or two (heavy) aim times.
     */
    public static CombatTimingState onIntent(CombatTimingState s, CombatIntent intent, int now, int aimTime) {
        if (!(intent instanceof CombatIntent.Engage engage)) return dropAim(s);
        if (s.aimTarget().filter(engage.target()::equals).isPresent()) return s;
        return new CombatTimingState(aimFloors(s, now, aimTime), s.nextAirHeavy(), Optional.of(engage.target()), 0, now,
                s.cannonsIdle());
    }

    /** The range goal's start: the cannons wait at least one (light) or two (heavy) aim times. */
    private static Map<WeaponChannel, Integer> aimFloors(CombatTimingState s, int now, int aimTime) {
        EnumMap<WeaponChannel, Integer> ready = new EnumMap<>(s.readyAt());
        ready.put(WeaponChannel.LIGHT, Math.max(s.readyAt(WeaponChannel.LIGHT), onStart(now, aimTime)));
        ready.put(WeaponChannel.HEAVY, Math.max(s.readyAt(WeaponChannel.HEAVY), onStart(now, aimTime * 2)));
        return ready;
    }

    /**
     * Whether the cannons may aim: the range goal could run, as the host carries a cannon, one of them
     * may fire (type, use flag and ammunition) and the crane is not loading.
     */
    public static boolean cannonsCanAim(CombatLoadout loadout, WeaponReadiness r) {
        return (loadout.has(WeaponChannel.LIGHT) || loadout.has(WeaponChannel.HEAVY)) && r.cannonsEnabled() && !r.crane();
    }

    /**
     * The cannons aim only while they may, as the range goal counted sight only while it ran. While they
     * may not, the aim stays at zero; on the tick they may again, the aim restarts under the floors the
     * goal's start set, and the sight of that tick counts.
     */
    public static CombatTimingState onCannonSight(CombatTimingState s, boolean canAim, boolean onSight, int now,
                                                  int aimTime) {
        if (!canAim) {
            if (s.cannonsIdle() && s.aimTicks() == 0) return s;
            return new CombatTimingState(s.readyAt(), s.nextAirHeavy(), s.aimTarget(), 0, s.engagedAt(), true);
        }
        CombatTimingState t = !s.cannonsIdle() ? s
                : new CombatTimingState(aimFloors(s, now, aimTime), s.nextAirHeavy(), s.aimTarget(), 0, s.engagedAt(),
                        false);
        return onSight(t, onSight);
    }

    public static CombatTimingState dropAim(CombatTimingState s) {
        if (s.aimTarget().isEmpty() && s.aimTicks() == 0) return s;
        return new CombatTimingState(s.readyAt(), s.nextAirHeavy(), Optional.empty(), 0, s.engagedAt(), s.cannonsIdle());
    }

    public static CombatTimingState onSight(CombatTimingState s, boolean onSight) {
        int aim = onSight ? s.aimTicks() + 1 : 0;
        if (aim == s.aimTicks()) return s;
        return new CombatTimingState(s.readyAt(), s.nextAirHeavy(), s.aimTarget(), aim, s.engagedAt(), s.cannonsIdle());
    }

    public static CombatTimingState onFired(CombatTimingState s, WeaponChannel channel, int delay, int now) {
        return s.withReady(channel, now + delay);
    }

    /** An aircraft launch was due; the next one is the other kind, whether this one launched or not. */
    public static CombatTimingState onAirLaunchDue(CombatTimingState s, boolean heavy) {
        return new CombatTimingState(s.readyAt(), !heavy, s.aimTarget(), s.aimTicks(), s.engagedAt(), s.cannonsIdle());
    }

    /** Whether every one of the weapons has waited past its ready tick for more than the threshold. */
    public static boolean stuck(CombatTimingState s, Set<WeaponChannel> channels, int now, int threshold) {
        for (WeaponChannel channel : channels) {
            if (now - Math.max(s.readyAt(channel), s.engagedAt()) <= threshold) return false;
        }
        return !channels.isEmpty();
    }

    /**
     * Sets the weapons back to a short delay. Resetting the cannons also drops the aim, as the
     * range goal's stop cleared its sight time; the goal was not restarted, so no aim floor applies.
     */
    public static CombatTimingState onStuckReset(CombatTimingState s, Set<WeaponChannel> channels, int now) {
        EnumMap<WeaponChannel, Integer> ready = new EnumMap<>(s.readyAt());
        for (WeaponChannel channel : channels) ready.put(channel, now + STUCK_RESET_DELAY);
        boolean cannons = channels.contains(WeaponChannel.LIGHT) || channels.contains(WeaponChannel.HEAVY);
        return new CombatTimingState(ready, s.nextAirHeavy(), s.aimTarget(), cannons ? 0 : s.aimTicks(), s.engagedAt(),
                s.cannonsIdle());
    }
}
