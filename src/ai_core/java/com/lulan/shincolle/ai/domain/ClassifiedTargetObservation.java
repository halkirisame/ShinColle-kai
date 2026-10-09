package com.lulan.shincolle.ai.domain;

import java.util.Objects;

public record ClassifiedTargetObservation(
        EntityClassification entity,
        RelationClassification relation,
        TargetTraitClassification traits) {
    public ClassifiedTargetObservation {
        Objects.requireNonNull(entity, "entity");
        Objects.requireNonNull(relation, "relation");
        Objects.requireNonNull(traits, "traits");
    }

    public static ClassifiedTargetObservation classify(
            TargetPredicateFacts facts, RelationClassification relation) {
        Objects.requireNonNull(facts, "facts");
        return new ClassifiedTargetObservation(
                TargetEntityClassifier.classify(facts),
                relation,
                TargetTraitClassifier.classify(facts));
    }
}
