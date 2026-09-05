package com.lulan.shincolle.ai.domain;

import com.lulan.shincolle.api.target.TargetTrait;

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
            Set<TargetTrait> registeredTraits) {
        Objects.requireNonNull(facts, "facts");
        Set<TargetTrait> checkedRegisteredTraits = Objects.requireNonNull(registeredTraits, "registeredTraits");
        EnumSet<TargetTrait> traits = checkedRegisteredTraits.isEmpty()
                ? EnumSet.noneOf(TargetTrait.class)
                : EnumSet.copyOf(checkedRegisteredTraits);
        if (facts.airplane()) {
            traits.add(TargetTrait.AIRBORNE);
            traits.add(TargetTrait.ANTI_AIR_ELIGIBLE);
            traits.add(TargetTrait.SUMMONED);
        }
        if (facts.abyssMissile()) {
            traits.add(TargetTrait.ANTI_AIR_ELIGIBLE);
            traits.add(TargetTrait.SPECIAL);
        }
        if (facts.submarine()) {
            traits.add(TargetTrait.SUBMARINE);
            traits.add(TargetTrait.ANTI_SUBMARINE_ELIGIBLE);
        }
        if (facts.friendlyShip() || facts.hostileShip()) {
            traits.add(TargetTrait.SHIP);
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
                facts.customAttackClassListed(),
                traits);
    }
}
