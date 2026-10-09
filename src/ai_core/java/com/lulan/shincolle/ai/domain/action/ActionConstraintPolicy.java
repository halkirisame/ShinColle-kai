package com.lulan.shincolle.ai.domain.action;

import java.util.EnumSet;
import java.util.Set;

public final class ActionConstraintPolicy {
    private ActionConstraintPolicy() { }

    /**
     * An exhausted or dead ship does nothing at all, as the original did by clearing its AI.
     * A body steered by a player keeps every action except its own movement.
     */
    public static ActionConstraints evaluate(ActionFacts facts) {
        Set<ActionInhibitReason> all = EnumSet.noneOf(ActionInhibitReason.class);
        if (facts.noFuel()) all.add(ActionInhibitReason.NO_FUEL);
        if (facts.dead()) all.add(ActionInhibitReason.DEAD);
        Set<ActionInhibitReason> movement = EnumSet.noneOf(ActionInhibitReason.class);
        movement.addAll(all);
        if (facts.playerControlled()) movement.add(ActionInhibitReason.PLAYER_CONTROL);
        ActionPermission everything = ActionPermission.inhibited(all);
        return new ActionConstraints(ActionPermission.inhibited(movement), everything, everything, everything,
                everything);
    }
}
