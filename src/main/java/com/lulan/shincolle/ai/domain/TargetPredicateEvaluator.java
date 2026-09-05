package com.lulan.shincolle.ai.domain;

import java.util.Objects;

public final class TargetPredicateEvaluator {
    private TargetPredicateEvaluator() {
    }

    public static boolean test(
            TargetPredicateKind kind,
            ClassifiedTargetObservation target,
            TargetPredicatePolicy policy) {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(policy, "policy");
        if (!target.entity().valid()) {
            return false;
        }
        return switch (kind) {
            case FRIENDLY_AUTOMATIC -> testFriendlyAutomatic(target, policy);
            case FRIENDLY_REVENGE -> testFriendlyRevenge(target);
            case HOSTILE_AUTOMATIC -> testHostileAutomatic(target, policy);
            case HOSTILE_REVENGE -> testHostileRevenge(target);
        };
    }

    private static boolean testFriendlyAutomatic(
            ClassifiedTargetObservation target, TargetPredicatePolicy policy) {
        EntityClassification entity = target.entity();
        RelationClassification relation = target.relation();
        TargetTraitClassification traits = target.traits();

        if (entity.player()) {
            if (entity.playerInvulnerable()) {
                return false;
            }
            return switch (policy.shipAttackPlayer()) {
                case 1 -> relation.banned();
                case 2 -> !relation.allied();
                case 3 -> !relation.sameOwner();
                default -> false;
            };
        }
        if (entity.entityInvulnerable()
                || !entity.invisibleDetectable()
                || !entity.lineOfSightEligible()) {
            return false;
        }
        if (traits.airplane()) {
            return policy.antiAir() && relation.banned();
        }
        if (traits.submarine()) {
            return policy.antiSubmarine() && relation.banned();
        }
        if (policy.pvpFirst() && traits.friendlyShipOrMount() && relation.banned()) {
            return true;
        }
        if (traits.hostileShip() || traits.monsterOrSlime()) {
            return true;
        }
        if (traits.customAttackClassListed() && !relation.allied()) {
            return true;
        }
        return traits.shipOwner() && !relation.allied();
    }

    private static boolean testFriendlyRevenge(ClassifiedTargetObservation target) {
        EntityClassification entity = target.entity();
        if (entity.player() && entity.playerInvulnerable()) {
            return false;
        }
        if (entity.entityInvulnerable() || !entity.invisibleDetectable()) {
            return false;
        }
        if (target.traits().shipOwner()) {
            return !target.relation().allied();
        }
        return !target.relation().sameOwner();
    }

    private static boolean testHostileAutomatic(
            ClassifiedTargetObservation target, TargetPredicatePolicy policy) {
        EntityClassification entity = target.entity();
        if (entity.player()) {
            return !entity.playerInvulnerable() && policy.mobShipsAttackPlayer();
        }
        if (entity.entityInvulnerable() || entity.invisible()) {
            return false;
        }
        if (target.traits().hostileShip()) {
            return false;
        }
        if (target.traits().friendlyShipOrMount()) {
            return true;
        }
        return target.traits().shipOwner() && !target.relation().sameOwner();
    }

    private static boolean testHostileRevenge(ClassifiedTargetObservation target) {
        EntityClassification entity = target.entity();
        if (entity.player()) {
            return !entity.playerInvulnerable();
        }
        if (entity.entityInvulnerable() || entity.invisible()) {
            return false;
        }
        if (target.traits().hostileShip()) {
            return false;
        }
        if (target.traits().friendlyShip()) {
            return true;
        }
        return !target.relation().sameOwner();
    }
}
