package com.lulan.shincolle.ai.domain.movement;

import com.lulan.shincolle.ai.domain.DimensionKey;
import com.lulan.shincolle.ai.domain.TargetHandle;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PickItemUntakeableTest {
    private static final TargetHandle ITEM = new TargetHandle(new UUID(0L, 1L), new DimensionKey("minecraft", "overworld"));
    private static final TargetHandle OTHER = new TargetHandle(new UUID(0L, 2L), new DimensionKey("minecraft", "overworld"));

    private static MovementState.PickItem started() {
        return PickItemMovePlanner.start(PickItemMovePlanner.initial(), 10);
    }

    @Test
    void anItemIsGivenUpOnOnlyAfterFailingForLongEnoughInARow() {
        MovementState.PickItem state = PickItemMovePlanner.failedTake(started(), ITEM, 100);
        assertFalse(PickItemMovePlanner.untakeable(state, ITEM, 100));
        // fails every 4 ticks, as a fast ship does: still inside an ordinary 40 tick pickup delay at 140
        for (int at = 104; at <= 160; at += 4) state = PickItemMovePlanner.failedTake(state, ITEM, at);
        assertFalse(PickItemMovePlanner.untakeable(state, ITEM, 100 + PickItemMovePlanner.TAKE_GRACE - 1));
        assertTrue(PickItemMovePlanner.untakeable(state, ITEM, 100 + PickItemMovePlanner.TAKE_GRACE));
    }

    @Test
    void failuresDoNotCarryOverToAnotherItemOrAcrossAGap() {
        MovementState.PickItem state = started();
        for (int at = 100; at <= 170; at += 10) state = PickItemMovePlanner.failedTake(state, ITEM, at);
        MovementState.PickItem other = PickItemMovePlanner.failedTake(state, OTHER, 175);
        assertFalse(PickItemMovePlanner.untakeable(other, OTHER, 180));
        assertFalse(PickItemMovePlanner.untakeable(other, ITEM, 180));
        // the same item again after a long while without a failed take is a new run
        MovementState.PickItem later = PickItemMovePlanner.failedTake(state, ITEM, 170 + PickItemMovePlanner.TAKE_GAP + 1);
        assertFalse(PickItemMovePlanner.untakeable(later, ITEM, 215));
        assertTrue(PickItemMovePlanner.untakeable(state, ITEM, 180));
        assertFalse(PickItemMovePlanner.untakeable(PickItemMovePlanner.tookItem(state), ITEM, 180));
        assertFalse(PickItemMovePlanner.untakeable(PickItemMovePlanner.start(state, 180), ITEM, 180));
    }

    @Test
    void anAbandonedItemIsLeftAloneForAWhileAndOthersAreNot() {
        MovementState.PickItem after = PickItemMovePlanner.abandon(started(), ITEM, 100);
        assertFalse(PickItemMovePlanner.choosable(after, ITEM, 699));
        assertTrue(PickItemMovePlanner.choosable(after, ITEM, 700));
        assertTrue(PickItemMovePlanner.choosable(after, OTHER, 101));
        // a new start keeps it given up on
        assertFalse(PickItemMovePlanner.choosable(PickItemMovePlanner.start(after, 120), ITEM, 130));
    }

    @Test
    void givingUpASecondItemKeepsTheFirstGivenUpUntilItsOwnTime() {
        MovementState.PickItem state = PickItemMovePlanner.abandon(PickItemMovePlanner.initial(), ITEM, 100);
        state = PickItemMovePlanner.abandon(state, OTHER, 260);
        assertFalse(PickItemMovePlanner.choosable(state, ITEM, 261));
        assertFalse(PickItemMovePlanner.choosable(state, OTHER, 261));
        assertTrue(PickItemMovePlanner.choosable(state, ITEM, 700));
        assertFalse(PickItemMovePlanner.choosable(state, OTHER, 700));
        assertTrue(PickItemMovePlanner.choosable(state, OTHER, 860));
    }
}
