package com.lulan.shincolle.ai.domain.command;

import com.lulan.shincolle.ai.domain.DimensionKey;
import com.lulan.shincolle.ai.domain.TargetHandle;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandStateTest {
    private static final DimensionKey OVERWORLD = new DimensionKey("minecraft", "overworld");
    private static final DimensionKey NETHER = new DimensionKey("minecraft", "the_nether");
    private static final DimensionKey CUSTOM = new DimensionKey("another_mod", "moon");
    private static final TargetHandle TARGET = new TargetHandle(UUID.fromString(
            "71cd54bd-70f8-4f72-bc7a-43b33a9a8383"), CUSTOM);
    private static final CommandPos POSITION = new CommandPos(-1, -1, -1);

    @Test
    void reducerHandlesEveryCommandAndShipTransition() {
        ShipCommandState initial = ShipCommandState.INITIAL;
        ShipCommandState attack = CommandStateReducer.apply(initial,
                new CommandStateOp.Apply(new ShipCommand.Attack(TARGET)));
        assertEquals(new ShipCommandState(new MovementOrder.Follow(), false, Optional.of(TARGET)), attack);
        assertEquals(initial, CommandStateReducer.apply(attack,
                new CommandStateOp.Apply(new ShipCommand.CancelAttack())));
        ShipCommandState entity = CommandStateReducer.apply(attack,
                new CommandStateOp.Apply(new ShipCommand.GuardEntity(TARGET)));
        assertEquals(new ShipCommandState(new MovementOrder.GuardEntity(TARGET), false, Optional.of(TARGET)), entity);
        for (boolean release : List.of(false, true)) {
            ShipCommandState move = CommandStateReducer.apply(entity,
                    new CommandStateOp.Apply(new ShipCommand.Move(CUSTOM, POSITION, release)));
            assertEquals(new ShipCommandState(new MovementOrder.MoveTo(CUSTOM, POSITION, release),
                    false, Optional.of(TARGET)), move);
            ShipCommandState guard = CommandStateReducer.apply(move,
                    new CommandStateOp.Apply(new ShipCommand.GuardPosition(CUSTOM, POSITION, release)));
            assertEquals(new ShipCommandState(new MovementOrder.GuardPosition(CUSTOM, POSITION, release),
                    false, Optional.of(TARGET)), guard);
            assertEquals(new ShipCommandState(new MovementOrder.Follow(), false, Optional.of(TARGET)),
                    CommandStateReducer.apply(guard, new CommandStateOp.Apply(new ShipCommand.Follow())));
            assertEquals(new ShipCommandState(new MovementOrder.Follow(), false, Optional.of(TARGET)),
                    CommandStateReducer.apply(guard, new CommandStateOp.EndMovement()));
            ShipCommandState sitting = CommandStateReducer.apply(guard,
                    new CommandStateOp.Apply(new ShipCommand.SetSitting(true)));
            assertEquals(new ShipCommandState(guard.movement(), true, Optional.empty()), sitting);
            assertEquals(new ShipCommandState(guard.movement(), false, Optional.empty()),
                    CommandStateReducer.apply(sitting, new CommandStateOp.Apply(new ShipCommand.SetSitting(false))));
            assertEquals(new ShipCommandState(guard.movement(), false, Optional.empty()),
                    CommandStateReducer.apply(new ShipCommandState(guard.movement(), true, Optional.empty()),
                            new CommandStateOp.StandUp()));
            assertEquals(new ShipCommandState(guard.movement(), false, Optional.empty()),
                    CommandStateReducer.apply(guard, new CommandStateOp.ClearManualAttack()));
        }
    }

    @Test
    void codecRoundTripsEveryOrderIncludingMinusOneDestination() {
        for (DimensionKey dimension : List.of(OVERWORLD, NETHER, CUSTOM)) {
            for (boolean sitting : List.of(false, true)) {
                for (boolean release : List.of(false, true)) {
                    for (MovementOrder movement : List.of(new MovementOrder.Follow(),
                            new MovementOrder.MoveTo(dimension, POSITION, release),
                            new MovementOrder.GuardPosition(dimension, POSITION, release),
                            new MovementOrder.GuardEntity(new TargetHandle(TARGET.uuid(), dimension)))) {
                        ShipCommandState state = new ShipCommandState(movement, sitting, Optional.of(TARGET));
                        assertEquals(new ShipCommandState(movement, sitting, Optional.empty()),
                                LegacyCommandCodec.decode(LegacyCommandCodec.encode(state)));
                    }
                }
            }
        }
    }

    @Test
    void onlyMoveAndPositionGuardOrdersNameABlock() {
        for (boolean release : List.of(false, true)) {
            assertEquals(Optional.of(POSITION), new MovementOrder.MoveTo(CUSTOM, POSITION, release).destination());
            assertEquals(Optional.of(POSITION),
                    new MovementOrder.GuardPosition(CUSTOM, POSITION, release).destination());
        }
        assertEquals(Optional.empty(), new MovementOrder.Follow().destination());
        assertEquals(Optional.empty(), new MovementOrder.GuardEntity(TARGET).destination());
    }

    @Test
    void clearedTupleIsExactlyTheSevenClearedFields() {
        LegacyCommandFields cleared = new LegacyCommandFields(-1, -1, -1, 0, 0, null, null, false, false, false, -1);
        assertTrue(LegacyCommandCodec.isClearedTuple(cleared));
        assertInstanceOf(MovementOrder.Follow.class, LegacyCommandCodec.decode(cleared).movement());
        // the follow flag, the one-shot flag, sitting and the legacy dimension number are not part of it
        for (LegacyCommandFields same : List.of(
                new LegacyCommandFields(-1, -1, -1, 0, 0, null, null, true, false, false, -1),
                new LegacyCommandFields(-1, -1, -1, 0, 0, null, null, false, true, false, -1),
                new LegacyCommandFields(-1, -1, -1, 0, 0, null, null, false, false, true, -1),
                new LegacyCommandFields(-1, -1, -1, -1, 0, null, null, false, false, false, -1),
                new LegacyCommandFields(-1, -1, -1, 1, 0, null, null, false, false, false, -1))) {
            assertTrue(LegacyCommandCodec.isClearedTuple(same), same.toString());
            assertInstanceOf(MovementOrder.Follow.class, LegacyCommandCodec.decode(same).movement());
        }
        // one field off at a time: none is the cleared tuple, and none decodes to follow through it
        List<LegacyCommandFields> notCleared = List.of(
                new LegacyCommandFields(-1, -1, -1, 0, 1, null, null, false, false, false, -1),
                new LegacyCommandFields(-1, -1, -1, 0, -1, null, null, false, false, false, -1),
                new LegacyCommandFields(0, -1, -1, 0, 0, null, null, false, false, false, -1),
                new LegacyCommandFields(-1, 0, -1, 0, 0, null, null, false, false, false, -1),
                new LegacyCommandFields(-1, -1, 0, 0, 0, null, null, false, false, false, -1),
                new LegacyCommandFields(-1, -1, -1, 0, 0, OVERWORLD, null, false, false, false, -1),
                new LegacyCommandFields(-1, -1, -1, 0, 0, null, TARGET.uuid(), false, false, false, -1),
                new LegacyCommandFields(-1, -1, -1, 0, 0, null, null, false, false, false, 0));
        for (LegacyCommandFields fields : notCleared) {
            assertFalse(LegacyCommandCodec.isClearedTuple(fields), fields.toString());
            MovementOrder order = LegacyCommandCodec.decode(fields).movement();
            assertTrue(order instanceof MovementOrder.MoveTo || order instanceof MovementOrder.GuardPosition,
                    fields + " -> " + order);
        }
        // an entity guard without its entity decodes to follow, but is not the cleared tuple the load repairs
        LegacyCommandFields lostEntity = new LegacyCommandFields(-1, -1, -1, 0, 2, null, null, false, false, false,
                -1);
        assertFalse(LegacyCommandCodec.isClearedTuple(lostEntity));
        assertInstanceOf(MovementOrder.Follow.class, LegacyCommandCodec.decode(lostEntity).movement());
        // the two forms answer alike
        for (LegacyCommandFields fields : notCleared) {
            assertEquals(LegacyCommandCodec.isClearedTuple(fields), LegacyCommandCodec.isClearedTuple(
                    fields.guardType(), fields.guardX(), fields.guardY(), fields.guardZ(), fields.guardId(),
                    fields.guardedDimension() != null, fields.guardedEntityUuid() != null));
        }
        assertTrue(LegacyCommandCodec.isClearedTuple(0, -1, -1, -1, -1, false, false));
    }

    @Test
    void codecDecodesLegacyAndIncompleteFields() {
        assertInstanceOf(MovementOrder.Follow.class, LegacyCommandCodec.decode(new LegacyCommandFields(
                8, 9, 10, -1, 1, NETHER, TARGET.uuid(), true, true, true, 42)).movement());
        assertInstanceOf(MovementOrder.GuardEntity.class, LegacyCommandCodec.decode(new LegacyCommandFields(
                -1, -1, -1, -1, 2, null, TARGET.uuid(), false, false, false, -1), NETHER).movement());
        assertInstanceOf(MovementOrder.Follow.class, LegacyCommandCodec.decode(new LegacyCommandFields(
                -1, -1, -1, 0, 2, null, null, false, false, false, -1)).movement());
        assertInstanceOf(MovementOrder.Follow.class, LegacyCommandCodec.decode(new LegacyCommandFields(
                -1, -1, -1, 0, 0, null, null, false, false, false, -1)).movement());
        assertEquals(new MovementOrder.MoveTo(OVERWORLD, POSITION, false),
                LegacyCommandCodec.decode(new LegacyCommandFields(-1, -1, -1, 0, 0,
                        null, null, false, false, false, 17)).movement());
        assertEquals(new MovementOrder.MoveTo(NETHER, POSITION, false),
                LegacyCommandCodec.decode(new LegacyCommandFields(-1, -1, -1, -1, 0,
                        NETHER, null, false, false, false, -1)).movement());
        assertEquals(new MovementOrder.GuardPosition(NETHER, POSITION, false),
                LegacyCommandCodec.decode(new LegacyCommandFields(-1, -1, -1, -1, 1,
                        null, null, false, false, false, -1)).movement());
        assertEquals(new MovementOrder.MoveTo(NETHER, new CommandPos(4, -17, 9), true),
                LegacyCommandCodec.decode(new LegacyCommandFields(4, -17, 9, -1, 0,
                        null, null, false, true, false, -1)).movement());
        assertEquals(new MovementOrder.MoveTo(OVERWORLD, new CommandPos(4, 5, 6), false),
                LegacyCommandCodec.decode(new LegacyCommandFields(4, 5, 6, 0, -2,
                        null, null, false, false, false, -1)).movement());
        assertEquals(new MovementOrder.GuardPosition(OVERWORLD, new CommandPos(4, 5, 6), false),
                LegacyCommandCodec.decode(new LegacyCommandFields(4, 5, 6, 0, 3,
                        null, null, false, false, false, -1)).movement());
    }
}
