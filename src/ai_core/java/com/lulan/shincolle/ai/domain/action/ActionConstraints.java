package com.lulan.shincolle.ai.domain.action;

public record ActionConstraints(ActionPermission movement, ActionPermission targetAcquisition,
                                ActionPermission retaliation, ActionPermission firing,
                                ActionPermission manualCommand) {
    public ActionPermission get(ActionKind kind) {
        return switch (kind) {
            case MOVEMENT -> this.movement;
            case TARGET_ACQUISITION -> this.targetAcquisition;
            case RETALIATION -> this.retaliation;
            case FIRING -> this.firing;
            case MANUAL_COMMAND -> this.manualCommand;
        };
    }

    public boolean allows(ActionKind kind) {
        return get(kind).allowed();
    }
}
