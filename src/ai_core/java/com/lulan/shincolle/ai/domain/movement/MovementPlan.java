package com.lulan.shincolle.ai.domain.movement;

import java.util.List;

/** The moves of one goal call, carried out in order. Empty does nothing. */
public record MovementPlan(List<MovementStep> steps) {
    public static final MovementPlan NONE = new MovementPlan(List.of());

    public MovementPlan {
        steps = List.copyOf(steps);
    }

    public static MovementPlan of(MovementStep... steps) {
        return steps.length == 0 ? NONE : new MovementPlan(List.of(steps));
    }

    public boolean isEmpty() {
        return this.steps.isEmpty();
    }

    public boolean hasPath() {
        return this.steps.stream().anyMatch(step -> step instanceof MovementStep.PathTo);
    }
}
