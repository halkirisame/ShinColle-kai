package com.lulan.shincolle.ai.domain.combat;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * What to fire this tick and how each attack goal moves.
 *
 * @param fire         the weapons to fire now
 * @param airLaunchDue an aircraft launch was due this tick, which flips the next kind even if none launched
 * @param airHeavy     the kind due, meaningful only while a launch is due
 * @param cannon       the range goal's movement
 * @param carrier      the carrier goal's movement
 * @param melee        the melee goal's movement
 * @param holds        for every carried weapon that does not fire while engaging, why
 */
public record AttackPlan(Set<WeaponChannel> fire, boolean airLaunchDue, boolean airHeavy,
                         CombatMovementDirective cannon, CombatMovementDirective carrier,
                         CombatMovementDirective melee, Map<WeaponChannel, Set<AttackHoldReason>> holds) {
    public static final AttackPlan HOLD = new AttackPlan(Set.of(), false, false,
            CombatMovementDirective.NONE, CombatMovementDirective.NONE, CombatMovementDirective.NONE, Map.of());

    public AttackPlan {
        fire = fire.isEmpty() ? Set.of() : Set.copyOf(EnumSet.copyOf(fire));
        Objects.requireNonNull(cannon, "cannon");
        Objects.requireNonNull(carrier, "carrier");
        Objects.requireNonNull(melee, "melee");
        EnumMap<WeaponChannel, Set<AttackHoldReason>> copy = new EnumMap<>(WeaponChannel.class);
        holds.forEach((channel, reasons) -> copy.put(channel, Set.copyOf(reasons)));
        holds = Map.copyOf(copy);
    }
}
