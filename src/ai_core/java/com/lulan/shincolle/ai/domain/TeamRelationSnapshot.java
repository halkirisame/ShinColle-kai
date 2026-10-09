package com.lulan.shincolle.ai.domain;

import java.util.Objects;
import java.util.Set;

public record TeamRelationSnapshot(
        int teamId,
        Set<Integer> alliedTeamIds,
        Set<Integer> bannedTeamIds) {
    public TeamRelationSnapshot {
        Objects.requireNonNull(alliedTeamIds, "alliedTeamIds");
        Objects.requireNonNull(bannedTeamIds, "bannedTeamIds");
        alliedTeamIds = Set.copyOf(alliedTeamIds);
        bannedTeamIds = Set.copyOf(bannedTeamIds);
    }
}
