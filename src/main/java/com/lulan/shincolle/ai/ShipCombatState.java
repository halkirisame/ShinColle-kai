package com.lulan.shincolle.ai;

import com.lulan.shincolle.ai.domain.TargetHandle;
import com.lulan.shincolle.ai.domain.combat.CombatLoadout;
import com.lulan.shincolle.ai.domain.combat.CombatTimingReducer;
import com.lulan.shincolle.ai.domain.combat.CombatTimingState;
import com.lulan.shincolle.ai.domain.combat.WeaponChannel;

import java.util.Optional;
import java.util.Set;

/**
 * A host's combat state under NEW: the weapons its attack goals carry and its one attack timer.
 * Server only, never saved; a loaded entity starts over.
 */
public final class ShipCombatState {
    private CombatLoadout loadout = CombatLoadout.NONE;
    private CombatTimingState timing;
    private final ShipSkillAttackState skill = new ShipSkillAttackState();

    public ShipSkillAttackState skill() {
        return skill;
    }

    public CombatLoadout loadout() {
        return this.loadout;
    }

    /** Called where an attack goal is created for the host, alongside its registration. */
    void addWeapons(Set<WeaponChannel> channels) {
        this.loadout = this.loadout.with(channels);
    }

    /** Called when the host's goals are cleared; registering them again rebuilds the loadout. */
    public void clearWeapons() {
        this.loadout = CombatLoadout.NONE;
        this.skill.clear();
    }

    /**
     * The timer, started when the host first engages: an attack goal counted its delays from its
     * start, so the first engagement fires on the same ticks as before.
     */
    public CombatTimingState timing(int now) {
        if (this.timing == null) this.timing = CombatTimingReducer.initial(now);
        return this.timing;
    }

    /** The target being aimed at, for inspection; empty before the first engagement. */
    public Optional<TargetHandle> aimTarget() {
        return this.timing == null ? Optional.empty() : this.timing.aimTarget();
    }

    void dropAim() {
        if (this.timing != null) this.timing = CombatTimingReducer.dropAim(this.timing);
    }

    void setTiming(CombatTimingState timing) {
        this.timing = timing;
    }
}
