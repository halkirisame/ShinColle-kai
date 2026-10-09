package com.lulan.shincolle.ai.domain.command;

public sealed interface RequestedCommand {
    record Attack(RequestedEntityRef target) implements RequestedCommand { }
    record GuardEntity(RequestedEntityRef target) implements RequestedCommand { }
    record Move(CommandPos position, boolean releaseOnArrival,
                boolean explicitArrivalMode) implements RequestedCommand { }
    record GuardPosition(CommandPos position, boolean releaseOnArrival,
                         boolean explicitArrivalMode) implements RequestedCommand { }
    record ToggleSit(RequestedEntityRef clickedShip) implements RequestedCommand { }
}
