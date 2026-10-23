package com.lulan.shincolle.ai.domain;


import java.util.Objects;
import java.util.Set;

public record TargetTraitClassification(
        boolean airplane,
        boolean abyssMissile,
        boolean submarine,
        boolean friendlyShip,
        boolean mount,
        boolean hostileShip,
        boolean monsterOrSlime,
        boolean shipOwner,
        boolean playerOwned,
        boolean customAttackClassListed,
        Set<TargetTraitKind> traits) {

    public TargetTraitClassification {
        traits = Set.copyOf(Objects.requireNonNull(traits, "traits"));
    }

    public boolean friendlyShipOrMount() {
        return friendlyShip || mount;
    }

    public boolean hasTrait(TargetTraitKind trait) {
        return traits.contains(Objects.requireNonNull(trait, "trait"));
    }
}
