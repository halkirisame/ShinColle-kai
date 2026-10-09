package com.lulan.shincolle.ai.domain.movement;

import com.lulan.shincolle.ai.domain.TargetHandle;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * How picking items up moves the ship: a path to the nearest item every 16 ticks, a stop on reaching
 * it. Stuck on the way it re-paths at once, then tries a point beside the item, then gives the item
 * up for a while.
 */
public final class PickItemMovePlanner {
    static final int SCAN = 16;
    /** Within this squared distance the goal takes the item. */
    static final double REACH_SQ = 9D;
    /** Entity ticks an item given up on is left alone. */
    static final int ABANDON = 600;
    /** Entity ticks of failed takes in a row, item in reach, before it is given up on; outlasts an ordinary pickup delay (40). */
    static final int TAKE_GRACE = 80;
    /** Entity ticks without a failed take after which the failures are no longer in a row. */
    static final int TAKE_GAP = 40;

    private PickItemMovePlanner() { }

    public static MovementState.PickItem initial() {
        return new MovementState.PickItem(0, Map.of(), Optional.empty(), 0, 0);
    }

    /** The scan starts now; items given up on stay given up on, failed takes start over. */
    public static MovementState.PickItem start(MovementState.PickItem state, int now) {
        return new MovementState.PickItem(now, state.abandoned(), Optional.empty(), 0, 0);
    }

    public static boolean scanDue(MovementState.PickItem state, int now) {
        return now >= state.scanAt();
    }

    public static MovementState.PickItem scanned(MovementState.PickItem state, int now) {
        return new MovementState.PickItem(now + SCAN, state.abandoned(), state.failing(),
                state.failingSince(), state.failingLast());
    }

    /** Whether {@code item} may be chosen now. */
    public static boolean choosable(MovementState.PickItem state, TargetHandle item, int now) {
        return now >= state.abandoned().getOrDefault(item, 0);
    }

    /** A take of {@code item}, in reach, failed: the failures in a row on that one item go on, or start. */
    public static MovementState.PickItem failedTake(MovementState.PickItem state, TargetHandle item, int now) {
        boolean goesOn = state.failing().filter(item::equals).isPresent() && now - state.failingLast() <= TAKE_GAP;
        return new MovementState.PickItem(state.scanAt(), state.abandoned(), Optional.of(item),
                goesOn ? state.failingSince() : now, now);
    }

    /** A take succeeded: failures in a row start over. */
    public static MovementState.PickItem tookItem(MovementState.PickItem state) {
        return new MovementState.PickItem(state.scanAt(), state.abandoned(), Optional.empty(), 0, 0);
    }

    /** Whether {@code item} has failed takes in a row for long enough, in reach, to mean it cannot be taken. */
    public static boolean untakeable(MovementState.PickItem state, TargetHandle item, int now) {
        return state.failing().filter(item::equals).isPresent() && now - state.failingSince() >= TAKE_GRACE;
    }

    /**
     * {@code item} cannot be taken: it is left alone for a while, as one given up on while stuck. Every
     * item given up on keeps its own time.
     */
    public static MovementState.PickItem abandon(MovementState.PickItem state, TargetHandle item, int now) {
        Map<TargetHandle, Integer> abandoned = new HashMap<>(state.abandoned());
        abandoned.values().removeIf(until -> now >= until);
        abandoned.put(item, now + ABANDON);
        return new MovementState.PickItem(state.scanAt(), abandoned, Optional.empty(), 0, 0);
    }

    /** On its way while it has an item it is not yet within reach of. */
    public static boolean travelling(boolean hasItem, double itemDistanceSq) {
        return hasItem && itemDistanceSq >= REACH_SQ;
    }

    public static MovementPlan toItem(TargetHandle item) {
        return MovementPlan.of(new MovementStep.PathTo(MovementBody.SELF, new MovementTarget.Entity(item), 1D,
                MovementReason.PICK_ITEM));
    }

    /** Within reach of the item, whether or not it could be taken. */
    public static MovementPlan reached() {
        return MovementPlan.of(new MovementStep.Stop(MovementBody.SELF, MovementReason.PICK_ITEM_REACHED));
    }

    /**
     * What a stuck ship does about {@code item} on this goal tick: nothing unless a stuck window has
     * just closed; then re-path, try beside it, or give it up and stop.
     *
     * @param at where the item lies
     */
    public static PlannedMove<MovementState.PickItem> recover(MovementState.PickItem state, StuckState stuck,
                                                            TargetHandle item, MovementPoint at, int now) {
        if (stuck.due() == StuckStage.GIVE_UP) {
            return new PlannedMove<>(abandon(state, item, now),
                    MovementPlan.of(new MovementStep.Stop(MovementBody.SELF, MovementReason.STUCK_GIVE_UP)));
        }
        return new PlannedMove<>(state, StuckDetector.recovery(stuck, MovementBody.SELF,
                new MovementTarget.Entity(item), at, 1D, Optional.empty())
                .<MovementPlan>map(MovementPlan::of).orElse(MovementPlan.NONE));
    }
}
