package com.lulan.shincolle.ai.domain.crane;

import com.lulan.shincolle.ai.domain.movement.MovementIntent;

import java.util.EnumSet;
import java.util.Set;

/** Whether an uncaptured waiting ship still needs to enter the crane's capture volume. */
public final class CraneEntryApproach {
    public enum StopReason {
        NOT_WAITING, CAPTURED, IN_CAPTURE_AREA, INVALID_DESTINATION, SITTING, LEASHED, RIDING, NO_FUEL,
        OTHER_MOVEMENT_INTENT
    }

    /** World-space bounds copied at the Minecraft boundary; touching faces do not intersect. */
    public record Bounds(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        public boolean intersects(Bounds other) {
            return minX < other.maxX && maxX > other.minX
                    && minY < other.maxY && maxY > other.minY
                    && minZ < other.maxZ && maxZ > other.minZ;
        }
    }

    public record Decision(Set<StopReason> stopReasons) {
        public Decision {
            stopReasons = Set.copyOf(stopReasons);
        }

        public boolean approach() {
            return stopReasons.isEmpty();
        }
    }

    private CraneEntryApproach() { }

    public static Decision decide(CraneLifecycle.ArrivalFacts facts, boolean hostRiding, boolean fueled,
                                  boolean captured, MovementIntent standing, Bounds body, Bounds capture) {
        Set<StopReason> reasons = EnumSet.noneOf(StopReason.class);
        if (facts.phase() != CraneLifecycle.Phase.WAITING) reasons.add(StopReason.NOT_WAITING);
        if (captured) reasons.add(StopReason.CAPTURED);
        if (body.intersects(capture)) reasons.add(StopReason.IN_CAPTURE_AREA);
        if (!CraneLifecycle.destinationValid(facts)) reasons.add(StopReason.INVALID_DESTINATION);
        if (facts.sitting()) reasons.add(StopReason.SITTING);
        if (facts.leashed()) reasons.add(StopReason.LEASHED);
        if (hostRiding || facts.riding() && !facts.ridingMount()) reasons.add(StopReason.RIDING);
        if (!fueled) reasons.add(StopReason.NO_FUEL);
        if (!(standing instanceof MovementIntent.GuardPosition || standing instanceof MovementIntent.MoveTo)) {
            reasons.add(StopReason.OTHER_MOVEMENT_INTENT);
        }
        return new Decision(reasons);
    }
}
