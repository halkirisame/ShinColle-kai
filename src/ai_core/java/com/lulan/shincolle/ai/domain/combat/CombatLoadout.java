package com.lulan.shincolle.ai.domain.combat;

import java.util.EnumSet;
import java.util.Set;

/** The weapons a host carries, fixed by the attack goals registered for it. */
public record CombatLoadout(Set<WeaponChannel> channels) {
    public static final CombatLoadout NONE = new CombatLoadout(Set.of());

    public CombatLoadout {
        channels = channels.isEmpty() ? Set.of() : Set.copyOf(EnumSet.copyOf(channels));
    }

    public static CombatLoadout of(WeaponChannel... channels) {
        return new CombatLoadout(channels.length == 0 ? Set.of() : EnumSet.of(channels[0], channels));
    }

    public boolean has(WeaponChannel channel) {
        return this.channels.contains(channel);
    }

    public CombatLoadout with(Set<WeaponChannel> added) {
        EnumSet<WeaponChannel> all = EnumSet.noneOf(WeaponChannel.class);
        all.addAll(this.channels);
        all.addAll(added);
        return new CombatLoadout(all);
    }
}
