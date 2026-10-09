package com.lulan.shincolle.ai.domain.command;

import java.util.List;

public record CommandDispatchResult(List<AcceptedEntry> accepted, List<RejectedEntry> rejected) {
    public CommandDispatchResult {
        accepted = List.copyOf(accepted);
        rejected = List.copyOf(rejected);
    }
}
