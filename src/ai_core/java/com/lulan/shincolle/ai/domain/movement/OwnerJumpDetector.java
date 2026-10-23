package com.lulan.shincolle.ai.domain.movement;

import com.lulan.shincolle.ai.domain.DimensionKey;

import java.util.List;
import java.util.ArrayList;
import java.util.Optional;

/** Horizontal movement over the last second, independent of worlds and entity ticking. */
public final class OwnerJumpDetector {
    public static final int WINDOW = 20;
    public static final int HOLD_TICKS = 400;
    /** Twelve blocks a second: below an elytra glide, above walking and sprinting. */
    private static final double JUMP_DISTANCE_SQ = 12D * 12D;

    private OwnerJumpDetector() { }

    public record Sample(long tick, MovementPoint position) { }

    /**
     * @param detections the ticks of the departures detected within the last {@link #HOLD_TICKS}, so a request made at
     *                   an earlier one is still told apart from a stale one. They never suppress a later detection.
     */
    public record State(DimensionKey dimension, List<Sample> history, List<Long> detections) {
        public State {
            history = List.copyOf(history);
            detections = List.copyOf(detections);
        }

        public static State initial(DimensionKey dimension) {
            return new State(dimension, List.of(), List.of());
        }

        /** Whether a departure was detected at exactly this tick and has not yet been held for {@link #HOLD_TICKS}. */
        public boolean detectedAt(long tick) {
            return detections.contains(tick);
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
        List<Long> detections = new ArrayList<>(state.detections());
        detections.removeIf(tick -> now - tick >= HOLD_TICKS);
        if (history.size() == WINDOW) {
            MovementPoint from = history.get(0).position();
            double dx = position.x() - from.x();
            double dz = position.z() - from.z();
            if (dx * dx + dz * dz >= JUMP_DISTANCE_SQ) {
                departed = Optional.of(from);
                detections.add(now);
                // The window starts again from this tick's position alone, so the same jump cannot be seen twice
                // and the next departure can be told once the window has filled again.
                history = List.of();
            }
        }
        List<Sample> next = new ArrayList<>(history);
        if (next.size() == WINDOW) next.remove(0);
        next.add(new Sample(now, position));
        return new Observation(new State(dimension, next, detections), departed);
    }
}
