package com.lulan.shincolle.ai.domain.command;

/** Ownership of position guards accepted during an enabled fishing task. */
public final class FishingStationPolicy {
    private FishingStationPolicy() { }

    public static ShipCommand accepted(ShipCommand command, boolean fishingActive) {
        if (command instanceof ShipCommand.GuardPosition guard) {
            return new ShipCommand.GuardPosition(guard.dimension(), guard.position(),
                    guard.releaseOnArrival(), fishingActive);
        }
        return command;
    }

    public static boolean shouldEnd(MovementOrder order, boolean fishingActive) {
        return !fishingActive && order instanceof MovementOrder.GuardPosition guard && guard.fishingStation();
    }
}
