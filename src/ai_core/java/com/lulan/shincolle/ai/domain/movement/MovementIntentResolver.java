package com.lulan.shincolle.ai.domain.movement;

import com.lulan.shincolle.ai.domain.command.MovementOrder;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Decides the movement intent once, in the order the original goal priorities gave it:
 * sit, then flee, then the standing order. Each activity keeps the set of inhibitors its
 * original goal checked, except that a leash now stops every commanded move and a fleeing
 * ship stays beside its owner instead of wandering off.
 */
public final class MovementIntentResolver {
    /** The original flee goal never ran for an owner this far away. */
    static final double FLEE_MAX_DISTANCE_SQ = 3600D;

    private MovementIntentResolver() { }

    public static MovementDecision resolve(MovementFacts facts) {
        MovementIntent intent = intent(facts);
        Map<MovementActivity, MovementPermission> permissions = new EnumMap<>(MovementActivity.class);
        permissions.put(MovementActivity.COMMANDED_MOVE, commandedMove(facts));
        boolean fleeing = intent instanceof MovementIntent.Flee;
        permissions.put(MovementActivity.WANDER, wander(facts, fleeing));
        permissions.put(MovementActivity.PICK_ITEM, pickItem(facts, fleeing));
        permissions.put(MovementActivity.IDLE_LOOK, idleLook(facts));
        return new MovementDecision(intent, permissions);
    }

    static MovementIntent intent(MovementFacts facts) {
        if (facts.orderedToSit()) return new MovementIntent.Sit();
        if (flees(facts)) return new MovementIntent.Flee();
        MovementOrder order = facts.order();
        if (order instanceof MovementOrder.MoveTo move) {
            return new MovementIntent.MoveTo(move.dimension(), move.position());
        }
        if (order instanceof MovementOrder.GuardPosition guard) {
            return guard.releaseOnArrival()
                    ? new MovementIntent.MoveTo(guard.dimension(), guard.position())
                    : new MovementIntent.GuardPosition(guard.dimension(), guard.position());
        }
        if (order instanceof MovementOrder.GuardEntity guard) {
            return new MovementIntent.GuardEntity(guard.target());
        }
        return new MovementIntent.FollowOwner();
    }

    private static boolean flees(MovementFacts facts) {
        return !facts.hostIsMount() && !facts.leashed() && facts.hasGrudge() && facts.ownerPresent()
                && facts.hpRatio() <= facts.fleeThreshold()
                && facts.ownerDistanceSq() < FLEE_MAX_DISTANCE_SQ;
    }

    private static MovementPermission commandedMove(MovementFacts facts) {
        Set<MovementInhibitReason> reasons = EnumSet.noneOf(MovementInhibitReason.class);
        if (facts.sittingPose()) reasons.add(MovementInhibitReason.SITTING);
        if (facts.riding()) reasons.add(MovementInhibitReason.RIDING);
        if (facts.crane()) reasons.add(MovementInhibitReason.CRANE);
        if (!facts.hasGrudge()) reasons.add(MovementInhibitReason.NO_GRUDGE);
        if (facts.leashed()) reasons.add(MovementInhibitReason.LEASHED);
        return MovementPermission.inhibited(reasons);
    }

    private static MovementPermission wander(MovementFacts facts, boolean fleeing) {
        Set<MovementInhibitReason> reasons = EnumSet.noneOf(MovementInhibitReason.class);
        if (fleeing) reasons.add(MovementInhibitReason.FLEEING);
        if (facts.orderedToSit()) reasons.add(MovementInhibitReason.SITTING);
        if (facts.riding()) reasons.add(MovementInhibitReason.RIDING);
        if (facts.fishing()) reasons.add(MovementInhibitReason.FISHING);
        if (facts.crane()) reasons.add(MovementInhibitReason.CRANE);
        return MovementPermission.inhibited(reasons);
    }

    private static MovementPermission pickItem(MovementFacts facts, boolean fleeing) {
        Set<MovementInhibitReason> reasons = EnumSet.noneOf(MovementInhibitReason.class);
        if (fleeing) reasons.add(MovementInhibitReason.FLEEING);
        if (facts.orderedToSit()) reasons.add(MovementInhibitReason.SITTING);
        if (facts.riding()) reasons.add(MovementInhibitReason.RIDING);
        if (!facts.pickItemEnabled()) reasons.add(MovementInhibitReason.PICK_ITEM_OFF);
        if (facts.crane()) reasons.add(MovementInhibitReason.CRANE);
        if (facts.fishing()) reasons.add(MovementInhibitReason.FISHING);
        return MovementPermission.inhibited(reasons);
    }

    private static MovementPermission idleLook(MovementFacts facts) {
        Set<MovementInhibitReason> reasons = EnumSet.noneOf(MovementInhibitReason.class);
        if (facts.ridingShip()) reasons.add(MovementInhibitReason.RIDING_SHIP);
        if (facts.engaged()) reasons.add(MovementInhibitReason.ENGAGED);
        return MovementPermission.inhibited(reasons);
    }
}
