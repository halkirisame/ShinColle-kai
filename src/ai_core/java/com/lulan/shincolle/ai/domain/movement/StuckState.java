package com.lulan.shincolle.ai.domain.movement;

import java.util.Objects;

/**
 * Whether a ship on its way is getting anywhere, as {@link StuckDetector} last judged it. Held by
 * the movement goal next to its movement state and dropped whenever the ship stops being on its
 * way. Server only, never saved.
 *
 * @param tracking     the ship is on its way and this state measures it
 * @param checkPos     where the ship stood when the current window opened
 * @param checkAt      the entity tick the current window opened
 * @param stuckWindows windows in a row that closed without the ship getting anywhere
 * @param fresh        a window closed stuck on this very observation
 */
public record StuckState(boolean tracking, MovementPoint checkPos, int checkAt, int stuckWindows, boolean fresh) {
    public static final StuckState NONE = new StuckState(false, new MovementPoint(0D, 0D, 0D), 0, 0, false);

    public StuckState {
        Objects.requireNonNull(checkPos, "checkPos");
    }

    /** The ship has not got anywhere in the last window at least. */
    public boolean stuck() {
        return this.stuckWindows > 0;
    }

    /** It is stuck for long enough that giving up is due. */
    public boolean gaveUp() {
        return this.stuckWindows >= StuckDetector.GIVE_UP_WINDOWS;
    }

    /** The recovery to carry out now: a stage only on the observation that closed a stuck window. */
    public StuckStage due() {
        if (!this.fresh) return StuckStage.NONE;
        return StuckStage.of(this.stuckWindows);
    }
}
