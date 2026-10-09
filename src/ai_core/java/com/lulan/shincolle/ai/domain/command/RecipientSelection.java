package com.lulan.shincolle.ai.domain.command;

import java.util.List;

public record RecipientSelection(List<CommandRecipient> recipients, List<RejectedEntry> rejected) {
    public RecipientSelection {
        recipients = List.copyOf(recipients);
        rejected = List.copyOf(rejected);
    }
}
