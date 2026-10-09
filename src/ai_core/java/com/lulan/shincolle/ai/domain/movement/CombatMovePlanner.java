package com.lulan.shincolle.ai.domain.movement;

import com.lulan.shincolle.ai.domain.TargetHandle;

import java.util.Optional;

/**
 * How an attack goal carries out its composed combat move. It remembers only the last reason, so a
 * switch into or out of the region rules re-paths at once instead of finishing a path that would
 * cross the edge; every other switch keeps the goal's own re-path timing. Stuck on the way in, it
 * re-paths at once, then tries a point beside where it was going, then holds and shoots from where
 * it stands.
 */
public final class CombatMovePlanner {

    private CombatMovePlanner() { }

    /**
     * @param targetAt      where the target stands
     * @param due           the goal's own re-path time has come
     * @param stopEveryTick a hold stops on every tick, not only when due
     * @param stuck         this tick's observation of the way in
     */
    public record Facts(CombatMove move, TargetHandle target, MovementPoint targetAt, double speed, boolean due,
                        boolean stopEveryTick, StuckState stuck) {
    }

    /** On its way while the composed move closes in. */
    public static boolean travelling(CombatMove move) {
        return move instanceof CombatMove.Approach;
    }

    public static PlannedMove<MovementState.Combat> plan(MovementState.Combat state, Facts facts) {
        CombatMove.Reason reason = facts.move().reason();
        Optional<CombatMove.Reason> last = state.last();
        boolean switched = last.isEmpty() || last.get() != reason;
        boolean act = facts.due() || switched && (last.filter(CombatMovePlanner::regional).isPresent()
                || regional(reason));
        MovementState.Combat next = new MovementState.Combat(Optional.of(reason));
        if (facts.move() instanceof CombatMove.Approach approach) {
            StuckState stuck = facts.stuck();
            // stuck for long enough: hold where it is and shoot from there
            if (stuck.gaveUp()) {
                return new PlannedMove<>(next, act || stuck.due() == StuckStage.GIVE_UP
                        ? MovementPlan.of(new MovementStep.Stop(MovementBody.SELF, MovementReason.STUCK_GIVE_UP))
                        : MovementPlan.NONE);
            }
            boolean toward = reason == CombatMove.Reason.TOWARD_TARGET;
            MovementTarget target = toward ? new MovementTarget.Entity(facts.target())
                    : new MovementTarget.Point(approach.destination());
            Optional<MovementStep.PathTo> recovery = StuckDetector.recovery(stuck, MovementBody.SELF, target,
                    toward ? facts.targetAt() : approach.destination(), facts.speed(), Optional.empty());
            if (recovery.isPresent()) return new PlannedMove<>(next, MovementPlan.of(recovery.get()));
            if (!act) return new PlannedMove<>(next, MovementPlan.NONE);
            return new PlannedMove<>(next, MovementPlan.of(new MovementStep.PathTo(MovementBody.SELF, target,
                    facts.speed(), toward ? MovementReason.COMBAT_TOWARD_TARGET : MovementReason.COMBAT_TO_REGION_EDGE)));
        }
        return new PlannedMove<>(next, act || facts.stopEveryTick()
                ? MovementPlan.of(new MovementStep.Stop(MovementBody.SELF, MovementReason.COMBAT_HOLD))
                : MovementPlan.NONE);
    }

    private static boolean regional(CombatMove.Reason reason) {
        return reason == CombatMove.Reason.TO_REGION_EDGE || reason == CombatMove.Reason.AT_REGION_EDGE
                || reason == CombatMove.Reason.NO_COMBAT_MOVEMENT;
    }
}
