package com.lulan.shincolle.ai.domain;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

public record RelationPolicy(Map<Integer, TeamRelationSnapshot> teamsByPlayerUid) {
    public RelationPolicy {
        Objects.requireNonNull(teamsByPlayerUid, "teamsByPlayerUid");
        TreeMap<Integer, TeamRelationSnapshot> copy = new TreeMap<>();
        teamsByPlayerUid.forEach((playerUid, team) -> {
            Objects.requireNonNull(playerUid, "playerUid");
            Objects.requireNonNull(team, "team");
            if (playerUid <= 0) {
                throw new IllegalArgumentException("Relation policy keys must be positive player UIDs");
            }
            copy.put(playerUid, team);
        });
        teamsByPlayerUid = Collections.unmodifiableMap(copy);
    }

    public static RelationPolicy empty() {
        return new RelationPolicy(Map.of());
    }

    public Optional<TeamRelationSnapshot> teamForPlayer(int playerUid) {
        return Optional.ofNullable(this.teamsByPlayerUid.get(playerUid));
    }
}
