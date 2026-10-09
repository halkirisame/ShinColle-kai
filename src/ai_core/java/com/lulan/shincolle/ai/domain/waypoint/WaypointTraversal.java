package com.lulan.shincolle.ai.domain.waypoint;

import com.lulan.shincolle.ai.domain.command.CommandPos;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

/** Decides whether a ship that stands at a waypoint waits there or moves on to the next. */
public final class WaypointTraversal {
    /** Ticks between two checks; a wait counts in steps of this. */
    public static final int CHECK_INTERVAL = 16;
    /** A ship counts as arrived within this squared distance of the waypoint's centre. */
    public static final double ARRIVAL_DISTANCE_SQ = 9D;

    private WaypointTraversal() {
    }

    /**
     * The waypoint a ship passes through: the one it guards when it takes part in the traversal and the route
     * goes on from it. The last point of a route, a point that is no waypoint and a ship that takes no part
     * have none.
     */
    public static Optional<CommandPos> passThrough(WaypointFacts facts) {
        if (!entryReasons(facts).isEmpty() || !facts.currentIsWaypoint() || facts.links().next().isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(facts.current());
    }

    private static Set<WaypointSkipReason> entryReasons(WaypointFacts facts) {
        Set<WaypointSkipReason> reasons = EnumSet.noneOf(WaypointSkipReason.class);
        if (!facts.blockGuard()) reasons.add(WaypointSkipReason.NO_BLOCK_GUARD);
        if (!facts.sameDimension()) reasons.add(WaypointSkipReason.OTHER_DIMENSION);
        if (facts.guardingEntity()) reasons.add(WaypointSkipReason.GUARDING_ENTITY);
        if (facts.sitting()) reasons.add(WaypointSkipReason.SITTING);
        if (facts.leashed()) reasons.add(WaypointSkipReason.LEASHED);
        if (facts.riding() && !facts.ridingMount()) reasons.add(WaypointSkipReason.RIDING);
        if (facts.formationMember()) reasons.add(WaypointSkipReason.FORMATION_MEMBER);
        return reasons;
    }

    public static WaypointStep step(WaypointFacts facts) {
        Set<WaypointSkipReason> reasons = entryReasons(facts);
        if (!reasons.isEmpty()) return new WaypointStep.Skip(reasons);

        if (!facts.currentIsWaypoint()) reasons.add(WaypointSkipReason.NOT_A_WAYPOINT);
        if (facts.bodyDistanceSqToCenter() >= ARRIVAL_DISTANCE_SQ) reasons.add(WaypointSkipReason.NOT_ARRIVED);
        if (!reasons.isEmpty()) return new WaypointStep.Skip(reasons);

        WaypointProgress progress = facts.progress();
        // A wait counts only where it began: a stay carried from another point starts over.
        int elapsed = progress.stay().filter(stay -> stay.at().equals(facts.current()))
                .map(WaypointStay::elapsedTicks).orElse(0);
        int stayMax = Math.max(facts.shipStayTicks(), facts.waypointStayTicks());
        if (elapsed < stayMax) {
            return new WaypointStep.Wait(new WaypointProgress(progress.lastWaypoint(),
                    Optional.of(new WaypointStay(facts.current(), elapsed + CHECK_INTERVAL))));
        }

        WaypointProgress done = new WaypointProgress(Optional.of(facts.current()), Optional.empty());
        Optional<CommandPos> next = facts.links().next();
        if (next.isEmpty()) return new WaypointStep.Hold(done);
        Optional<CommandPos> destination = next;
        if (progress.lastWaypoint().isPresent() && next.get().equals(progress.lastWaypoint().get())
                && facts.links().last().isPresent()) {
            destination = facts.links().last();
        }
        return new WaypointStep.Advance(destination.get(), done);
    }
}
