package com.lulan.shincolle.ai.domain.movement;

import java.util.Objects;

/** A planner's answer: the goal's next state and the moves to carry out. */
public record PlannedMove<S extends MovementState>(S state, MovementPlan plan) {
    public PlannedMove {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(plan, "plan");
    }
}
