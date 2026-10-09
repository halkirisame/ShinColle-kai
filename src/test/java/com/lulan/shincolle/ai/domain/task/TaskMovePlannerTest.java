package com.lulan.shincolle.ai.domain.task;

import com.lulan.shincolle.ai.domain.command.CommandPos;
import com.lulan.shincolle.ai.domain.movement.MovementBody;
import com.lulan.shincolle.ai.domain.movement.MovementInhibitReason;
import com.lulan.shincolle.ai.domain.movement.MovementPermission;
import com.lulan.shincolle.ai.domain.movement.MovementPlan;
import com.lulan.shincolle.ai.domain.movement.MovementPoint;
import com.lulan.shincolle.ai.domain.movement.MovementReason;
import com.lulan.shincolle.ai.domain.movement.MovementStep;
import com.lulan.shincolle.ai.domain.movement.MovementTarget;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskMovePlannerTest {

    private static MovementStep.PathTo onlyPath(MovementPlan plan) {
        assertEquals(1, plan.steps().size());
        return (MovementStep.PathTo) plan.steps().get(0);
    }

    @Test
    void returnPathsToTheGuardPointItselfFromTheShip() {
        MovementStep.PathTo path = onlyPath(TaskMovePlanner.plan(
                new TaskMoveRequest.ReturnToWaypoint(new CommandPos(3, -60, 7)), MovementPermission.ALLOWED));
        assertEquals(MovementBody.SELF, path.body());
        assertEquals(new MovementTarget.Point(new MovementPoint(3D, -60D, 7D)), path.target());
        assertEquals(1D, path.speed());
        assertEquals(MovementReason.TASK_RETURN, path.reason());
    }

    @Test
    void fishingAndMiningPathToTheirPointWithTheirOwnReason() {
        MovementStep.PathTo fishing = onlyPath(TaskMovePlanner.plan(
                new TaskMoveRequest.FishingSpot(new MovementPoint(2.5D, 64D, 9.5D)), MovementPermission.ALLOWED));
        assertEquals(new MovementTarget.Point(new MovementPoint(2.5D, 64D, 9.5D)), fishing.target());
        assertEquals(MovementReason.TASK_FISHING_SPOT, fishing.reason());

        MovementStep.PathTo mining = onlyPath(TaskMovePlanner.plan(
                new TaskMoveRequest.MiningShuffle(new MovementPoint(1D, 2D, 3D)), MovementPermission.ALLOWED));
        assertEquals(new MovementTarget.Point(new MovementPoint(1D, 2D, 3D)), mining.target());
        assertEquals(MovementReason.TASK_MINING_SHUFFLE, mining.reason());
    }

    @Test
    void aWalkThatIsNotPermittedMakesNoPlan() {
        MovementPermission inhibited = MovementPermission.inhibited(Set.of(MovementInhibitReason.values()[0]));
        assertTrue(TaskMovePlanner.plan(new TaskMoveRequest.FishingSpot(new MovementPoint(0D, 0D, 0D)),
                inhibited).isEmpty());
    }
}
