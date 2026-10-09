package com.lulan.shincolle.ai.domain.command;

import java.util.List;

public final class FormationGate {
    private FormationGate() { }

    public static boolean move(int activeCount, List<CommandRejectReason> failures, boolean mismatchedFormation) {
        return activeCount >= 5 && !mismatchedFormation
                && failures.stream().allMatch(reason -> reason == CommandRejectReason.NO_FUEL);
    }

    public static boolean guardEntity(int loadedShipCount) {
        return loadedShipCount > 4;
    }

    public static CommandRejectReason rejection(CommandRejectReason prior) {
        return prior == CommandRejectReason.NO_FUEL ? prior : CommandRejectReason.FORMATION_UNSATISFIED;
    }
}
