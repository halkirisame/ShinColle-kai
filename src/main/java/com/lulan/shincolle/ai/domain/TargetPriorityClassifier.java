package com.lulan.shincolle.ai.domain;

import com.lulan.shincolle.api.target.TargetTrait;

import java.util.Objects;

/** Assigns an eligible candidate to a priority tier without deciding attackability. */
public final class TargetPriorityClassifier {
    private TargetPriorityClassifier() {
    }

    public static TargetPriorityTier classify(
            TargetPredicateKind kind,
            ClassifiedTargetObservation target,
            TargetPredicatePolicy policy) {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(policy, "policy");
        if (kind != TargetPredicateKind.FRIENDLY_AUTOMATIC) {
            return TargetPriorityTier.NORMAL;
        }
        TargetTraitClassification traits = target.traits();
        if (traits.hasTrait(TargetTrait.ANTI_AIR_ELIGIBLE)) {
            return TargetPriorityTier.ANTI_AIR;
        }
        if (traits.hasTrait(TargetTrait.ANTI_SUBMARINE_ELIGIBLE)) {
            return TargetPriorityTier.ANTI_SUBMARINE;
        }
        if (policy.pvpFirst() && traits.friendlyShipOrMount() && target.relation().banned()) {
            return TargetPriorityTier.PVP_FIRST;
        }
        return TargetPriorityTier.NORMAL;
    }
}
