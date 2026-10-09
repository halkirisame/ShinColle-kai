package com.lulan.shincolle.ai.domain.movement;

import com.lulan.shincolle.ai.domain.DimensionKey;
import com.lulan.shincolle.ai.domain.TargetHandle;
import com.lulan.shincolle.ai.domain.command.CommandPos;
import com.lulan.shincolle.ai.domain.command.MovementOrder;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuardProximityTest {
    private static final DimensionKey OVERWORLD = new DimensionKey("minecraft", "overworld");
    private static final MovementOrder FOLLOW = new MovementOrder.Follow();
    private static final MovementOrder GUARD_ENTITY = new MovementOrder.GuardEntity(
            new TargetHandle(new UUID(0L, 1L), OVERWORLD));
    /** Inner distance 2, outer distance 4, in no formation. */
    private static final MovementSettings SETTINGS = new MovementSettings(false, 2, 4, false);
    private static final MovementPoint SELF = new MovementPoint(0D, 64D, 0D);

    private static Optional<MovementPoint> at(double x, double y, double z) {
        return Optional.of(new MovementPoint(x, y, z));
    }

    private static boolean follow(float width, Optional<MovementPoint> leader) {
        return GuardProximity.inPlace(FOLLOW, SETTINGS, width, SELF, leader, Optional.empty());
    }

    private static boolean guardEntity(float width, Optional<MovementPoint> guarded) {
        return GuardProximity.inPlace(GUARD_ENTITY, SETTINGS, width, SELF, Optional.empty(), guarded);
    }

    private static boolean block(boolean oneShot, float width, MovementPoint self, CommandPos pos) {
        MovementOrder order = oneShot ? new MovementOrder.MoveTo(OVERWORLD, pos, true)
                : new MovementOrder.GuardPosition(OVERWORLD, pos, false);
        // neither a leader nor a guarded entity changes where a block is
        return GuardProximity.inPlace(order, SETTINGS, width, self, at(0D, 64D, 0D), at(0D, 64D, 0D));
    }

    @Test
    void followingCountsUpToTheOuterDistanceInclusive() {
        assertTrue(follow(0F, at(0D, 64D, 0D)));
        assertTrue(follow(0F, at(4D, 64D, 0D)), "exactly the outer distance is inside");
        assertFalse(follow(0F, at(Math.nextUp(4D), 64D, 0D)));
        assertTrue(follow(0F, at(0D, 68D, 0D)), "the distance is measured in three dimensions");
        assertFalse(follow(0F, at(4D, 64D, 0.1D)));
        // half the width is added before squaring
        assertTrue(follow(1F, at(4.5D, 64D, 0D)));
        assertFalse(follow(1F, at(4.51D, 64D, 0D)));
        // the inner distance and the guarded entity play no part
        assertFalse(GuardProximity.inPlace(FOLLOW, SETTINGS, 0F, SELF, at(4.01D, 64D, 0D), at(0D, 64D, 0D)));
        assertFalse(follow(0F, Optional.empty()), "nobody to follow");
    }

    @Test
    void guardingAnEntityCountsInsideTheInnerDistanceExclusive() {
        assertTrue(guardEntity(0F, at(0D, 64D, 0D)));
        assertTrue(guardEntity(0F, at(Math.nextDown(2D), 64D, 0D)));
        assertFalse(guardEntity(0F, at(2D, 64D, 0D)), "exactly the inner distance is outside");
        assertTrue(guardEntity(1F, at(2.49D, 64D, 0D)));
        assertFalse(guardEntity(1F, at(2.5D, 64D, 0D)));
        // a guarded entity above or below counts by distance alone
        assertTrue(guardEntity(0F, at(0D, 65.9D, 0D)));
        assertTrue(guardEntity(0F, at(0D, 62.1D, 0D)));
        // the leader plays no part
        assertFalse(GuardProximity.inPlace(GUARD_ENTITY, SETTINGS, 0F, SELF, at(0D, 64D, 0D), Optional.empty()),
                "the guarded entity does not resolve");
    }

    @Test
    void guardingABlockMeasuresFromItsCornerAndNeverFromBelow() {
        for (boolean oneShot : new boolean[] {false, true}) {
            CommandPos pos = new CommandPos(10, 64, -3);
            // the block's own coordinates, not its middle
            assertTrue(block(oneShot, 0F, new MovementPoint(10D, 64D, -3D), pos));
            assertTrue(block(oneShot, 0F, new MovementPoint(10.5D, 64D, -2.5D), pos));
            assertTrue(block(oneShot, 0F, new MovementPoint(Math.nextDown(12D), 64D, -3D), pos));
            assertFalse(block(oneShot, 0F, new MovementPoint(12D, 64D, -3D), pos), "exactly the inner distance");
            assertTrue(block(oneShot, 1F, new MovementPoint(12.49D, 64D, -3D), pos));
            assertFalse(block(oneShot, 1F, new MovementPoint(12.5D, 64D, -3D), pos));
            // standing at the block's height counts, standing above it counts by distance, below never
            assertTrue(block(oneShot, 0F, new MovementPoint(10D, 65.9D, -3D), pos));
            assertFalse(block(oneShot, 0F, new MovementPoint(10D, 66D, -3D), pos));
            assertFalse(block(oneShot, 0F, new MovementPoint(10D, Math.nextDown(64D), -3D), pos));
            assertFalse(block(oneShot, 0F, new MovementPoint(10D, 63D, -3D), pos));
        }
        // a block at -1 on every axis, and one below zero, are blocks like any other
        assertTrue(block(false, 0F, new MovementPoint(-1D, -1D, -1D), new CommandPos(-1, -1, -1)));
        assertTrue(block(true, 0F, new MovementPoint(0.5D, -59.5D, 0.5D), new CommandPos(0, -60, 0)));
        assertFalse(block(true, 0F, new MovementPoint(0.5D, -60.5D, 0.5D), new CommandPos(0, -60, 0)));
    }

    @Test
    void theSettingsOtherThanTheTwoDistancesPlayNoPart() {
        MovementSettings formation = new MovementSettings(true, 2, 4, true);
        MovementPoint self = new MovementPoint(1D, 64D, 0D);
        CommandPos pos = new CommandPos(0, 64, 0);
        for (MovementOrder order : new MovementOrder[] {FOLLOW, GUARD_ENTITY,
                new MovementOrder.MoveTo(OVERWORLD, pos, false), new MovementOrder.GuardPosition(OVERWORLD, pos, true)}) {
            for (double away : new double[] {0D, 1.5D, 3D, 6D}) {
                Optional<MovementPoint> other = at(1D + away, 64D, 0D);
                MovementPoint moved = new MovementPoint(1D + away, 64D, 0D);
                assertEquals(GuardProximity.inPlace(order, SETTINGS, 0.6F, moved, other, other),
                        GuardProximity.inPlace(order, formation, 0.6F, moved, other, other), order + " " + away);
                assertEquals(GuardProximity.inPlace(order, SETTINGS, 0.6F, self, other, other),
                        GuardProximity.inPlace(order, formation, 0.6F, self, other, other), order + " " + away);
            }
        }
    }
}
