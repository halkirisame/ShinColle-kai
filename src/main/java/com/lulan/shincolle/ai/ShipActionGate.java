package com.lulan.shincolle.ai;

import com.lulan.shincolle.ai.domain.action.ActionConstraintPolicy;
import com.lulan.shincolle.ai.domain.action.ActionConstraints;
import com.lulan.shincolle.ai.domain.action.ActionFacts;
import com.lulan.shincolle.ai.domain.action.ActionKind;
import com.lulan.shincolle.entity.BasicEntityMount;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.reference.ID;
import net.minecraft.world.entity.player.Player;

/**
 * Single entry point through which friendly-ship goals ask whether an action is currently
 * forbidden. Goals stay registered while the constraints stop their actions.
 */
public final class ShipActionGate {
    private ShipActionGate() { }

    public static boolean blocked(Object host, ActionKind kind) {
        BasicEntityShip ship = host instanceof BasicEntityShip s ? s
                : host instanceof BasicEntityMount mount ? mount.getHost() : null;
        if (ship == null) return false;
        boolean playerControlled = host instanceof BasicEntityMount mount
                && mount.getControllingPassenger() instanceof Player;
        return !constraints(ship, playerControlled).allows(kind);
    }

    public static ActionConstraints constraints(BasicEntityShip ship, boolean playerControlled) {
        return ActionConstraintPolicy.evaluate(new ActionFacts(
                ship.getStateFlag(ID.F.NoFuel), ship.isDeadOrDying(), playerControlled));
    }
}
