package com.lulan.shincolle.ai.domain.movement;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Judges whether a ship on its way is getting anywhere: every {@link #WINDOW} entity ticks it
 * compares where the ship stands with where it stood when the window opened. A goal observes once
 * per goal tick, before it plans.
 */
public final class StuckDetector {
    /** Entity ticks per window. */
    static final int WINDOW = 40;
    /** Moving less than one block in a window is not getting anywhere. */
    static final double PROGRESS_SQ = 1D;
    static final int GIVE_UP_WINDOWS = 3;
    /** A fight tries again this many windows after giving up. */
    static final int RETRY_WINDOWS = GIVE_UP_WINDOWS + 3;
    private static final int[] DETOUR_RADII = {2, 3};
    private static final int[][] DETOUR_DIRECTIONS = {{1, 0}, {0, 1}, {-1, 0}, {0, -1}, {1, 1}, {-1, 1}, {-1, -1},
            {1, -1}};

    private StuckDetector() { }

    /**
     * @param travelling the ship is on its way; when it is not, the state is dropped
     * @param at         where the ship stands
     * @param now        the entity tick
     */
    public static StuckState observe(StuckState state, boolean travelling, MovementPoint at, int now) {
        if (!travelling) return StuckState.NONE;
        if (!state.tracking()) return new StuckState(true, at, now, 0, false);
        if (now - state.checkAt() < WINDOW) {
            return state.fresh() ? new StuckState(true, state.checkPos(), state.checkAt(), state.stuckWindows(), false)
                    : state;
        }
        boolean progressed = at.distanceSq(state.checkPos()) >= PROGRESS_SQ;
        return progressed ? new StuckState(true, at, now, 0, false)
                : new StuckState(true, at, now, state.stuckWindows() + 1, true);
    }

    /**
     * The path a stuck ship tries now: the same one again, or one to a reachable point beside
     * {@code around}. Empty on any other observation.
     */
    public static Optional<MovementStep.PathTo> recovery(StuckState state, MovementBody body, MovementTarget target,
                                                         MovementPoint around, double speed,
                                                         Optional<MovementStep.Teleport> onFailure) {
        return switch (state.due()) {
            case REPATH -> Optional.of(new MovementStep.PathTo(body, target, speed, MovementReason.STUCK_REPATH,
                    onFailure));
            case DETOUR -> Optional.of(new MovementStep.PathTo(body, new MovementTarget.Around(around), speed,
                    MovementReason.STUCK_DETOUR, onFailure));
            default -> Optional.empty();
        };
    }

    /**
     * The points beside {@code center} a detour tries, in order: two blocks out and then three, each
     * east, south, west, north and then the diagonals. The executor takes the first it can reach.
     */
    public static List<MovementPoint> detourPoints(MovementPoint center) {
        List<MovementPoint> points = new ArrayList<>();
        for (int radius : DETOUR_RADII) {
            for (int[] direction : DETOUR_DIRECTIONS) {
                points.add(new MovementPoint(center.x() + direction[0] * radius, center.y(),
                        center.z() + direction[1] * radius));
            }
        }
        return points;
    }

    /** After giving up for a while a fight measures afresh, starting again from the first stage. */
    public static StuckState retryAfterGivingUp(StuckState state) {
        return state.stuckWindows() >= RETRY_WINDOWS ? StuckState.NONE : state;
    }
}
