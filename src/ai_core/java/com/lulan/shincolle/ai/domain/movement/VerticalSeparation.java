package com.lulan.shincolle.ai.domain.movement;

import com.lulan.shincolle.ai.domain.TargetHandle;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Relative vertical separation and its short, explicitly commanded recall window. */
public final class VerticalSeparation {
    public static final int HISTORY_TICKS = 20;
    public static final int RECALL_TICKS = 600;
    public static final int NOTICE_TICKS = 60;
    private static final double SEPARATION = 10D;

    private VerticalSeparation() { }

    public record Sample(long tick, double difference) { }
    public record Window(long detectedAt, boolean notified) { }
    public record State(Optional<TargetHandle> owner, List<Sample> history, Optional<Window> window) {
        public State {
            history = List.copyOf(history);
        }

        public static State empty() {
            return new State(Optional.empty(), List.of(), Optional.empty());
        }
    }
    public record Observation(State state, boolean notifyOwner) { }

    public static Observation observe(State state, TargetHandle owner, double relativeY, long now) {
        if (!state.owner().filter(owner::equals).isPresent()) state = State.empty();
        if (!state.history().isEmpty()) {
            long previous = state.history().get(state.history().size() - 1).tick();
            if (now <= previous) return new Observation(state, false);
            if (now != previous + 1) state = State.empty();
        }
        double difference = Math.abs(relativeY);
        Optional<Window> window = state.window();
        List<Sample> history = new ArrayList<>(state.history());
        history.removeIf(sample -> now - sample.tick() > HISTORY_TICKS);
        if (difference < SEPARATION) {
            if (window.isPresent()) history.clear();
            window = Optional.empty();
        } else {
            if (window.isPresent() && now - window.get().detectedAt() >= RECALL_TICKS) window = Optional.empty();
            boolean increased = history.stream().anyMatch(sample -> difference - sample.difference() >= SEPARATION);
            if (window.isEmpty() && increased) window = Optional.of(new Window(now, false));
        }
        boolean notice = window.isPresent() && !window.get().notified()
                && now - window.get().detectedAt() >= NOTICE_TICKS;
        if (notice) window = Optional.of(new Window(window.get().detectedAt(), true));
        history.add(new Sample(now, difference));
        return new Observation(new State(Optional.of(owner), history, window), notice);
    }

    /** Checked again against the current height at the command boundary. */
    public static State atCommand(State state, TargetHandle owner, double relativeY, long now) {
        return canRecall(state, owner, relativeY, now) ? state : consume(state);
    }

    public static boolean canRecall(State state, TargetHandle owner, double relativeY, long now) {
        return state.owner().filter(owner::equals).isPresent() && Math.abs(relativeY) >= SEPARATION
                && state.window().filter(window -> now >= window.detectedAt()
                        && now - window.detectedAt() < RECALL_TICKS).isPresent();
    }

    /** A successful recall starts fresh, so the old increase cannot immediately open another window. */
    public static State consume(State state) {
        return new State(state.owner(), List.of(), Optional.empty());
    }
}
