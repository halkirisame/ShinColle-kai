package com.lulan.shincolle.ai.domain.combat;

import com.lulan.shincolle.ai.domain.TargetHandle;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The one attack timer of a host. Times are the host's tick count.
 *
 * @param readyAt      per weapon, the first tick it may fire again
 * @param nextAirHeavy the next aircraft launch is heavy, while both kinds are in use
 * @param aimTarget    the target being aimed at; empty while holding fire
 * @param aimTicks     consecutive ticks the aim target has been in sight
 * @param engagedAt    the tick the aim target was taken
 * @param cannonsIdle  the cannons could not aim on the last tick (the range goal could not run), so the aim
 *                     restarts when they can
 */
public record CombatTimingState(Map<WeaponChannel, Integer> readyAt, boolean nextAirHeavy,
                                Optional<TargetHandle> aimTarget, int aimTicks, int engagedAt,
                                boolean cannonsIdle) {
    public CombatTimingState {
        Objects.requireNonNull(aimTarget, "aimTarget");
        EnumMap<WeaponChannel, Integer> copy = new EnumMap<>(WeaponChannel.class);
        copy.putAll(readyAt);
        for (WeaponChannel channel : WeaponChannel.values()) {
            if (!copy.containsKey(channel)) throw new IllegalArgumentException("no readyAt for " + channel);
        }
        readyAt = Map.copyOf(copy);
    }

    public int readyAt(WeaponChannel channel) {
        return this.readyAt.get(channel);
    }

    CombatTimingState withReady(WeaponChannel channel, int tick) {
        EnumMap<WeaponChannel, Integer> next = new EnumMap<>(this.readyAt);
        next.put(channel, tick);
        return new CombatTimingState(next, this.nextAirHeavy, this.aimTarget, this.aimTicks, this.engagedAt,
                this.cannonsIdle);
    }
}
