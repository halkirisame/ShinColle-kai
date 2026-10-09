package com.lulan.shincolle.ai.domain.action;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ActionConstraintPolicyTest {
    @Test
    void fuelledLivingShipMayDoEverything() {
        ActionConstraints constraints = ActionConstraintPolicy.evaluate(new ActionFacts(false, false, false));
        for (ActionKind kind : ActionKind.values()) {
            assertEquals(ActionPermission.ALLOWED, constraints.get(kind), kind.name());
        }
    }

    @Test
    void noFuelOrDeathForbidsEveryActionWithItsReasons() {
        assertEveryKind(new ActionFacts(true, false, false), Set.of(ActionInhibitReason.NO_FUEL));
        assertEveryKind(new ActionFacts(false, true, false), Set.of(ActionInhibitReason.DEAD));
        assertEveryKind(new ActionFacts(true, true, false), Set.of(ActionInhibitReason.NO_FUEL, ActionInhibitReason.DEAD));
    }

    @Test
    void playerControlForbidsOnlyMovement() {
        ActionConstraints constraints = ActionConstraintPolicy.evaluate(new ActionFacts(false, false, true));
        assertEquals(Set.of(ActionInhibitReason.PLAYER_CONTROL), constraints.movement().reasons());
        for (ActionKind kind : ActionKind.values()) {
            assertEquals(kind != ActionKind.MOVEMENT, constraints.allows(kind), kind.name());
        }
        ActionConstraints dry = ActionConstraintPolicy.evaluate(new ActionFacts(true, false, true));
        assertEquals(Set.of(ActionInhibitReason.NO_FUEL, ActionInhibitReason.PLAYER_CONTROL),
                dry.movement().reasons());
        assertEquals(Set.of(ActionInhibitReason.NO_FUEL), dry.firing().reasons());
    }

    @Test
    void permissionIsAllowedExactlyWithoutReasons() {
        assertThrows(IllegalArgumentException.class, () -> new ActionPermission(true, Set.of(ActionInhibitReason.DEAD)));
        assertThrows(IllegalArgumentException.class, () -> new ActionPermission(false, Set.of()));
    }

    private static void assertEveryKind(ActionFacts facts, Set<ActionInhibitReason> reasons) {
        ActionConstraints constraints = ActionConstraintPolicy.evaluate(facts);
        for (ActionKind kind : ActionKind.values()) {
            assertFalse(constraints.allows(kind), kind.name());
            assertEquals(reasons, constraints.get(kind).reasons(), kind.name());
        }
    }
}
