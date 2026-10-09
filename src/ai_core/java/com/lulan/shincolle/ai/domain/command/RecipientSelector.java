package com.lulan.shincolle.ai.domain.command;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class RecipientSelector {
    private RecipientSelector() { }

    public static List<CommandRecipient> select(CommandMode mode, List<SlotObservation> slots) {
        return selectDetailed(mode, slots).recipients();
    }

    public static RecipientSelection selectDetailed(CommandMode mode, List<SlotObservation> slots) {
        Set<ShipUid> seen = new HashSet<>();
        List<CommandRecipient> recipients = new ArrayList<>();
        List<RejectedEntry> rejected = new ArrayList<>();
        List<SlotObservation> ordered = slots.stream()
                .sorted(Comparator.comparingInt(SlotObservation::slot)).toList();

        if (mode == CommandMode.SINGLE) {
            for (SlotObservation slot : ordered) {
                if (slot.ship().isEmpty() || !slot.selected() || !slot.known()
                        || !seen.add(slot.ship().get())) {
                    continue;
                }
                CommandRecipient recipient = new CommandRecipient(slot.slot(), slot.ship().get().value());
                if (slot.sunk()) {
                    rejected.add(new RejectedEntry(recipient, CommandRejectReason.SUNK));
                    continue;
                }
                recipients.add(recipient);
                break;
            }
        } else {
            for (SlotObservation slot : ordered) {
                if (slot.ship().isEmpty() || mode == CommandMode.GROUP && !slot.selected()) {
                    continue;
                }
                if (!seen.add(slot.ship().get())) {
                    continue;
                }
                if (recipients.size() >= 6) {
                    break;
                }
                recipients.add(new CommandRecipient(slot.slot(), slot.ship().get().value()));
            }
        }

        return new RecipientSelection(recipients, rejected);
    }
}
