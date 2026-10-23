package com.lulan.shincolle.ai.domain.combat;

import java.util.EnumSet;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

/** Pure start/cancel/emission rules, independent of targets, ordinary clocks and movement. */
public final class RuBurstDecision {
    private RuBurstDecision() { }

    public enum Effect { START, EMIT, FINISH, CANCEL, REJECT }
    public enum Reason { BUSY, NO_AMMO, STOP_AI, NO_AI, NOT_ALIVE, REMOVED, SITTING, CRANE, FIRING_BLOCKED, DIMENSION_CHANGED, CLEARED }

    public record Permission(boolean stopAi, boolean noAi, boolean alive, boolean removed,
                             boolean sitting, boolean crane, boolean firingAllowed) {
        public Set<Reason> reasons() {
            EnumSet<Reason> reasons = EnumSet.noneOf(Reason.class);
            if (this.stopAi) reasons.add(Reason.STOP_AI);
            if (this.noAi) reasons.add(Reason.NO_AI);
            if (!this.alive) reasons.add(Reason.NOT_ALIVE);
            if (this.removed) reasons.add(Reason.REMOVED);
            if (this.sitting) reasons.add(Reason.SITTING);
            if (this.crane) reasons.add(Reason.CRANE);
            if (!this.firingAllowed) reasons.add(Reason.FIRING_BLOCKED);
            return Set.copyOf(reasons);
        }
    }

    public record Plan(RuBurstState state, Set<Effect> effects, Set<Reason> reasons, Optional<RuBurstState.Aim> shot) {
        public Plan {
            effects = Set.copyOf(effects);
            reasons = Set.copyOf(reasons);
        }
    }

    public static Plan start(RuBurstState state, int level, RuBurstState.Aim aim, Permission permission, boolean ammo) {
        EnumSet<Reason> reasons = EnumSet.noneOf(Reason.class);
        reasons.addAll(permission.reasons());
        if (state.busy()) reasons.add(Reason.BUSY);
        if (!ammo) reasons.add(Reason.NO_AMMO);
        if (!reasons.isEmpty()) return new Plan(state, Set.of(Effect.REJECT), reasons, Optional.empty());
        return new Plan(new RuBurstState(new RuBurstState.Burst(5 + (int) (Math.max(0, level) * .035F), aim),
                state.lastEvaluatedTick()), Set.of(Effect.START), Set.of(), Optional.empty());
    }

    public static Plan observe(RuBurstState state, Permission permission, boolean sameDimension) {
        EnumSet<Reason> reasons = EnumSet.noneOf(Reason.class);
        reasons.addAll(permission.reasons());
        if (!sameDimension) reasons.add(Reason.DIMENSION_CHANGED);
        return state.busy() && !reasons.isEmpty() ? cancel(state, reasons) : unchanged(state);
    }

    public static Plan tick(RuBurstState state, int now, Permission permission, boolean sameDimension) {
        Plan observed = observe(state, permission, sameDimension);
        if (observed.effects().contains(Effect.CANCEL)) return observed;
        if (state.lastEvaluatedTick().isPresent() && state.lastEvaluatedTick().getAsInt() == now) return unchanged(state);
        RuBurstState evaluated = new RuBurstState(state.phase(), OptionalInt.of(now));
        if (!(state.phase() instanceof RuBurstState.Burst burst) || (now & 3) != 0) return unchanged(evaluated);
        RuBurstState.Phase next = burst.remaining() == 1 ? new RuBurstState.Idle()
                : new RuBurstState.Burst(burst.remaining() - 1, burst.fixedAim());
        return new Plan(new RuBurstState(next, OptionalInt.of(now)), burst.remaining() == 1
                ? Set.of(Effect.EMIT, Effect.FINISH) : Set.of(Effect.EMIT), Set.of(), Optional.of(burst.fixedAim()));
    }

    public static Plan cancel(RuBurstState state, Set<Reason> reasons) {
        return new Plan(new RuBurstState(new RuBurstState.Idle(), state.lastEvaluatedTick()),
                state.busy() ? Set.of(Effect.CANCEL) : Set.of(), reasons, Optional.empty());
    }

    private static Plan unchanged(RuBurstState state) {
        return new Plan(state, Set.of(), Set.of(), Optional.empty());
    }
}
