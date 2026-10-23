package com.lulan.shincolle.ai;

import com.lulan.shincolle.ai.domain.combat.RuBurstDecision;
import com.lulan.shincolle.ai.domain.combat.RuBurstState;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.Optional;
import java.util.Set;

/** Per-entity server envelope. No burst payload is persisted or sent to clients. */
public final class RuBurstRuntime {
    private RuBurstState state = RuBurstState.initial();
    private Set<RuBurstDecision.Reason> reasons = Set.of();
    Optional<ResourceKey<Level>> dimension = Optional.empty();

    public RuBurstState state() { return this.state; }
    public Set<RuBurstDecision.Reason> reasons() { return this.reasons; }

    void apply(RuBurstDecision.Plan plan) {
        this.state = plan.state();
        if (!plan.reasons().isEmpty() || plan.effects().contains(RuBurstDecision.Effect.START)) this.reasons = plan.reasons();
        if (!this.state.busy()) this.dimension = Optional.empty();
    }

    void cancel() {
        apply(RuBurstDecision.cancel(this.state, Set.of(RuBurstDecision.Reason.CLEARED)));
    }

    void clear() {
        this.state = RuBurstState.initial();
        this.reasons = Set.of();
        this.dimension = Optional.empty();
    }
}
