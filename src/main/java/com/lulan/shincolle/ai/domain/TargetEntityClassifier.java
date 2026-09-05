package com.lulan.shincolle.ai.domain;

import java.util.Objects;

public final class TargetEntityClassifier {
    private TargetEntityClassifier() {
    }

    public static EntityClassification classify(TargetPredicateFacts facts) {
        Objects.requireNonNull(facts, "facts");
        boolean valid = facts.hostPresent()
                && facts.targetPresent()
                && facts.targetAlive()
                && !facts.sameEntity();
        return new EntityClassification(
                valid,
                facts.player(),
                facts.playerInvulnerable(),
                facts.entityInvulnerable(),
                facts.invisible(),
                !facts.invisible() || facts.hostDetectsInvisible(),
                !facts.lineOfSightRequired() || facts.hasLineOfSight());
    }
}
