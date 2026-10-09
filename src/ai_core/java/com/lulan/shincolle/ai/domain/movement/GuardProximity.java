package com.lulan.shincolle.ai.domain.movement;

import com.lulan.shincolle.ai.domain.command.CommandPos;
import com.lulan.shincolle.ai.domain.command.MovementOrder;

import java.util.Optional;

/**
 * Whether a host stands at the place its order keeps it at. A host there, under water with no air
 * above it, stays down instead of floating up.
 */
public final class GuardProximity {
    private GuardProximity() { }

    /**
     * Following counts up to the outer follow distance from the one followed; guarding counts inside the
     * inner one, from the guarded entity or from the corner of the ordered block, and a host below the
     * ordered block is never at it. Both distances are widened by half the host's width.
     *
     * @param width   the host's width
     * @param leader  the one a following host follows, empty when there is none to measure from
     * @param guarded the guarded entity, empty while it does not resolve
     */
    public static boolean inPlace(MovementOrder order, MovementSettings settings, float width, MovementPoint self,
                                  Optional<MovementPoint> leader, Optional<MovementPoint> guarded) {
        if (order instanceof MovementOrder.Follow) {
            float max = settings.followMax() + width * 0.5F;
            float maxSq = max * max;
            return leader.isPresent() && leader.get().distanceSq(self) <= maxSq;
        }
        float min = settings.followMin() + width * 0.5F;
        float minSq = min * min;
        if (order instanceof MovementOrder.GuardEntity) {
            return guarded.isPresent() && self.distanceSq(guarded.get()) < minSq;
        }
        Optional<CommandPos> ordered = order.destination();
        if (ordered.isEmpty()) return false;
        CommandPos block = ordered.get();
        double dx = self.x() - block.x();
        double dy = self.y() - block.y();
        double dz = self.z() - block.z();
        double distSq = dx * dx + dy * dy + dz * dz;
        return distSq < minSq && self.y() >= block.y();
    }
}
