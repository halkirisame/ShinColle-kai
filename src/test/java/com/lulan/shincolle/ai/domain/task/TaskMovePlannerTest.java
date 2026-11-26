package com.lulan.shincolle.ai.domain.task;

import com.lulan.shincolle.ai.domain.command.CommandPos;
import com.lulan.shincolle.ai.domain.command.MovementOrder;
import com.lulan.shincolle.ai.domain.movement.MovementBody;
import com.lulan.shincolle.ai.domain.movement.MovementConstraint;
import com.lulan.shincolle.ai.domain.movement.MovementFacts;
import com.lulan.shincolle.ai.domain.movement.MovementInhibitReason;
import com.lulan.shincolle.ai.domain.movement.MovementIntent;
import com.lulan.shincolle.ai.domain.movement.MovementIntentResolver;
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

    @Test
    void theShipsOwnMountCarriesFixedPointTasksWithoutChangingTheRequest() {
        MovementFacts riding = new MovementFacts(new MovementOrder.Follow(), false, false, false,
                1F, 0F, false, 0D, true, true, false, false, false, false, false, false);
        for (TaskMoveRequest request : new TaskMoveRequest[] {
                new TaskMoveRequest.ReturnToWaypoint(new CommandPos(3, -60, 7)),
                new TaskMoveRequest.FishingSpot(new MovementPoint(2.5D, 64D, 9.5D))}) {
            MovementPermission permission = TaskMovePermissions.of(riding, new MovementConstraint.Unconstrained(),
                    request, MovementBody.VEHICLE);
            assertTrue(permission.allowed());
            MovementStep.PathTo original = onlyPath(TaskMovePlanner.plan(request, MovementPermission.ALLOWED));
            MovementStep.PathTo mounted = onlyPath(TaskMovePlanner.plan(request, permission, MovementBody.VEHICLE));
            assertEquals(MovementBody.VEHICLE, mounted.body());
            assertEquals(original.target(), mounted.target());
            assertEquals(original.speed(), mounted.speed());
            assertEquals(original.reason(), mounted.reason());
            assertEquals(original.onFailure(), mounted.onFailure());
        }
    }

    @Test
    void theOwnMountCannotShuffleEvenWhileUnengagedAndInsideItsRegion() {
        MovementFacts riding = new MovementFacts(new MovementOrder.Follow(), false, false, false,
                1F, 0F, false, 0D, true, true, false, false, false, false, false, false);
        TaskMoveRequest request = new TaskMoveRequest.MiningShuffle(new MovementPoint(1D, 2D, 3D));
        MovementPermission permission = TaskMovePermissions.of(riding, new MovementConstraint.Unconstrained(),
                request, MovementBody.VEHICLE);
        assertEquals(Set.of(MovementInhibitReason.RIDING), permission.reasons());
        assertTrue(TaskMovePlanner.plan(request, permission, MovementBody.VEHICLE).isEmpty());
    }

    @Test
    void anotherVehicleStillInhibitsEveryTaskWalk() {
        MovementFacts riding = new MovementFacts(new MovementOrder.Follow(), false, false, false,
                1F, 0F, false, 0D, true, true, false, false, false, false, false, false);
        for (TaskMoveRequest request : new TaskMoveRequest[] {
                new TaskMoveRequest.ReturnToWaypoint(new CommandPos(3, -60, 7)),
                new TaskMoveRequest.FishingSpot(new MovementPoint(2.5D, 64D, 9.5D)),
                new TaskMoveRequest.MiningShuffle(new MovementPoint(1D, 2D, 3D))}) {
            MovementPermission permission = TaskMovePermissions.of(riding, new MovementConstraint.Unconstrained(),
                    request, MovementBody.SELF);
            assertEquals(Set.of(MovementInhibitReason.RIDING), permission.reasons());
            assertTrue(TaskMovePlanner.plan(request, permission, MovementBody.SELF).isEmpty());
        }
    }

    @Test
    void anUnmountedShipKeepsItsSelfPlan() {
        MovementFacts standing = new MovementFacts(new MovementOrder.Follow(), false, false, false,
                1F, 0F, false, 0D, true, false, false, false, false, false, false, false);
        TaskMoveRequest request = new TaskMoveRequest.FishingSpot(new MovementPoint(2.5D, -56D, 9.5D));
        MovementPermission permission = TaskMovePermissions.of(standing, new MovementConstraint.Unconstrained(),
                request, MovementBody.SELF);
        assertEquals(MovementPermission.ALLOWED, permission);
        assertEquals(TaskMovePlanner.plan(request, MovementPermission.ALLOWED),
                TaskMovePlanner.plan(request, permission, MovementBody.SELF));
        assertEquals(MovementBody.SELF, onlyPath(TaskMovePlanner.plan(request, permission, MovementBody.SELF)).body());
    }

    @Test
    void ownMountFixedPointTasksWaitDuringFleeIntent() {
        MovementFacts fleeing = new MovementFacts(new MovementOrder.Follow(), false, false, false,
                0.1F, 0.5F, true, 400D, true, true, false, false, false, false, false, false);
        assertTrue(MovementIntentResolver.resolve(fleeing).intent() instanceof MovementIntent.Flee);
        for (TaskMoveRequest request : new TaskMoveRequest[] {
                new TaskMoveRequest.ReturnToWaypoint(new CommandPos(3, -60, 7)),
                new TaskMoveRequest.FishingSpot(new MovementPoint(2.5D, 64D, 9.5D))}) {
            MovementPermission permission = TaskMovePermissions.of(fleeing, new MovementConstraint.Unconstrained(),
                    request, MovementBody.VEHICLE);
            assertEquals(Set.of(MovementInhibitReason.FLEEING), permission.reasons());
            assertTrue(TaskMovePlanner.plan(request, permission, MovementBody.VEHICLE).isEmpty());
        }
    }

    @Test
    void ownMountFixedPointTasksWaitForEngagement() {
        MovementFacts engaged = new MovementFacts(new MovementOrder.Follow(), false, false, false,
                1F, 0.5F, true, 400D, true, true, false, false, false, false, false, true);
        for (TaskMoveRequest request : new TaskMoveRequest[] {
                new TaskMoveRequest.ReturnToWaypoint(new CommandPos(3, -60, 7)),
                new TaskMoveRequest.FishingSpot(new MovementPoint(2.5D, 64D, 9.5D))}) {
            MovementPermission permission = TaskMovePermissions.of(engaged, new MovementConstraint.Unconstrained(),
                    request, MovementBody.VEHICLE);
            assertEquals(Set.of(MovementInhibitReason.ENGAGED), permission.reasons());
            assertTrue(TaskMovePlanner.plan(request, permission, MovementBody.VEHICLE).isEmpty());
        }
    }

    @Test
    void ownMountFixedPointTasksKeepBothFleeingAndEngagedReasons() {
        MovementFacts both = new MovementFacts(new MovementOrder.Follow(), false, false, false,
                0.1F, 0.5F, true, 400D, true, true, false, false, false, false, false, true);
        for (TaskMoveRequest request : new TaskMoveRequest[] {
                new TaskMoveRequest.ReturnToWaypoint(new CommandPos(3, -60, 7)),
                new TaskMoveRequest.FishingSpot(new MovementPoint(2.5D, 64D, 9.5D))}) {
            MovementPermission permission = TaskMovePermissions.of(both, new MovementConstraint.Unconstrained(),
                    request, MovementBody.VEHICLE);
            assertEquals(Set.of(MovementInhibitReason.FLEEING, MovementInhibitReason.ENGAGED), permission.reasons());
            assertTrue(TaskMovePlanner.plan(request, permission, MovementBody.VEHICLE).isEmpty());
        }
    }

    @Test
    void selfFixedPointTasksStayAllowedDuringFleeingAndEngagement() {
        for (boolean fleeing : new boolean[]{false, true}) {
            for (boolean engaged : new boolean[]{false, true}) {
                MovementFacts facts = new MovementFacts(new MovementOrder.Follow(), false, false, false,
                        fleeing ? 0.1F : 1F, 0.5F, true, 400D, true, false, false, false, false, false, false, engaged);
                assertEquals(fleeing, MovementIntentResolver.resolve(facts).intent() instanceof MovementIntent.Flee);
                for (TaskMoveRequest request : new TaskMoveRequest[] {
                        new TaskMoveRequest.ReturnToWaypoint(new CommandPos(3, -60, 7)),
                        new TaskMoveRequest.FishingSpot(new MovementPoint(2.5D, 64D, 9.5D))}) {
                    MovementPermission permission = TaskMovePermissions.of(facts, new MovementConstraint.Unconstrained(),
                            request, MovementBody.SELF);
                    assertEquals(MovementPermission.ALLOWED, permission);
                    assertEquals(TaskMovePlanner.plan(request, MovementPermission.ALLOWED),
                            TaskMovePlanner.plan(request, permission, MovementBody.SELF));
                }
            }
        }
    }

    @Test
    void lowHealthWithoutFleeIntentDoesNotBlockOwnMountFixedPointTasks() {
        MovementFacts noOwner = new MovementFacts(new MovementOrder.Follow(), false, false, false,
                0.1F, 0.5F, false, 0D, true, true, false, false, false, false, false, false);
        assertTrue(!(MovementIntentResolver.resolve(noOwner).intent() instanceof MovementIntent.Flee));
        for (TaskMoveRequest request : new TaskMoveRequest[] {
                new TaskMoveRequest.ReturnToWaypoint(new CommandPos(3, -60, 7)),
                new TaskMoveRequest.FishingSpot(new MovementPoint(2.5D, 64D, 9.5D))}) {
            MovementPermission permission = TaskMovePermissions.of(noOwner, new MovementConstraint.Unconstrained(),
                    request, MovementBody.VEHICLE);
            assertEquals(MovementPermission.ALLOWED, permission);
            assertEquals(MovementBody.VEHICLE, onlyPath(TaskMovePlanner.plan(request, permission, MovementBody.VEHICLE)).body());
        }
    }
}
