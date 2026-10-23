package com.lulan.shincolle.ai.domain.command;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FishingCommandPolicyTest {
    @Test
    void singleFishingKeepsItsSpotBeforeTheFirstCast() {
        assertFalse(CommandDecision.releaseOnArrival(CommandMode.SINGLE, true, true, true));
    }

    @Test
    void otherModesAndExplicitMoveOnlyKeepTheirArrivalBehavior() {
        assertTrue(CommandDecision.releaseOnArrival(CommandMode.SINGLE, false, true, true));
        assertTrue(CommandDecision.releaseOnArrival(CommandMode.GROUP, true, true, true));
        assertTrue(CommandDecision.releaseOnArrival(CommandMode.FORMATION, true, true, true));
        assertTrue(CommandDecision.releaseOnArrival(CommandMode.SINGLE, true, false, true));
        for (CommandMode mode : CommandMode.values()) {
            assertFalse(CommandDecision.releaseOnArrival(mode, true, true, false));
        }
    }

    @Test
    void repeatingTheFishingOrderStillReturnsToFollow() {
        var dimension = new com.lulan.shincolle.ai.domain.DimensionKey("minecraft", "overworld");
        var position = new CommandPos(4, -60, 8);
        boolean release = CommandDecision.releaseOnArrival(CommandMode.SINGLE, true, true, true);
        assertFalse(release);
        var repeated = CommandDecision.position(dimension, position, release, release, true,
                dimension, position, true);
        assertTrue(repeated instanceof ShipCommand.Follow);
    }
}
