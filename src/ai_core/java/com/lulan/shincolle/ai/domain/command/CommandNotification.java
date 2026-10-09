package com.lulan.shincolle.ai.domain.command;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class CommandNotification {

    private static final List<CommandRejectReason> REASON_ORDER = List.of(
            CommandRejectReason.OUT_OF_RANGE,
            CommandRejectReason.OTHER_DIMENSION,
            CommandRejectReason.NOT_FOUND,
            CommandRejectReason.NOT_OWNED,
            CommandRejectReason.SUNK);

    private CommandNotification() { }

    public static Summary summarize(CommandDispatchResult result, List<SunkLocation> sunkLocations) {
        EnumMap<CommandRejectReason, Integer> counts = new EnumMap<>(CommandRejectReason.class);
        Set<Integer> sunkSlots = new HashSet<>();
        boolean formationUnsatisfied = false;
        for (RejectedEntry entry : result.rejected()) {
            CommandRejectReason reason = entry.reason();
            if (reason == CommandRejectReason.NO_FUEL) {
                continue;
            }
            counts.merge(reason, 1, Integer::sum);
            formationUnsatisfied |= reason == CommandRejectReason.FORMATION_UNSATISFIED;
            if (reason == CommandRejectReason.SUNK) {
                sunkSlots.add(entry.recipient().slot());
            }
        }

        List<ReasonCount> reasons = new ArrayList<>();
        for (CommandRejectReason reason : REASON_ORDER) {
            int count = counts.getOrDefault(reason, 0);
            if (count > 0) {
                reasons.add(new ReasonCount(reason, count));
            }
        }
        int sunkCount = counts.getOrDefault(CommandRejectReason.SUNK, 0);
        SunkSummary sunk = null;
        if (sunkCount > 0) {
            SunkLocation first = sunkLocations.stream()
                    .filter(location -> sunkSlots.contains(location.slot()))
                    .min(Comparator.comparingInt(SunkLocation::slot)).orElse(null);
            sunk = new SunkSummary(sunkCount, first, sunkCount > 1);
        }

        return new Summary(reasons, formationUnsatisfied,
                result.accepted().isEmpty() && result.rejected().isEmpty(), sunk);
    }

    public record ReasonCount(CommandRejectReason reason, int count) { }

    public record SunkLocation(int slot, int shipUid, int x, int y, int z) { }

    public record SunkSummary(int count, SunkLocation first, boolean more) { }

    public record Summary(List<ReasonCount> reasons, boolean formationUnsatisfied,
                          boolean noRecipient, SunkSummary sunk) {
        public Summary {
            reasons = List.copyOf(reasons);
        }
    }
}
