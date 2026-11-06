package com.lulan.shincolle.ai.domain.command;

import java.util.List;

public final class FormationGate {
    private static final int MIN_FORMATION_MEMBERS = 5;

    private FormationGate() { }

    public static boolean move(int activeCount, List<CommandRejectReason> failures, boolean mismatchedFormation) {
        return activeCount >= MIN_FORMATION_MEMBERS && !mismatchedFormation;
    }

    public static boolean guardEntity(int loadedShipCount) {
        return loadedShipCount > 4;
    }

    public static CommandRejectReason rejection(CommandRejectReason prior) {
        return prior != null ? prior : CommandRejectReason.FORMATION_UNSATISFIED;
    }
}
