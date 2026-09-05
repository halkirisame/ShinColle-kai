package com.lulan.shincolle.ai.domain;

import com.lulan.shincolle.api.target.TargetTrait;

import java.util.Objects;

/** Pure attackability rules for Stage 2 candidate filtering. */
public final class TargetEligibilityEvaluator {
    private TargetEligibilityEvaluator() {
    }

    public static boolean test(
            TargetPredicateKind kind,
            ClassifiedTargetObservation target,
            TargetPredicatePolicy policy) {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(policy, "policy");
        if (kind != TargetPredicateKind.FRIENDLY_AUTOMATIC) {
            return TargetPredicateEvaluator.test(kind, target, policy);
        }
        return testFriendlyAutomatic(target, policy);
    }

    private static boolean testFriendlyAutomatic(
            ClassifiedTargetObservation target,
            TargetPredicatePolicy policy) {
        EntityClassification entity = target.entity();
        RelationClassification relation = target.relation();
        TargetTraitClassification traits = target.traits();
        if (!entity.valid()) {
            return false;
        }
        if (entity.player()) {
            if (entity.playerInvulnerable()) {
                return false;
            }
            if (playerPolicyAllows(policy.shipAttackPlayer(), relation)) {
                return true;
            }
        }
        if (entity.entityInvulnerable()
                || !entity.invisibleDetectable()
                || !entity.lineOfSightEligible()) {
            return false;
        }
        if (entity.player()) {
            return traits.customAttackClassListed();
        }
        if (traits.hasTrait(TargetTrait.ANTI_AIR_ELIGIBLE)) {
            return policy.antiAir() && relation.banned();
        }
        if (traits.hasTrait(TargetTrait.ANTI_SUBMARINE_ELIGIBLE)) {
            return policy.antiSubmarine() && relation.banned();
        }
        if (policy.pvpFirst() && traits.friendlyShipOrMount() && relation.banned()) {
            return true;
        }
        if (traits.hostileShip() || traits.monsterOrSlime()) {
            return true;
        }
        return traits.customAttackClassListed() && !relation.allied();
    }

    private static boolean playerPolicyAllows(int playerPolicy, RelationClassification relation) {
        return switch (playerPolicy) {
            case 1 -> relation.banned();
            case 2 -> !relation.allied();
            case 3 -> !relation.sameOwner();
            default -> false;
        };
    }
}
