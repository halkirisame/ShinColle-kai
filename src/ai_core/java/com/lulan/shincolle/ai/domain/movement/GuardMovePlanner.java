package com.lulan.shincolle.ai.domain.movement;

import com.lulan.shincolle.ai.domain.DimensionKey;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * How guarding a point or an entity moves the ship: when it stops, re-paths, and teleports. The goal
 * ticks every entity tick; it calls {@link #count} and then {@link #move}, and reports the path
 * request's outcome through {@link #pathed}.
 */
public final class GuardMovePlanner {
    static final int FIRST_REPATH = 10;
    static final int REPATH = 32;
    static final int ANCHOR_RESOLVE = 8;
    static final double FORMATION_STALE_SQ = 6D;
    static final double TELEPORT_Y_OFFSET = 0.75D;
    /**
     * At a waypoint the route goes on from, the squared distances of a formation's block guard (two blocks and
     * a little more), inside the arrival range of nine, whatever FollowMin and FollowMax are.
     */
    static final double PASS_THROUGH_MIN_SQ = 4D;
    static final double PASS_THROUGH_MAX_SQ = 7D;

    private GuardMovePlanner() { }

    /** A new goal has no guarded point yet. */
    public static MovementState.Guard initial() {
        return new MovementState.Guard(0, 0, 0, 0, new MovementPoint(-1D, -1D, -1D),
                new MovementPoint(-1D, -100D, -1D), false);
    }

    public static MovementState.Guard start(MovementState.Guard state, int now) {
        return new MovementState.Guard(FIRST_REPATH, 0, 0, now, state.destination(), state.anchorMemory(),
                state.moving());
    }

    public static MovementState.Guard stopped(MovementState.Guard state) {
        return new MovementState.Guard(FIRST_REPATH, state.stillTimer(), state.farTimer(), state.anchorResolveAt(),
                state.destination(), state.anchorMemory(), false);
    }

    /**
     * @param formation   the ship stands in a formation
     * @param guardEntity it guards an entity rather than a point
     */
    public static FollowRange range(boolean formation, boolean guardEntity, boolean pickItem, int followMin,
                                    int followMax, float width) {
        if (!formation) return FollowRange.loose(followMin, followMax, width, pickItem);
        return guardEntity ? FollowRange.formation(5D, 9D, pickItem) : FollowRange.formation(4D, 7D, pickItem);
    }

    /**
     * The range of a guard that may stand at a waypoint the route goes on from. Away from one it is the range
     * above; at one it is tightened to the pass-through distances so the ship ends up where the traversal
     * counts it as arrived. An engaged ship keeps its start distance, which is the combat's allowed area, and
     * so does one that is picking an item up.
     *
     * @param passThrough the guarded point is a waypoint the route goes on from
     * @param engaged     the ship is engaged with a locked target
     * @param pickingItem the item pickup goal is running
     */
    public static FollowRange range(boolean formation, boolean guardEntity, boolean pickItem, int followMin,
                                    int followMax, float width, boolean passThrough, boolean engaged,
                                    boolean pickingItem) {
        FollowRange base = range(formation, guardEntity, pickItem, followMin, followMax, width);
        return passThrough ? base.passingThrough(engaged || pickingItem) : base;
    }

    /** In a formation the place is worked out again once the guarded entity has moved this far. */
    public static boolean formationStale(MovementState.Guard state, MovementPoint guarded) {
        return guarded.distanceSq(state.anchorMemory()) > FORMATION_STALE_SQ;
    }

    public static MovementState.Guard formationPlace(MovementState.Guard state, MovementPoint place,
                                                     MovementPoint guarded) {
        return new MovementState.Guard(state.repathIn(), state.stillTimer(), state.farTimer(),
                state.anchorResolveAt(), place, guarded, state.moving());
    }

    /** Outside a formation the ship walks to the guarded point or entity itself. */
    public static MovementState.Guard toPoint(MovementState.Guard state, MovementPoint point) {
        return new MovementState.Guard(state.repathIn(), state.stillTimer(), state.farTimer(),
                state.anchorResolveAt(), point, state.anchorMemory(), state.moving());
    }

    /**
     * The start of every goal tick: one tick nearer the next path, and one tick longer stuck while
     * the ship is not getting anywhere. Getting anywhere starts the stuck time over.
     */
    public static MovementState.Guard count(MovementState.Guard state, boolean stuck) {
        return new MovementState.Guard(state.repathIn() - 1, stuck ? state.stillTimer() + 1 : 0,
                state.farTimer(), state.anchorResolveAt(), state.destination(), state.anchorMemory(),
                state.moving());
    }

    public static boolean anchorResolveDue(MovementState.Guard state, int now) {
        return now >= state.anchorResolveAt();
    }

    public static MovementState.Guard anchorResolved(MovementState.Guard state, int now) {
        return new MovementState.Guard(state.repathIn(), state.stillTimer(), state.farTimer(), now + ANCHOR_RESOLVE,
                state.destination(), state.anchorMemory(), state.moving());
    }

    /** On its way while outside the inner distance, and under a move order until it arrives. */
    public static boolean travelling(double distanceSq, FollowRange range, boolean temporaryMove) {
        return temporaryMove || distanceSq > range.minSq();
    }

    /**
     * @param distanceSq    from the ship to its destination
     * @param temporaryMove a move order, which ends on arrival rather than stopping short of it
     * @param dimension     the destination's dimension
     * @param stuck         this goal tick's observation of the way there
     */
    public record Facts(double distanceSq, FollowRange range, boolean temporaryMove, DimensionKey dimension,
                        TeleportRule teleport, StuckState stuck) {
    }

    /**
     * Stop inside the inner distance; when stuck, re-path at once or try a point beside the
     * destination, otherwise re-path when due; then teleport to the destination once the ship has
     * been far away, or stuck, for longer than the cooldown.
     */
    public static PlannedMove<MovementState.Guard> move(MovementState.Guard state, Facts facts) {
        List<MovementStep> steps = new ArrayList<>();
        boolean moving = state.moving();
        if (!facts.temporaryMove() && facts.distanceSq() <= facts.range().minSq()) {
            moving = false;
            steps.add(new MovementStep.Stop(MovementBody.SELF, MovementReason.GUARD_ARRIVED));
        }
        int repathIn = state.repathIn();
        Optional<MovementStep.PathTo> recovery = StuckDetector.recovery(facts.stuck(), MovementBody.SELF,
                new MovementTarget.Point(state.destination()), state.destination(), 1D, Optional.empty());
        if (recovery.isPresent()) {
            repathIn = REPATH;
            steps.add(recovery.get());
        } else if (repathIn <= 0) {
            repathIn = REPATH;
            steps.add(new MovementStep.PathTo(MovementBody.SELF, new MovementTarget.Point(state.destination()), 1D,
                    MovementReason.GUARD));
        }
        int stillTimer = state.stillTimer();
        int farTimer = state.farTimer();
        TeleportRule rule = facts.teleport();
        if (rule.enabled()) {
            MovementPoint destination = state.destination();
            MovementPoint landing = new MovementPoint(destination.x(), destination.y() + TELEPORT_Y_OFFSET,
                    destination.z());
            boolean far = false;
            if (facts.distanceSq() > rule.distanceSq()) {
                farTimer++;
                if (farTimer > rule.cooldown()) {
                    farTimer = 0;
                    far = true;
                    steps.add(new MovementStep.Teleport(MovementBody.SELF, landing, MovementReason.TELEPORT_FAR,
                            facts.dimension()));
                }
            }
            if (!far && stillTimer > rule.cooldown()) {
                stillTimer = 0;
                steps.add(new MovementStep.Teleport(MovementBody.SELF, landing, MovementReason.TELEPORT_TIME,
                        facts.dimension()));
            }
        }
        return new PlannedMove<>(new MovementState.Guard(repathIn, stillTimer, farTimer, state.anchorResolveAt(),
                state.destination(), state.anchorMemory(), moving), new MovementPlan(steps));
    }

    /** A path request that was carried out sets whether the ship is on its way. */
    public static MovementState.Guard pathed(MovementState.Guard state, PathOutcome outcome) {
        if (outcome == PathOutcome.NONE) return state;
        return new MovementState.Guard(state.repathIn(), state.stillTimer(), state.farTimer(),
                state.anchorResolveAt(), state.destination(), state.anchorMemory(), outcome == PathOutcome.ISSUED);
    }
}
