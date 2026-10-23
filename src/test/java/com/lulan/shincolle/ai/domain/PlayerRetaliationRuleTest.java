package com.lulan.shincolle.ai.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerRetaliationRuleTest {

    @Test
    void playerTargetIsRefusedUnlessShipOrFleetPermits() {
        assertFalse(PlayerRetaliationRule.allows(true, false, false));
        assertTrue(PlayerRetaliationRule.allows(true, true, false));
        assertTrue(PlayerRetaliationRule.allows(true, false, true));
        assertTrue(PlayerRetaliationRule.allows(true, true, true));
    }

    @Test
    void nonPlayerTargetIsNeverRestricted() {
        assertTrue(PlayerRetaliationRule.allows(false, false, false));
        assertTrue(PlayerRetaliationRule.allows(false, true, true));
    }
}
