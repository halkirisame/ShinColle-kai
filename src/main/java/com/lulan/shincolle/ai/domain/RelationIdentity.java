package com.lulan.shincolle.ai.domain;

import java.util.Objects;

public record RelationIdentity(Kind kind, long stableId) {
    private static final long MAX_HOSTILE_ID = -(long) Integer.MIN_VALUE;

    public RelationIdentity {
        Objects.requireNonNull(kind, "kind");
        if (kind == Kind.PLAYER && (stableId <= 0 || stableId > Integer.MAX_VALUE)) {
            throw new IllegalArgumentException("Player identity must be a positive int UID");
        }
        if (kind == Kind.HOSTILE && (stableId < 2 || stableId > MAX_HOSTILE_ID)) {
            throw new IllegalArgumentException("Hostile identity is outside the legacy PID range");
        }
        if ((kind == Kind.OWNERLESS || kind == Kind.UNAFFILIATED) && stableId != 0) {
            throw new IllegalArgumentException("Identity without an owner must use stable ID zero");
        }
    }

    public static RelationIdentity fromLegacyPlayerUid(int playerUid) {
        if (playerUid > 0) {
            return player(playerUid);
        }
        if (playerUid < -1) {
            return hostile(-(long) playerUid);
        }
        return playerUid == -1 ? ownerless() : unaffiliated();
    }

    public static RelationIdentity player(int playerUid) {
        return new RelationIdentity(Kind.PLAYER, playerUid);
    }

    public static RelationIdentity hostile(long stableId) {
        return new RelationIdentity(Kind.HOSTILE, stableId);
    }

    public static RelationIdentity ownerless() {
        return new RelationIdentity(Kind.OWNERLESS, 0);
    }

    public static RelationIdentity unaffiliated() {
        return new RelationIdentity(Kind.UNAFFILIATED, 0);
    }

    public int playerUid() {
        if (this.kind != Kind.PLAYER) {
            throw new IllegalStateException("Only player identities have a player UID");
        }
        return (int) this.stableId;
    }

    public enum Kind {
        PLAYER,
        HOSTILE,
        OWNERLESS,
        UNAFFILIATED
    }
}
