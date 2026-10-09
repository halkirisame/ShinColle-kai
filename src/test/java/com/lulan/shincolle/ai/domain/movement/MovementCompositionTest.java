package com.lulan.shincolle.ai.domain.movement;

import com.lulan.shincolle.ai.domain.DimensionKey;
import com.lulan.shincolle.ai.domain.TargetHandle;
import com.lulan.shincolle.ai.domain.command.CommandPos;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MovementCompositionTest {
    private static final DimensionKey OVERWORLD = new DimensionKey("minecraft", "overworld");
    private static final MovementPoint ORIGIN = new MovementPoint(0D, 64D, 0D);
    private static final MovementConstraint.Within CIRCLE =
            new MovementConstraint.Within(ORIGIN, 6D, ConstraintSource.FOLLOW_OWNER);

    private static MovementPoint at(double x) {
        return new MovementPoint(x, 64D, 0D);
    }

    private static CombatMove compose(MovementConstraint constraint, double self, double target, boolean hold) {
        return MovementComposition.compose(constraint, new CombatMove.Request(at(self), at(target), hold));
    }

    @Test
    void holdingForFireAlwaysHolds() {
        assertEquals(new CombatMove.Hold(CombatMove.Reason.IN_POSITION), compose(CIRCLE, 0D, 20D, true));
        assertEquals(new CombatMove.Hold(CombatMove.Reason.IN_POSITION),
                compose(new MovementConstraint.Unconstrained(), 0D, 20D, true));
    }

    @Test
    void noCombatMovementHolds() {
        assertEquals(new CombatMove.Hold(CombatMove.Reason.NO_COMBAT_MOVEMENT),
                compose(new MovementConstraint.NoCombatMovement(ConstraintSource.FLEE), 0D, 20D, false));
    }

    @Test
    void unconstrainedClosesOnTheTarget() {
        assertEquals(new CombatMove.Approach(at(20D), CombatMove.Reason.TOWARD_TARGET),
                compose(new MovementConstraint.Unconstrained(), 0D, 20D, false));
    }

    @Test
    void aTargetInsideTheInnerCircleIsApproached() {
        // inner radius is 6 - 2 = 4
        assertEquals(new CombatMove.Approach(at(4D), CombatMove.Reason.TOWARD_TARGET), compose(CIRCLE, -3D, 4D, false));
    }

    @Test
    void aTargetOutsideStopsAtTheInnerEdge() {
        CombatMove move = compose(CIRCLE, -3D, 20D, false);
        assertEquals(new CombatMove.Approach(at(4D), CombatMove.Reason.TO_REGION_EDGE), move);

        MovementPoint diagonal = new MovementPoint(30D, 64D, 40D);
        CombatMove.Approach toward = (CombatMove.Approach) MovementComposition.compose(CIRCLE,
                new CombatMove.Request(ORIGIN, diagonal, false));
        assertEquals(16D, ORIGIN.distanceSq(toward.destination()), 1e-9, "the edge point is 4 from the anchor");
    }

    @Test
    void theShipAtTheEdgeHolds() {
        assertEquals(new CombatMove.Hold(CombatMove.Reason.AT_REGION_EDGE), compose(CIRCLE, 4D, 20D, false));
        assertEquals(new CombatMove.Hold(CombatMove.Reason.AT_REGION_EDGE), compose(CIRCLE, 2.5D, 20D, false));
        assertEquals(CombatMove.Reason.TO_REGION_EDGE, compose(CIRCLE, 2.4D, 20D, false).reason());
    }

    @Test
    void aCircleNoWiderThanTheMarginHolds() {
        MovementConstraint.Within tight = new MovementConstraint.Within(ORIGIN, 2D, ConstraintSource.GUARD_POSITION);
        assertEquals(new CombatMove.Hold(CombatMove.Reason.NO_COMBAT_MOVEMENT), compose(tight, 0D, 20D, false));
    }

    @Test
    void compositionIsDeterministic() {
        assertEquals(compose(CIRCLE, -3D, 20D, false), compose(CIRCLE, -3D, 20D, false));
    }

    @Test
    void constraintsFollowTheIntent() {
        MovementConstraints.Facts facts = new MovementConstraints.Facts(false, Optional.of(ORIGIN),
                Optional.of(at(9D)), 4, 0.6F, false);
        double radius = MovementConstraints.followRadius(4, 0.6F, false);
        assertEquals(new MovementConstraint.Within(ORIGIN, radius, ConstraintSource.FOLLOW_OWNER),
                MovementConstraints.of(new MovementIntent.FollowOwner(), facts));
        TargetHandle cow = new TargetHandle(UUID.randomUUID(), OVERWORLD);
        assertEquals(new MovementConstraint.Within(at(9D), radius, ConstraintSource.GUARD_ENTITY),
                MovementConstraints.of(new MovementIntent.GuardEntity(cow), facts));
        assertEquals(new MovementConstraint.Within(new MovementPoint(1.5D, 64.5D, 2.5D), radius,
                        ConstraintSource.GUARD_POSITION),
                MovementConstraints.of(new MovementIntent.GuardPosition(OVERWORLD, new CommandPos(1, 64, 2)), facts));
        assertEquals(new MovementConstraint.Unconstrained(),
                MovementConstraints.of(new MovementIntent.MoveTo(OVERWORLD, new CommandPos(1, 64, 2)), facts));
        assertEquals(new MovementConstraint.NoCombatMovement(ConstraintSource.FLEE),
                MovementConstraints.of(new MovementIntent.Flee(), facts));
        assertEquals(new MovementConstraint.NoCombatMovement(ConstraintSource.SIT),
                MovementConstraints.of(new MovementIntent.Sit(), facts));

        MovementConstraints.Facts formation = new MovementConstraints.Facts(true, Optional.of(ORIGIN),
                Optional.empty(), 4, 0.6F, false);
        assertEquals(new MovementConstraint.NoCombatMovement(ConstraintSource.FORMATION),
                MovementConstraints.of(new MovementIntent.FollowOwner(), formation));

        MovementConstraints.Facts alone = new MovementConstraints.Facts(false, Optional.empty(),
                Optional.empty(), 4, 0.6F, false);
        assertEquals(new MovementConstraint.Unconstrained(),
                MovementConstraints.of(new MovementIntent.FollowOwner(), alone));
        assertEquals(new MovementConstraint.Unconstrained(),
                MovementConstraints.of(new MovementIntent.GuardEntity(cow), alone));
    }

    @Test
    void followRadiusMatchesTheFollowGoal() {
        assertEquals(12F + 0.6F * 0.75F, MovementConstraints.followRadius(12, 0.6F, false), 1e-6);
        assertEquals(12F + 0.6F * 0.75F + 5F, MovementConstraints.followRadius(12, 0.6F, true), 1e-6);
    }

    @Test
    void fleeStayWalksFromFourBlocksAndStopsAtTheOriginalDistance() {
        assertFalse(FleeStay.shouldWalk(false, 16D));
        assertTrue(FleeStay.shouldWalk(false, 16.01D));
        assertTrue(FleeStay.shouldWalk(true, 6.01D));
        assertFalse(FleeStay.shouldWalk(true, 6D));
    }
}
