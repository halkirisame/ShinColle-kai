package com.lulan.shincolle.ai.domain.task;

import com.lulan.shincolle.ai.domain.command.CommandPos;
import com.lulan.shincolle.ai.domain.command.MovementOrder;
import com.lulan.shincolle.ai.domain.movement.ConstraintSource;
import com.lulan.shincolle.ai.domain.movement.MovementBody;
import com.lulan.shincolle.ai.domain.movement.MovementConstraint;
import com.lulan.shincolle.ai.domain.movement.MovementFacts;
import com.lulan.shincolle.ai.domain.movement.MovementInhibitReason;
import com.lulan.shincolle.ai.domain.movement.MovementPermission;
import com.lulan.shincolle.ai.domain.movement.MovementPoint;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskMovePermissionsTest {

    private static final MovementConstraint FREE = new MovementConstraint.Unconstrained();
    private static final TaskMoveRequest BACK = new TaskMoveRequest.ReturnToWaypoint(new CommandPos(0, 0, 0));
    private static final TaskMoveRequest SPOT = new TaskMoveRequest.FishingSpot(new MovementPoint(0D, 0D, 0D));
    private static final TaskMoveRequest SHUFFLE = new TaskMoveRequest.MiningShuffle(new MovementPoint(3D, 0D, 0D));

    private static MovementFacts facts(boolean sitting, boolean riding, boolean crane, boolean grudge,
                                       boolean leashed, boolean engaged) {
        return new MovementFacts(new MovementOrder.Follow(), false, sitting, false, 1F, 0F, false, 0D, grudge,
                riding, false, leashed, crane, false, false, engaged);
    }

    private static MovementFacts idle() {
        return facts(false, false, false, true, false, false);
    }

    @Test
    void anIdleShipMayWalkForEveryWork() {
        for (TaskMoveRequest request : new TaskMoveRequest[] {BACK, SPOT, SHUFFLE}) {
            assertEquals(MovementPermission.ALLOWED, TaskMovePermissions.of(idle(), FREE, request));
        }
    }

    @Test
    void everyWalkIsHeldBackWhileTheShipCannotMove() {
        for (TaskMoveRequest request : new TaskMoveRequest[] {BACK, SPOT, SHUFFLE}) {
            assertEquals(Set.of(MovementInhibitReason.SITTING),
                    TaskMovePermissions.of(facts(true, false, false, true, false, false), FREE, request).reasons());
            assertEquals(Set.of(MovementInhibitReason.RIDING),
                    TaskMovePermissions.of(facts(false, true, false, true, false, false), FREE, request).reasons());
            assertEquals(Set.of(MovementInhibitReason.CRANE),
                    TaskMovePermissions.of(facts(false, false, true, true, false, false), FREE, request).reasons());
            assertEquals(Set.of(MovementInhibitReason.NO_GRUDGE),
                    TaskMovePermissions.of(facts(false, false, false, false, false, false), FREE, request).reasons());
            assertEquals(Set.of(MovementInhibitReason.LEASHED),
                    TaskMovePermissions.of(facts(false, false, false, true, true, false), FREE, request).reasons());
        }
    }

    @Test
    void theReasonsAreAllGivenNotJustTheFirst() {
        assertEquals(Set.of(MovementInhibitReason.SITTING, MovementInhibitReason.LEASHED),
                TaskMovePermissions.of(facts(true, false, false, true, true, false), FREE, BACK).reasons());
    }

    @Test
    void onlyMiningWaitsWhileEngaged() {
        MovementFacts engaged = facts(false, false, false, true, false, true);
        assertEquals(Set.of(MovementInhibitReason.ENGAGED), TaskMovePermissions.of(engaged, FREE, SHUFFLE).reasons());
        assertTrue(TaskMovePermissions.of(engaged, FREE, BACK).allowed());
        assertTrue(TaskMovePermissions.of(engaged, FREE, SPOT).allowed());
    }

    @Test
    void aMiningShuffleStaysWithinTheRegionOnItsEdge() {
        MovementPoint anchor = new MovementPoint(0D, 0D, 0D);
        assertTrue(TaskMovePermissions.of(idle(),
                new MovementConstraint.Within(anchor, 3D, ConstraintSource.GUARD_POSITION), SHUFFLE).allowed());
        assertEquals(Set.of(MovementInhibitReason.OUTSIDE_REGION), TaskMovePermissions.of(idle(),
                new MovementConstraint.Within(anchor, 2.99D, ConstraintSource.GUARD_POSITION), SHUFFLE).reasons());
    }

    @Test
    void aShipWhoseIntentOwnsItsMovementDoesNotShuffle() {
        assertEquals(Set.of(MovementInhibitReason.OUTSIDE_REGION), TaskMovePermissions.of(idle(),
                new MovementConstraint.NoCombatMovement(ConstraintSource.FORMATION), SHUFFLE).reasons());
    }

    @Test
    void theWalkBackAndTheWalkToTheSpotAreNotHeldToTheRegion() {
        MovementConstraint tight = new MovementConstraint.Within(new MovementPoint(100D, 0D, 0D), 1D,
                ConstraintSource.GUARD_POSITION);
        assertTrue(TaskMovePermissions.of(idle(), tight, BACK).allowed());
        assertTrue(TaskMovePermissions.of(idle(), tight, SPOT).allowed());
        assertTrue(TaskMovePermissions.of(idle(), new MovementConstraint.NoCombatMovement(ConstraintSource.SIT),
                BACK).allowed());
    }

    @Test
    void theOwnMountExceptionKeepsSittingCraneGrudgeAndLeashInhibitions() {
        MovementFacts blocked = facts(true, true, true, false, true, false);
        Set<MovementInhibitReason> expected = Set.of(MovementInhibitReason.SITTING, MovementInhibitReason.CRANE,
                MovementInhibitReason.NO_GRUDGE, MovementInhibitReason.LEASHED);
        for (TaskMoveRequest request : new TaskMoveRequest[] {BACK, SPOT, SHUFFLE}) {
            MovementPermission permission = TaskMovePermissions.of(blocked, FREE, request, MovementBody.VEHICLE);
            Set<MovementInhibitReason> reasons = EnumSet.copyOf(expected);
            if (request instanceof TaskMoveRequest.MiningShuffle) reasons.add(MovementInhibitReason.RIDING);
            assertEquals(reasons, permission.reasons());
            assertTrue(TaskMovePlanner.plan(request, permission, MovementBody.VEHICLE).isEmpty());
        }
    }

    @Test
    void miningFromTheOwnMountStillWaitsForEngagementAndStaysInsideItsRegion() {
        MovementFacts engaged = facts(false, true, false, true, false, true);
        MovementConstraint tight = new MovementConstraint.Within(new MovementPoint(0D, 0D, 0D), 2.99D,
                ConstraintSource.GUARD_POSITION);
        MovementPermission permission = TaskMovePermissions.of(engaged, tight, SHUFFLE, MovementBody.VEHICLE);
        assertEquals(Set.of(MovementInhibitReason.RIDING, MovementInhibitReason.ENGAGED, MovementInhibitReason.OUTSIDE_REGION),
                permission.reasons());
        assertTrue(TaskMovePlanner.plan(SHUFFLE, permission, MovementBody.VEHICLE).isEmpty());
        assertEquals(Set.of(MovementInhibitReason.ENGAGED),
                TaskMovePermissions.of(engaged, tight, BACK, MovementBody.VEHICLE).reasons());
        assertEquals(Set.of(MovementInhibitReason.ENGAGED),
                TaskMovePermissions.of(engaged, tight, SPOT, MovementBody.VEHICLE).reasons());
    }
}
