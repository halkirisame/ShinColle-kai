package com.lulan.shincolle.ai.domain.command;

import com.lulan.shincolle.ai.domain.DimensionKey;
import com.lulan.shincolle.ai.domain.TargetHandle;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandPipelineTest {
    private static final DimensionKey OVERWORLD = new DimensionKey("minecraft", "overworld");
    private static final DimensionKey NETHER = new DimensionKey("minecraft", "the_nether");
    private static final TargetHandle TARGET = new TargetHandle(UUID.randomUUID(), OVERWORLD);

    /** A slot as the team list stores it: zero or less while it holds no ship. */
    private static SlotObservation slot(int slot, int rawShipUid, boolean selected, boolean known, boolean sunk) {
        return new SlotObservation(slot, ShipUid.fromLegacy(rawShipUid), selected, known, sunk);
    }

    @Test
    void singleCutsOffAfterFirstKnownSelectedEvenWhenValidationRejectsIt() {
        List<SlotObservation> slots = List.of(slot(0, 10, true, false, false),
                slot(1, 11, true, true, false),
                slot(2, 12, true, true, false));
        assertEquals(List.of(new CommandRecipient(1, 11)), RecipientSelector.select(CommandMode.SINGLE, slots));
        for (RecipientObservation observation : List.of(
                new RecipientObservation(false, false, false, 0, false),
                new RecipientObservation(true, true, true, 4096.01, false),
                new RecipientObservation(true, true, false, 0, false))) {
            assertTrue(RecipientValidator.validate(CommandKind.ATTACK, observation).isPresent());
            assertEquals(1, RecipientSelector.select(CommandMode.SINGLE, slots).size());
        }
    }

    @Test
    void groupAndFormationSelectInSlotOrder() {
        List<SlotObservation> slots = List.of(slot(3, 13, false, true, false),
                slot(2, 12, true, false, false),
                slot(0, 10, true, true, false),
                slot(1, 0, true, false, false));
        assertEquals(List.of(new CommandRecipient(0, 10), new CommandRecipient(2, 12)),
                RecipientSelector.select(CommandMode.GROUP, slots));
        assertEquals(List.of(new CommandRecipient(0, 10), new CommandRecipient(2, 12),
                        new CommandRecipient(3, 13)), RecipientSelector.select(CommandMode.FORMATION, slots));
        assertTrue(RecipientSelector.select(CommandMode.SINGLE, List.of()).isEmpty());
    }

    @Test
    void emptySlotsAreNeverRecipientsInAnyMode() {
        assertEquals(java.util.Optional.empty(), ShipUid.fromLegacy(0));
        assertEquals(java.util.Optional.empty(), ShipUid.fromLegacy(-1));
        assertEquals(java.util.Optional.empty(), ShipUid.fromLegacy(Integer.MIN_VALUE));
        assertEquals(java.util.Optional.of(new ShipUid(1)), ShipUid.fromLegacy(1));
        // an empty slot that is selected and known is passed over, and the next ship is taken
        for (int empty : new int[] {0, -1, Integer.MIN_VALUE}) {
            List<SlotObservation> slots = List.of(slot(0, empty, true, true, false),
                    slot(1, empty, true, true, true),
                    slot(2, 12, true, true, false));
            RecipientSelection single = RecipientSelector.selectDetailed(CommandMode.SINGLE, slots);
            assertEquals(List.of(new CommandRecipient(2, 12)), single.recipients(), "raw " + empty);
            assertTrue(single.rejected().isEmpty(), "an empty slot is not a sunk ship: raw " + empty);
            assertEquals(List.of(new CommandRecipient(2, 12)), RecipientSelector.select(CommandMode.GROUP, slots));
            assertEquals(List.of(new CommandRecipient(2, 12)),
                    RecipientSelector.select(CommandMode.FORMATION, slots));
        }
        // empty slots do not use up the six places, and the same ship in two slots is taken once
        List<SlotObservation> crowded = List.of(slot(0, 0, true, true, false), slot(1, 11, true, true, false),
                slot(2, 11, true, true, false), slot(3, 13, true, true, false), slot(4, 14, true, true, false),
                slot(5, 15, true, true, false), slot(6, 16, true, true, false), slot(7, 17, true, true, false),
                slot(8, 18, true, true, false));
        assertEquals(List.of(new CommandRecipient(1, 11), new CommandRecipient(3, 13), new CommandRecipient(4, 14),
                        new CommandRecipient(5, 15), new CommandRecipient(6, 16), new CommandRecipient(7, 17)),
                RecipientSelector.select(CommandMode.FORMATION, crowded));
    }

    @Test
    void singleSkipsSunkSelectedKnownSlotsAndReportsThemAsRejected() {
        List<SlotObservation> slots = List.of(
                slot(2, 12, true, true, false),
                slot(0, 10, true, true, true),
                slot(1, 11, true, true, false),
                slot(3, 13, true, true, true));

        RecipientSelection selection = RecipientSelector.selectDetailed(CommandMode.SINGLE, slots);

        assertEquals(List.of(new CommandRecipient(1, 11)), selection.recipients());
        assertEquals(List.of(new RejectedEntry(new CommandRecipient(0, 10), CommandRejectReason.SUNK)),
                selection.rejected());
    }

    @Test
    void singleWithOnlySunkKnownSlotsHasNoRecipientAndRejectsEachSunkSlot() {
        List<SlotObservation> slots = List.of(slot(0, 10, true, true, true),
                slot(1, 11, true, true, true));

        RecipientSelection selection = RecipientSelector.selectDetailed(CommandMode.SINGLE, slots);

        assertTrue(selection.recipients().isEmpty());
        assertEquals(List.of(new RejectedEntry(new CommandRecipient(0, 10), CommandRejectReason.SUNK),
                        new RejectedEntry(new CommandRecipient(1, 11), CommandRejectReason.SUNK)),
                selection.rejected());
    }

    @Test
    void groupKeepsSunkShipsAsRecipientsForPerShipRejection() {
        List<SlotObservation> slots = List.of(slot(0, 10, true, true, true),
                slot(1, 11, true, true, false));

        RecipientSelection selection = RecipientSelector.selectDetailed(CommandMode.GROUP, slots);

        assertEquals(List.of(new CommandRecipient(0, 10), new CommandRecipient(1, 11)),
                selection.recipients());
        assertTrue(selection.rejected().isEmpty());
    }

    @Test
    void rangeBoundaryAndSittingExemptions() {
        assertTrue(RecipientValidator.validate(CommandKind.GUARD_ENTITY,
                new RecipientObservation(true, true, true, 4096, false)).isEmpty());
        assertEquals(CommandRejectReason.OUT_OF_RANGE, RecipientValidator.validate(CommandKind.MOVE,
                new RecipientObservation(true, true, true, 4096.0001, false)).orElseThrow());
        assertTrue(RecipientValidator.validate(CommandKind.TOGGLE_SIT,
                new RecipientObservation(true, true, true, 10000, true)).isEmpty());
    }

    @Test
    void formationGateRequiresFiveMatchingActiveMembersAndKeepsFuelReason() {
        for (int size : new int[]{5, 6}) {
            assertTrue(FormationGate.move(size, List.of(), false));
        }
        assertFalse(FormationGate.move(4, List.of(), false));
        assertFalse(FormationGate.move(5, List.of(CommandRejectReason.OUT_OF_RANGE), false));
        assertFalse(FormationGate.move(5, List.of(CommandRejectReason.OTHER_DIMENSION), false));
        assertFalse(FormationGate.move(5, List.of(), true));
        assertTrue(FormationGate.move(5, List.of(CommandRejectReason.NO_FUEL), false));
        assertFalse(FormationGate.move(4, List.of(CommandRejectReason.NO_FUEL), false));
        assertEquals(CommandRejectReason.NO_FUEL, FormationGate.rejection(CommandRejectReason.NO_FUEL));
        assertEquals(CommandRejectReason.FORMATION_UNSATISFIED,
                FormationGate.rejection(CommandRejectReason.OUT_OF_RANGE));
    }

    @Test
    void commandDecisionDistinguishesTargetDimensionAndArrivalMode() {
        CommandPos pos = new CommandPos(1, 2, 3);
        assertInstanceOf(ShipCommand.CancelAttack.class, CommandDecision.attack(TARGET, TARGET));
        assertInstanceOf(ShipCommand.Follow.class, CommandDecision.guardEntity(TARGET, TARGET));
        assertInstanceOf(ShipCommand.Follow.class,
                CommandDecision.position(OVERWORLD, pos, false, false, true, OVERWORLD, pos));
        assertInstanceOf(ShipCommand.GuardPosition.class,
                CommandDecision.position(OVERWORLD, pos, false, false, true, NETHER, pos, true));
        assertInstanceOf(ShipCommand.Move.class,
                CommandDecision.position(OVERWORLD, pos, true, true, true, OVERWORLD, pos));
    }
}
