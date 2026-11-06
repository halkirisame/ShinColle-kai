package com.lulan.shincolle.ai.domain.command;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public final class CommandNotification {

    private static final List<CommandRejectReason> REASON_ORDER = List.of(
            CommandRejectReason.OUT_OF_RANGE,
            CommandRejectReason.OTHER_DIMENSION,
            CommandRejectReason.NOT_FOUND,
            CommandRejectReason.NOT_OWNED,
            CommandRejectReason.SUNK,
            CommandRejectReason.NO_FUEL);

    private CommandNotification() { }

    public static Summary summarize(CommandDispatchResult result, List<SunkLocation> sunkLocations) {
        return summarize(result, sunkLocations, false);
    }

    public static Summary summarize(CommandDispatchResult result, List<SunkLocation> sunkLocations,
                                    boolean formationMove) {
        EnumMap<CommandRejectReason, Integer> counts = new EnumMap<>(CommandRejectReason.class);
        EnumMap<CommandRejectReason, Set<Integer>> slots = new EnumMap<>(CommandRejectReason.class);
        Set<Integer> sunkSlots = new HashSet<>();
        boolean formationUnsatisfied = false;
        for (RejectedEntry entry : result.rejected()) {
            CommandRejectReason reason = entry.reason();
            if (reason == CommandRejectReason.NO_FUEL && !formationMove) {
                continue;
            }
            counts.merge(reason, 1, Integer::sum);
            if (formationMove && entry.recipient().slot() >= 0) {
                slots.computeIfAbsent(reason, ignored -> new TreeSet<>()).add(entry.recipient().slot() + 1);
            }
            formationUnsatisfied |= reason == CommandRejectReason.FORMATION_UNSATISFIED;
            if (reason == CommandRejectReason.SUNK) {
                sunkSlots.add(entry.recipient().slot());
            }
        }

        List<ReasonCount> reasons = new ArrayList<>();
        for (CommandRejectReason reason : REASON_ORDER) {
            int count = counts.getOrDefault(reason, 0);
            if (count > 0) {
                reasons.add(new ReasonCount(reason, count, List.copyOf(slots.getOrDefault(reason, Set.of()))));
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

    public record ReasonCount(CommandRejectReason reason, int count, List<Integer> slots) {
        public ReasonCount {
            slots = List.copyOf(slots);
        }

        public ReasonCount(CommandRejectReason reason, int count) {
            this(reason, count, List.of());
        }
    }

    public record SunkLocation(int slot, int shipUid, int x, int y, int z) { }

    public record SunkSummary(int count, SunkLocation first, boolean more) { }

    public record Summary(List<ReasonCount> reasons, boolean formationUnsatisfied,
                          boolean noRecipient, SunkSummary sunk) {
        public Summary {
            reasons = List.copyOf(reasons);
        }
    }
}
