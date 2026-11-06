package com.lulan.shincolle.ai.domain.task;

import com.lulan.shincolle.ai.domain.action.ActionInhibitReason;
import java.util.EnumSet;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;

/** Value-only rules for ship resource rewards and the independent liquid and experience pump. */
public final class ShipResourceRules {
    private ShipResourceRules() { }

    public enum InhibitReason { DISABLED, SITTING, STOPPED, DEAD, NO_FUEL, MISSING_DRUM }

    /** Stored units from one all-or-nothing pickup, not an experience multiplier. */
    public record PickupResult(int storedCount) {
        public PickupResult {
            if (storedCount < 0) throw new IllegalArgumentException("Stored item count must be nonnegative");
        }
    }

    public record PumpFacts(boolean enabled, boolean sitting, boolean stopped,
                            Set<ActionInhibitReason> actionReasons, boolean transport, boolean married,
                            boolean fluidDrum, int level, int tick) {
        public PumpFacts {
            actionReasons = Set.copyOf(actionReasons);
        }
    }

    public record Decision(Set<InhibitReason> reasons, boolean liquidDue, boolean xpDue) {
        public Decision {
            reasons = Set.copyOf(reasons);
        }

        public boolean allowed() { return reasons.isEmpty(); }
    }

    public static Decision decide(PumpFacts facts) {
        Set<InhibitReason> reasons = EnumSet.noneOf(InhibitReason.class);
        if (!facts.enabled()) reasons.add(InhibitReason.DISABLED);
        if (facts.sitting()) reasons.add(InhibitReason.SITTING);
        if (facts.stopped()) reasons.add(InhibitReason.STOPPED);
        if (facts.actionReasons().contains(ActionInhibitReason.DEAD)) reasons.add(InhibitReason.DEAD);
        if (facts.actionReasons().contains(ActionInhibitReason.NO_FUEL)) reasons.add(InhibitReason.NO_FUEL);
        if (!(facts.transport() && facts.married()) && !facts.fluidDrum()) reasons.add(InhibitReason.MISSING_DRUM);
        int mask = facts.level() >= 145 ? 3 : facts.level() >= 115 ? 7 : facts.level() >= 75 ? 15
                : facts.level() >= 30 ? 31 : 63;
        return new Decision(reasons, reasons.isEmpty() && (facts.tick() & mask) == 0,
                reasons.isEmpty() && (facts.tick() & 3) == 0);
    }

    public static boolean infinite(int depth, List<Boolean> columns) {
        return depth > 0 && columns.size() == 3 && columns.stream().allMatch(Boolean::booleanValue);
    }

    public static int liquidReward(boolean committed, boolean transport, int configuredExperience) {
        return committed && transport ? configuredExperience : 0;
    }

    public static int pickupReward(PickupResult result, int configuredExperience) {
        return result.storedCount() > 0 ? configuredExperience : 0;
    }

    public static OptionalInt collectedXp(int remainder, int value, int count) {
        if (remainder < 0 || value <= 0 || count <= 0) return OptionalInt.empty();
        long total = (long) remainder + (long) value * count;
        return total <= Integer.MAX_VALUE ? OptionalInt.of((int) total) : OptionalInt.empty();
    }

    public static boolean canBottle(int remainder) { return remainder >= 8; }
}
