package com.lulan.shincolle.ai.domain;


import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

public final class TargetTraitClassifier {
    private TargetTraitClassifier() {
    }

    public static TargetTraitClassification classify(TargetPredicateFacts facts) {
        return classify(facts, Set.of());
    }

    public static TargetTraitClassification classify(
            TargetPredicateFacts facts,
            Set<TargetTraitKind> registeredTraits) {
        Objects.requireNonNull(facts, "facts");
        Set<TargetTraitKind> checkedRegisteredTraits = Objects.requireNonNull(registeredTraits, "registeredTraits");
        EnumSet<TargetTraitKind> traits = checkedRegisteredTraits.isEmpty()
                ? EnumSet.noneOf(TargetTraitKind.class)
                : EnumSet.copyOf(checkedRegisteredTraits);
        if (facts.airplane()) {
            traits.add(TargetTraitKind.AIRBORNE);
            traits.add(TargetTraitKind.ANTI_AIR_ELIGIBLE);
            traits.add(TargetTraitKind.SUMMONED);
        }
        if (facts.abyssMissile()) {
            traits.add(TargetTraitKind.ANTI_AIR_ELIGIBLE);
            traits.add(TargetTraitKind.SPECIAL);
        }
        if (facts.submarine()) {
            traits.add(TargetTraitKind.SUBMARINE);
            traits.add(TargetTraitKind.ANTI_SUBMARINE_ELIGIBLE);
        }
        if (facts.friendlyShip() || facts.hostileShip()) {
            traits.add(TargetTraitKind.SHIP);
        }
        return new TargetTraitClassification(
                facts.airplane(),
                facts.abyssMissile(),
                facts.submarine(),
                facts.friendlyShip(),
                facts.mount(),
                facts.hostileShip(),
                facts.monsterOrSlime(),
                facts.shipOwner(),
                facts.playerOwned(),
                facts.customAttackClassListed(),
                traits);
    }
}
