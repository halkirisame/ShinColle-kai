package com.lulan.shincolle.ai.domain.movement;

/** Why a planned teleport was not carried out. */
public enum TeleportDenial {
    SKILL_NOT_AUTHORIZED,
    OTHER_DIMENSION,
    /** The body teleported too recently. */
    COOLDOWN,
    CHUNK_NOT_LOADED,
    OUTSIDE_WORLD_BORDER,
    /** Neither the destination nor any point beside it has room for the body. */
    NO_FREE_SPACE
}
