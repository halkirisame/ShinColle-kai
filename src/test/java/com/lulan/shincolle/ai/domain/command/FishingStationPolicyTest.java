package com.lulan.shincolle.ai.domain.command;

import com.lulan.shincolle.ai.domain.DimensionKey;
import com.lulan.shincolle.ai.domain.TargetHandle;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FishingStationPolicyTest {
    private static final DimensionKey DIMENSION = new DimensionKey("minecraft", "overworld");
    private static final CommandPos SPOT = new CommandPos(8, 64, 4);

    @Test
    void acceptedGuardBelongsToFishingOnlyWhenActive() {
        for (boolean release : new boolean[]{true, false}) {
            var guard = new ShipCommand.GuardPosition(DIMENSION, SPOT, release);
            assertEquals(new ShipCommand.GuardPosition(DIMENSION, SPOT, release, true),
                    FishingStationPolicy.accepted(guard, true));
            assertEquals(guard, FishingStationPolicy.accepted(guard, false));
        }
    }

    @Test
    void moveFollowFormationAndEntityGuardNeverBecomeFishingStations() {
        for (ShipCommand command : List.of(new ShipCommand.Move(DIMENSION, SPOT, false),
                new ShipCommand.Follow(), new ShipCommand.FormationMove(DIMENSION, SPOT, 1),
                new ShipCommand.GuardEntity(new TargetHandle(UUID.randomUUID(), DIMENSION)))) {
            for (boolean active : new boolean[]{true, false}) {
                assertSame(command, FishingStationPolicy.accepted(command, active));
            }
        }
    }

    @Test
    void fishingOffEndsOnlyTheMarkedPositionGuard() {
        for (boolean marked : new boolean[]{true, false}) {
            for (boolean active : new boolean[]{true, false}) {
                for (boolean release : new boolean[]{true, false}) {
                    assertEquals(marked && !active, FishingStationPolicy.shouldEnd(
                            new MovementOrder.GuardPosition(DIMENSION, SPOT, release, marked), active));
                }
            }
        }
        assertFalse(FishingStationPolicy.shouldEnd(new MovementOrder.MoveTo(DIMENSION, SPOT, false), false));
        assertFalse(FishingStationPolicy.shouldEnd(new MovementOrder.Follow(), false));
    }

    @Test
    void reducerCopiesTheMarkAndReplacingMovementDropsIt() {
        var state = CommandStateReducer.apply(ShipCommandState.INITIAL, new CommandStateOp.Apply(
                new ShipCommand.GuardPosition(DIMENSION, SPOT, false, true)));
        assertTrue(((MovementOrder.GuardPosition) state.movement()).fishingStation());
        state = CommandStateReducer.apply(state, new CommandStateOp.Apply(new ShipCommand.SetSitting(true)));
        assertTrue(((MovementOrder.GuardPosition) state.movement()).fishingStation());
        state = CommandStateReducer.apply(state, new CommandStateOp.Apply(new ShipCommand.Move(DIMENSION, SPOT, false)));
        assertFalse(FishingStationPolicy.shouldEnd(state.movement(), false));
    }

    @Test
    void legacyCodecRoundTripRetainsOnlyPositionGuardOwnership() {
        for (boolean marked : new boolean[]{true, false}) {
            var state = CommandStateReducer.apply(ShipCommandState.INITIAL, new CommandStateOp.Apply(
                    new ShipCommand.GuardPosition(DIMENSION, SPOT, true, marked)));
            assertEquals(state, LegacyCommandCodec.decode(LegacyCommandCodec.encode(state)));
        }
        var unmarked = new LegacyCommandFields(8, 64, 4, 0, 1, DIMENSION, null,
                false, false, false, -1);
        assertFalse(((MovementOrder.GuardPosition) LegacyCommandCodec.decode(unmarked).movement()).fishingStation());
    }
}
