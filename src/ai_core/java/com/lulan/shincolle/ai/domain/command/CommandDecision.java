package com.lulan.shincolle.ai.domain.command;

import com.lulan.shincolle.ai.domain.DimensionKey;
import com.lulan.shincolle.ai.domain.TargetHandle;

public final class CommandDecision {
    private CommandDecision() { }

    public static ShipCommand attack(TargetHandle target, TargetHandle current) {
        return target.equals(current) ? new ShipCommand.CancelAttack() : new ShipCommand.Attack(target);
    }

    public static ShipCommand guardEntity(TargetHandle target, TargetHandle current) {
        return target.equals(current) ? new ShipCommand.Follow() : new ShipCommand.GuardEntity(target);
    }

    public static ShipCommand position(DimensionKey dimension, CommandPos position, boolean releaseOnArrival,
                                       boolean changeArrivalMode, boolean hasDestination,
                                       DimensionKey currentDimension, CommandPos currentPosition) {
        return position(dimension, position, releaseOnArrival, changeArrivalMode, hasDestination,
                currentDimension, currentPosition, false);
    }

    public static ShipCommand position(DimensionKey dimension, CommandPos position, boolean releaseOnArrival,
                                       boolean changeArrivalMode, boolean hasDestination,
                                       DimensionKey currentDimension, CommandPos currentPosition,
                                       boolean guardPosition) {
        if (!changeArrivalMode && hasDestination && dimension.equals(currentDimension)
                && position.equals(currentPosition)) {
            return new ShipCommand.Follow();
        }
        return guardPosition ? new ShipCommand.GuardPosition(dimension, position, releaseOnArrival)
                : new ShipCommand.Move(dimension, position, releaseOnArrival);
    }
}
