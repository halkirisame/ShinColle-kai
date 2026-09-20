package com.lulan.shincolle.ai.domain;

public record TargetPredicateFacts(
        boolean hostPresent,
        boolean targetPresent,
        boolean targetAlive,
        boolean sameEntity,
        boolean player,
        boolean playerInvulnerable,
        boolean entityInvulnerable,
        boolean invisible,
        boolean hostDetectsInvisible,
        boolean lineOfSightRequired,
        boolean hasLineOfSight,
        boolean airplane,
        boolean abyssMissile,
        boolean submarine,
        boolean friendlyShip,
        boolean mount,
        boolean hostileShip,
        boolean monsterOrSlime,
        boolean shipOwner,
        boolean customAttackClassListed) {
}
