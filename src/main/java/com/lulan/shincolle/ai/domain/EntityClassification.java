package com.lulan.shincolle.ai.domain;

public record EntityClassification(
        boolean valid,
        boolean player,
        boolean playerInvulnerable,
        boolean entityInvulnerable,
        boolean invisible,
        boolean invisibleDetectable,
        boolean lineOfSightEligible) {
}
