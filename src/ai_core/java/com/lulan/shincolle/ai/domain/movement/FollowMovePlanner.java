package com.lulan.shincolle.ai.domain.movement;

import com.lulan.shincolle.ai.domain.DimensionKey;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * How following the owner moves the ship: when it stops, re-paths, and teleports. The goal calls
 * {@link #count} and then {@link #move} once per goal tick, which is every other entity tick.
 */
public final class FollowMovePlanner {
    static final int FIRST_REPATH = 10;
    static final int REPATH = 32;
    static final int OWNER_RESOLVE = 32;
    static final double FORMATION_STALE_SQ = 7D;
    static final double TELEPORT_Y_OFFSET = 0.75D;

    private FollowMovePlanner() { }

    /** A new goal walks to where the ship stands until it has looked up its owner. */
    public static MovementState.Follow initial(MovementPoint self) {
        return new MovementState.Follow(0, 0, 0, 0, self, self);
    }

    public static MovementState.Follow start(MovementState.Follow state, int now) {
        return new MovementState.Follow(FIRST_REPATH, 0, 0, now, state.destination(), state.anchorMemory(),
                state.destinationKind());
    }

    public static FollowRange range(boolean formation, boolean pickItem, int followMin, int followMax, float width) {
        return formation ? FollowRange.formation(4D, 7D, pickItem)
                : FollowRange.loose(followMin, followMax, width, pickItem);
    }

    /** In a formation the place is worked out again once the owner has moved this far from the last one. */
    public static boolean formationStale(MovementState.Follow state, MovementPoint owner) {
        return owner.distanceSq(state.anchorMemory()) > FORMATION_STALE_SQ;
    }

    /**
     * The formation slot worked out as the flagship's. A slot outside the table never gets here as
     * itself: where the raw value is read it is already placed as the flagship's.
     */
    public static boolean flagshipSlot(FormationSlot slot) {
        return slot.flagship();
    }

    /**
     * A formation place worked out for the owner standing at {@code owner}. The place is stored with what
     * it stands for: the flagship's place is a rounded block beside the owner, not the owner, and any other
     * slot's is a place that can be far from an owner who is near.
     */
    public static MovementState.Follow formationPlace(MovementState.Follow state, MovementPoint place,
                                                      MovementPoint owner, FormationSlot slot) {
        FollowDestination kind = flagshipSlot(slot) ? FollowDestination.FLAGSHIP_PLACE
                : FollowDestination.FORMATION_PLACE;
        return new MovementState.Follow(state.repathIn(), state.timeTimer(), state.farTimer(),
                state.ownerResolveAt(), place, owner, kind);
    }

    /** Outside a formation the ship walks to the owner itself. */
    public static MovementState.Follow toOwner(MovementState.Follow state, MovementPoint owner) {
        return new MovementState.Follow(state.repathIn(), state.timeTimer(), state.farTimer(),
                state.ownerResolveAt(), owner, state.anchorMemory(), FollowDestination.OWNER);
    }

    /**
     * The start of every goal tick: one tick nearer the next path, and one tick longer stuck while
     * the ship is not getting anywhere. Getting anywhere starts the stuck time over.
     */
    public static MovementState.Follow count(MovementState.Follow state, boolean stuck) {
        return new MovementState.Follow(state.repathIn() - 1, stuck ? state.timeTimer() + 1 : 0, state.farTimer(),
                state.ownerResolveAt(), state.destination(), state.anchorMemory(),
                state.destinationKind());
    }

    /** A recalled follower still uses the ordinary far condition and the executor's safety checks. */
    public static MovementState.Follow afterOwnerJump(MovementState.Follow state, double distanceSq, double ownerDistanceSq,
                                                      TeleportRule rule, long remainingEntityTicks) {
        if (!rule.enabled() || remainingEntityTicks > 0 || distanceSq <= rule.distanceSq()
                || ownerDistanceSq <= rule.distanceSq()) return state;
        return new MovementState.Follow(state.repathIn(), state.timeTimer(), Math.max(state.farTimer(), rule.cooldown()),
                state.ownerResolveAt(), state.destination(), state.anchorMemory(), state.destinationKind());
    }

    public static boolean ownerResolveDue(MovementState.Follow state, int now) {
        return now >= state.ownerResolveAt();
    }

    public static MovementState.Follow ownerResolved(MovementState.Follow state, int now) {
        return new MovementState.Follow(state.repathIn(), state.timeTimer(), state.farTimer(), now + OWNER_RESOLVE,
                state.destination(), state.anchorMemory(), state.destinationKind());
    }

    /** On its way while outside the inner distance. */
    public static boolean travelling(double distanceSq, FollowRange range) {
        return distanceSq > range.minSq();
    }

    /**
     * @param distanceSq from the ship to its destination
     * @param owner      where the owner stands, empty when it is unknown
     * @param dimension  the owner's dimension
     * @param stuck      this goal tick's observation of the way there
     * @param ownerDistanceSq from the ship to where the owner stands now, not as of the last look
     * @param flagshipPlaceDistanceSq from the ship to the flagship slot worked out for the owner as it stands
     *                        now; positive infinity when it was not worked out, which never counts as arrived
     */
    public record Facts(double distanceSq, FollowRange range, Optional<MovementPoint> owner, DimensionKey dimension,
                        TeleportRule teleport, StuckState stuck, double ownerDistanceSq,
                        double flagshipPlaceDistanceSq) {
        /** Facts whose distance was measured this tick, so the owner is as far as the destination. */
        public Facts(double distanceSq, FollowRange range, Optional<MovementPoint> owner, DimensionKey dimension,
                     TeleportRule teleport, StuckState stuck) {
            this(distanceSq, range, owner, dimension, teleport, stuck, distanceSq, Double.POSITIVE_INFINITY);
        }

        /** Facts whose flagship slot was not worked out for the owner as it stands now. */
        public Facts(double distanceSq, FollowRange range, Optional<MovementPoint> owner, DimensionKey dimension,
                     TeleportRule teleport, StuckState stuck, double ownerDistanceSq) {
            this(distanceSq, range, owner, dimension, teleport, stuck, ownerDistanceSq, Double.POSITIVE_INFINITY);
        }
    }

    /**
     * The stored distance may be up to a look old, so a destination that has since been reached may need no
     * teleport. What the destination stands for is read from the state, where it was written with it:
     * the owner is reached once the owner is inside the inner distance; the flagship slot, which is a rounded
     * block beside the owner, once the slot as worked out for the owner now is; any other formation place is
     * never judged by how near the owner is, since it can be far from an owner who is near.
     */
    static boolean ownerAlreadyArrivedAt(MovementState.Follow state, Facts facts) {
        double minSq = facts.range().minSq();
        return switch (state.destinationKind()) {
            case OWNER -> facts.ownerDistanceSq() <= minSq;
            case FLAGSHIP_PLACE -> facts.flagshipPlaceDistanceSq() <= minSq;
            case FORMATION_PLACE -> false;
        };
    }

    /**
     * Whether {@link #move} would reach the stuck teleport's check this tick, which is when the flagship slot
     * as worked out for the owner now is worth measuring: the stuck time is past the cooldown and the stored
     * destination is a flagship slot. The distance teleport is decided first and may take the tick, but that
     * is not known here, so the slot is measured whenever the stuck teleport could be reached.
     */
    public static boolean flagshipPlaceDistanceWanted(MovementState.Follow state, TeleportRule rule) {
        return rule.enabled() && state.destinationKind() == FollowDestination.FLAGSHIP_PLACE
                && state.timeTimer() > rule.cooldown();
    }

    /**
     * Stop inside the inner distance; when stuck, re-path at once or try a point beside the
     * destination, otherwise re-path when due; then teleport to the owner once the ship has been far
     * away, or stuck, for longer than the cooldown. The two teleports share one cooldown and the
     * distance one is checked first. The distance one reads the distance stored at the last look; the
     * stuck one, when the destination is the owner or the flagship slot, also asks that the owner, or the
     * slot as worked out now, is farther than the inner distance.
     */
    public static PlannedMove<MovementState.Follow> move(MovementState.Follow state, Facts facts) {
        List<MovementStep> steps = new ArrayList<>();
        if (facts.distanceSq() <= facts.range().minSq()) {
            steps.add(new MovementStep.Stop(MovementBody.SELF, MovementReason.FOLLOW_ARRIVED));
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
                    MovementReason.FOLLOW_OWNER));
        }
        int timeTimer = state.timeTimer();
        int farTimer = state.farTimer();
        TeleportRule rule = facts.teleport();
        if (rule.enabled() && facts.owner().isPresent()) {
            MovementPoint owner = facts.owner().get();
            MovementPoint landing = new MovementPoint(owner.x(), owner.y() + TELEPORT_Y_OFFSET, owner.z());
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
            if (!far && timeTimer > rule.cooldown()) {
                timeTimer = 0;
                if (!ownerAlreadyArrivedAt(state, facts)) {
                    steps.add(new MovementStep.Teleport(MovementBody.SELF, landing, MovementReason.TELEPORT_TIME,
                            facts.dimension()));
                }
            }
        }
        return new PlannedMove<>(new MovementState.Follow(repathIn, timeTimer, farTimer, state.ownerResolveAt(),
                state.destination(), state.anchorMemory(), state.destinationKind()),
                new MovementPlan(steps));
    }
}
