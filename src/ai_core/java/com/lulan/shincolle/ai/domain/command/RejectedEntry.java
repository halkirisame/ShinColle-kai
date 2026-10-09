package com.lulan.shincolle.ai.domain.command;

public record RejectedEntry(CommandRecipient recipient, CommandRejectReason reason) { }
