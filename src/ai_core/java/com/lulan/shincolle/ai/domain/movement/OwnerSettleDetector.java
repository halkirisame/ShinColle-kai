package com.lulan.shincolle.ai.domain.movement;

import com.lulan.shincolle.ai.domain.DimensionKey;

import java.util.ArrayList;
import java.util.List;

/** Whether an owner has landed and stopped, from facts only; it never sees an entity or a world. */
public final class OwnerSettleDetector {
    /** Two seconds of history at twenty ticks a second. */
    public static final int WINDOW = 40;
    private static final double SETTLE_DISTANCE_SQ = 6D * 6D;

    private OwnerSettleDetector() { }

    /**
     * @param fallFlying gliding with elytra
     * @param supported  standing, swimming or riding, so not falling
     */
    public record Facts(boolean fallFlying, boolean supported) { }

    public record State(DimensionKey dimension, List<OwnerJumpDetector.Sample> history, boolean settled) {
        public State {
            history = List.copyOf(history);
        }

        public static State initial(DimensionKey dimension) {
            return new State(dimension, List.of(), false);
        }
    }

    /** @param becameSettled true only on the tick the owner went from not settled to settled */
    public record Observation(State state, boolean settled, boolean becameSettled) { }

    public static Observation observe(State state, DimensionKey dimension, MovementPoint position, Facts facts, long now) {
        if (!state.dimension().equals(dimension)) state = State.initial(dimension);
        List<OwnerJumpDetector.Sample> history = state.history();
        if (!history.isEmpty()) {
            long previous = history.get(history.size() - 1).tick();
            if (now <= previous) return new Observation(state, state.settled(), false);
            if (now != previous + 1) history = List.of();
        }
        boolean settled = false;
        if (history.size() == WINDOW && !facts.fallFlying() && facts.supported()) {
            MovementPoint from = history.get(0).position();
            double dx = position.x() - from.x();
            double dz = position.z() - from.z();
            settled = dx * dx + dz * dz < SETTLE_DISTANCE_SQ;
        }
        List<OwnerJumpDetector.Sample> next = new ArrayList<>(history);
        if (next.size() == WINDOW) next.remove(0);
        next.add(new OwnerJumpDetector.Sample(now, position));
        return new Observation(new State(dimension, next, settled), settled, settled && !state.settled());
    }
}
