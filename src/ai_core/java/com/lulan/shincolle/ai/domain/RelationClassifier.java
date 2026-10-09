package com.lulan.shincolle.ai.domain;

import java.util.Objects;
import java.util.Optional;

public final class RelationClassifier {
    private RelationClassifier() {
    }

    public static RelationClassification classify(
            RelationIdentity self,
            RawEntityObservation target,
            RelationPolicy policy) {
        Objects.requireNonNull(target, "target");
        return classify(self, target.relationIdentity(), policy);
    }

    public static RelationClassification classify(
            RelationIdentity self,
            RelationIdentity target,
            RelationPolicy policy) {
        Objects.requireNonNull(self, "self");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(policy, "policy");

        boolean sameOwner = hasOwnerIdentity(self)
                && self.kind() == target.kind()
                && self.stableId() == target.stableId();

        if (self.kind() == RelationIdentity.Kind.HOSTILE
                && target.kind() == RelationIdentity.Kind.HOSTILE) {
            return new RelationClassification(sameOwner, true, false);
        }

        if (isPlayerHostilePair(self, target)) {
            return new RelationClassification(false, false, true);
        }

        if (self.kind() == RelationIdentity.Kind.PLAYER
                && target.kind() == RelationIdentity.Kind.PLAYER) {
            int selfUid = self.playerUid();
            int targetUid = target.playerUid();
            Optional<TeamRelationSnapshot> selfTeam = policy.teamForPlayer(selfUid);
            Optional<TeamRelationSnapshot> targetTeam = policy.teamForPlayer(targetUid);
            boolean allied = selfUid == targetUid;
            boolean banned = false;
            if (selfTeam.isPresent() && targetTeam.isPresent()) {
                int targetTeamId = targetTeam.orElseThrow().teamId();
                allied |= selfTeam.orElseThrow().alliedTeamIds().contains(targetTeamId);
                banned = selfTeam.orElseThrow().bannedTeamIds().contains(targetTeamId);
            }
            return new RelationClassification(sameOwner, allied, banned);
        }

        return new RelationClassification(false, false, false);
    }

    private static boolean hasOwnerIdentity(RelationIdentity identity) {
        return identity.kind() == RelationIdentity.Kind.PLAYER
                || identity.kind() == RelationIdentity.Kind.HOSTILE;
    }

    private static boolean isPlayerHostilePair(RelationIdentity self, RelationIdentity target) {
        return self.kind() == RelationIdentity.Kind.PLAYER
                && target.kind() == RelationIdentity.Kind.HOSTILE
                || self.kind() == RelationIdentity.Kind.HOSTILE
                && target.kind() == RelationIdentity.Kind.PLAYER;
    }
}
