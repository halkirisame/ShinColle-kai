package com.lulan.shincolle.ai.domain.movement;

import com.lulan.shincolle.ai.domain.DimensionKey;

import java.util.List;
import java.util.ArrayList;
import java.util.Optional;
import java.util.OptionalLong;

/** Horizontal movement over the last second, independent of worlds and entity ticking. */
public final class OwnerJumpDetector {
    public static final int WINDOW = 20;
    public static final int HOLD_TICKS = 400;
    private static final double JUMP_DISTANCE_SQ = 30D * 30D;

    private OwnerJumpDetector() { }

    public record Sample(long tick, MovementPoint position) { }

    public record State(DimensionKey dimension, List<Sample> history, OptionalLong cooldownUntil) {
        public State {
            history = List.copyOf(history);
        }

        public static State initial(DimensionKey dimension) {
            return new State(dimension, List.of(), OptionalLong.empty());
        }
    }

    public record Observation(State state, Optional<MovementPoint> departedFrom) { }

    public static Observation observe(State state, DimensionKey dimension, MovementPoint position, long now) {
        if (!state.dimension().equals(dimension)) state = State.initial(dimension);
        List<Sample> history = state.history();
        if (!history.isEmpty()) {
            long previous = history.get(history.size() - 1).tick();
            if (now <= previous) return new Observation(state, Optional.empty());
            if (now != previous + 1) {
                history = List.of();
            }
        }
        Optional<MovementPoint> departed = Optional.empty();
        OptionalLong cooldown = state.cooldownUntil();
        if (history.size() == WINDOW && (cooldown.isEmpty() || now >= cooldown.getAsLong())) {
            MovementPoint from = history.get(0).position();
            double dx = position.x() - from.x();
            double dz = position.z() - from.z();
            if (dx * dx + dz * dz >= JUMP_DISTANCE_SQ) {
                departed = Optional.of(from);
                cooldown = OptionalLong.of(now + HOLD_TICKS);
            }
        }
        List<Sample> next = new ArrayList<>(history);
        if (next.size() == WINDOW) next.remove(0);
        next.add(new Sample(now, position));
        return new Observation(new State(dimension, next, cooldown), departed);
    }
}
