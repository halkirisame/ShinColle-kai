package com.lulan.shincolle.ai.domain.command;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandNotificationTest {

    @Test
    void singleSunkShipIncludesItsLocationWithoutMoreMarker() {
        CommandDispatchResult result = result(rejected(2, 12, CommandRejectReason.SUNK));

        CommandNotification.Summary summary = CommandNotification.summarize(result,
                List.of(new CommandNotification.SunkLocation(2, 12, 20, 64, -5)));

        assertEquals(new CommandNotification.SunkSummary(1,
                new CommandNotification.SunkLocation(2, 12, 20, 64, -5), false), summary.sunk());
    }

    @Test
    void multipleSunkShipsUseLowestSlotLocationAndMoreMarker() {
        CommandDispatchResult result = result(
                rejected(4, 14, CommandRejectReason.SUNK), rejected(1, 11, CommandRejectReason.SUNK));

        CommandNotification.Summary summary = CommandNotification.summarize(result, List.of(
                new CommandNotification.SunkLocation(4, 14, 40, 70, 4),
                new CommandNotification.SunkLocation(1, 11, 10, 65, 1)));

        assertEquals(new CommandNotification.SunkSummary(2,
                new CommandNotification.SunkLocation(1, 11, 10, 65, 1), true), summary.sunk());
    }

    @Test
    void reasonsKeepExistingOrderAndAppendSunkWhileIgnoringFuel() {
        CommandDispatchResult result = result(
                rejected(3, 13, CommandRejectReason.SUNK),
                rejected(2, 12, CommandRejectReason.NOT_FOUND),
                rejected(1, 11, CommandRejectReason.OTHER_DIMENSION),
                rejected(0, 10, CommandRejectReason.OUT_OF_RANGE),
                rejected(4, 14, CommandRejectReason.NO_FUEL));

        CommandNotification.Summary summary = CommandNotification.summarize(result,
                List.of(new CommandNotification.SunkLocation(3, 13, 30, 70, 3)));

        assertEquals(List.of(
                new CommandNotification.ReasonCount(CommandRejectReason.OUT_OF_RANGE, 1),
                new CommandNotification.ReasonCount(CommandRejectReason.OTHER_DIMENSION, 1),
                new CommandNotification.ReasonCount(CommandRejectReason.NOT_FOUND, 1),
                new CommandNotification.ReasonCount(CommandRejectReason.SUNK, 1)), summary.reasons());
        assertFalse(summary.noRecipient());
        assertFalse(summary.formationUnsatisfied());
        assertTrue(summary.sunk() != null);
    }

    @Test
    void formationFailureKeepsSunkReasonForItsAdditionalNotice() {
        CommandDispatchResult result = result(
                rejected(0, 10, CommandRejectReason.FORMATION_UNSATISFIED),
                rejected(1, 11, CommandRejectReason.SUNK));

        CommandNotification.Summary summary = CommandNotification.summarize(result,
                List.of(new CommandNotification.SunkLocation(1, 11, 10, 65, 1)));

        assertTrue(summary.formationUnsatisfied());
        assertEquals(List.of(new CommandNotification.ReasonCount(CommandRejectReason.SUNK, 1)),
                summary.reasons());
        assertEquals(1, summary.sunk().count());
    }

    private static CommandDispatchResult result(RejectedEntry... entries) {
        return new CommandDispatchResult(List.of(), List.of(entries));
    }

    private static RejectedEntry rejected(int slot, int uid, CommandRejectReason reason) {
        return new RejectedEntry(new CommandRecipient(slot, uid), reason);
    }
}
