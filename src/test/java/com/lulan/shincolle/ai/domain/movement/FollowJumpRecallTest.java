package com.lulan.shincolle.ai.domain.movement;

import com.lulan.shincolle.ai.domain.DimensionKey;
import com.lulan.shincolle.ai.domain.TargetHandle;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FollowJumpRecallTest {
    private static final MovementPoint HOME = new MovementPoint(0D, 64D, 0D);
    private static final MovementPoint OWNER = new MovementPoint(300D, 64D, 0D);
    private static final TeleportRule RULE = new TeleportRule(true, 200, 256);

    @Test
    void recallSurvivesGoalStartAndUsesTheOrdinaryFarTeleportAfterFiveSeconds() {
        var state = FollowMovePlanner.start(FollowMovePlanner.initial(HOME), 200);
        state = FollowMovePlanner.afterOwnerJump(state, 90_000D, 90_000D, RULE, 0);
        var move = FollowMovePlanner.move(state, new FollowMovePlanner.Facts(90_000D, new FollowRange(4D, 9D),
                Optional.of(OWNER), new DimensionKey("minecraft", "overworld"), RULE, StuckState.NONE));
        assertTrue(move.plan().steps().stream().anyMatch(step -> step instanceof MovementStep.Teleport teleport
                && teleport.reason() == MovementReason.TELEPORT_FAR));
        assertEquals(0, move.state().farTimer());
    }

    @Test
    void beforeDeadlineNearOwnerAndDisabledTeleportNeverAdvanceTheFarTimer() {
        var state = FollowMovePlanner.start(FollowMovePlanner.initial(HOME), 0);
        assertEquals(state, FollowMovePlanner.afterOwnerJump(state, 90_000D, 90_000D, RULE, 1));
        assertEquals(state, FollowMovePlanner.afterOwnerJump(state, 256D, 256D, RULE, 0));
        assertEquals(state, FollowMovePlanner.afterOwnerJump(state, 90_000D, 256D, RULE, 0));
        assertEquals(state, FollowMovePlanner.afterOwnerJump(state, 90_000D, 90_000D, new TeleportRule(false, 200, 256), 0));
    }

    @Test
    void requestExpiresAndCannotBeUsedForAnotherOwnerOrDimension() {
        var dimension = new DimensionKey("minecraft", "overworld");
        var owner = new TargetHandle(UUID.randomUUID(), dimension);
        var request = new FollowRecallRequest(owner, 500);
        assertEquals(100, request.remaining(owner, 500).orElseThrow());
        assertEquals(0, request.remaining(owner, 600).orElseThrow());
        assertTrue(request.remaining(owner, 900).isEmpty());
        assertTrue(request.remaining(new TargetHandle(UUID.randomUUID(), dimension), 600).isEmpty());
        assertTrue(request.remaining(new TargetHandle(owner.uuid(), new DimensionKey("minecraft", "the_nether")), 600).isEmpty());
    }
}
