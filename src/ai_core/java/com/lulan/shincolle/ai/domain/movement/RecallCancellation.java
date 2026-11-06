package com.lulan.shincolle.ai.domain.movement;

import com.lulan.shincolle.ai.domain.command.MovementOrder;
import com.lulan.shincolle.ai.domain.command.ShipCommandState;

/** Reasons an old departure may no longer issue or consume a recall. */
public enum RecallCancellation {
    COMMAND_CHANGED, OWNER_CHANGED, SHIP_DIED, SHIP_REMOVED, DIMENSION_CHANGED,
    OWNER_GONE, DISABLED, SERVER_STOPPING, ROSTER_EVICTED, INELIGIBLE, ARRIVED;

    public static boolean commandCancels(ShipCommandState accepted) {
        return accepted.sitting() || !(accepted.movement() instanceof MovementOrder.Follow);
    }
}
