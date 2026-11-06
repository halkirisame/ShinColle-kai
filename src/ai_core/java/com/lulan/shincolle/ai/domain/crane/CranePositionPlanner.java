package com.lulan.shincolle.ai.domain.crane;

import com.lulan.shincolle.ai.domain.movement.MovementBody;
import com.lulan.shincolle.ai.domain.movement.MovementIntent;
import com.lulan.shincolle.ai.domain.movement.MovementPlan;
import com.lulan.shincolle.ai.domain.movement.MovementPoint;
import com.lulan.shincolle.ai.domain.movement.MovementReason;
import com.lulan.shincolle.ai.domain.movement.MovementStep;
import com.lulan.shincolle.ai.domain.movement.MovementTarget;

import java.util.Optional;

/** Bounded path requests for an explicitly selected crane movement intent. */
public final class CranePositionPlanner {
    public record State(int nextPathTick, Optional<MovementPoint> destination, boolean moving) {
        public static final State INITIAL = new State(0, Optional.empty(), false);
    }

    public record Result(State state, MovementPlan plan) { }

    private CranePositionPlanner() {
    }

    public static Result step(State state, MovementIntent.CranePosition intent, MovementPoint body, int tick) {
        if (body.distanceSq(intent.destination()) <= 0.25D) {
            return new Result(new State(tick, Optional.of(intent.destination()), false),
                    state.moving() ? MovementPlan.of(new MovementStep.Stop(MovementBody.SELF, MovementReason.CRANE_ARRIVED))
                            : MovementPlan.NONE);
        }
        if (!state.destination().filter(intent.destination()::equals).isPresent() || tick >= state.nextPathTick()) {
            return new Result(new State(tick + 16, Optional.of(intent.destination()), true),
                    MovementPlan.of(new MovementStep.PathTo(MovementBody.SELF,
                            new MovementTarget.Point(intent.destination()), intent.speed(), intent.reason())));
        }
        return new Result(state, MovementPlan.NONE);
    }
}
