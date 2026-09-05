package com.lulan.shincolle.ai.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TargetPredicateEvaluatorTest {

    private static final RelationClassification NEUTRAL =
            new RelationClassification(false, false, false);
    private static final RelationClassification ALLY =
            new RelationClassification(false, true, false);
    private static final RelationClassification BANNED =
            new RelationClassification(false, false, true);
    private static final RelationClassification SAME_OWNER =
            new RelationClassification(true, true, false);

    @Test
    void entityClassificationRejectsMissingDeadAndSelfTargets() {
        FactsBuilder builder = facts();
        assertFalse(test(TargetPredicateKind.FRIENDLY_AUTOMATIC,
                builder.targetPresent(false).build(), NEUTRAL, policy()));
        assertFalse(test(TargetPredicateKind.FRIENDLY_REVENGE,
                builder.targetPresent(true).targetAlive(false).build(), NEUTRAL, policy()));
        assertFalse(test(TargetPredicateKind.HOSTILE_AUTOMATIC,
                builder.targetAlive(true).sameEntity(true).build(), NEUTRAL, policy()));
        assertFalse(test(TargetPredicateKind.HOSTILE_REVENGE,
                builder.sameEntity(false).hostPresent(false).build(), NEUTRAL, policy()));
    }

    @Test
    void entityClassificationKeepsCommonChecksIndependent() {
        EntityClassification classification = TargetEntityClassifier.classify(facts()
                .player(true)
                .entityInvulnerable(true)
                .invisible(true)
                .hostDetectsInvisible(false)
                .lineOfSightRequired(true)
                .hasLineOfSight(false)
                .build());

        assertTrue(classification.valid());
        assertTrue(classification.player());
        assertTrue(classification.entityInvulnerable());
        assertFalse(classification.invisibleDetectable());
        assertFalse(classification.lineOfSightEligible());
    }

    @Test
    void targetTraitsKeepMissileAndOwnerNonExclusive() {
        TargetTraitClassification traits = TargetTraitClassifier.classify(facts()
                .abyssMissile(true)
                .shipOwner(true)
                .build());

        assertTrue(traits.abyssMissile());
        assertTrue(traits.shipOwner());
        assertFalse(traits.airplane());
    }

    @Test
    void friendlyPlayerConfigReturnsBeforeCustomTargetTrait() {
        TargetPredicateFacts player = facts()
                .player(true)
                .customAttackClassListed(true)
                .build();

        assertFalse(test(TargetPredicateKind.FRIENDLY_AUTOMATIC,
                player, NEUTRAL, policyWithPlayerMode(0)));
        assertTrue(test(TargetPredicateKind.FRIENDLY_AUTOMATIC,
                player, BANNED, policyWithPlayerMode(1)));
        assertFalse(test(TargetPredicateKind.FRIENDLY_AUTOMATIC,
                player, NEUTRAL, policyWithPlayerMode(1)));
        assertTrue(test(TargetPredicateKind.FRIENDLY_AUTOMATIC,
                player, NEUTRAL, policyWithPlayerMode(2)));
        assertFalse(test(TargetPredicateKind.FRIENDLY_AUTOMATIC,
                player, ALLY, policyWithPlayerMode(2)));
        assertTrue(test(TargetPredicateKind.FRIENDLY_AUTOMATIC,
                player, NEUTRAL, policyWithPlayerMode(3)));
        assertFalse(test(TargetPredicateKind.FRIENDLY_AUTOMATIC,
                player, SAME_OWNER, policyWithPlayerMode(3)));
    }

    @Test
    void airplaneUsesExplicitAntiAirBranchBeforeOwnerFallback() {
        TargetPredicateFacts airplane = facts().airplane(true).shipOwner(true).build();

        assertFalse(test(TargetPredicateKind.FRIENDLY_AUTOMATIC,
                airplane, BANNED, policy(false, false, false)));
        assertTrue(test(TargetPredicateKind.FRIENDLY_AUTOMATIC,
                airplane, BANNED, policy(false, true, false)));
        assertFalse(test(TargetPredicateKind.FRIENDLY_AUTOMATIC,
                airplane, NEUTRAL, policy(false, true, false)));
    }

    @Test
    void friendlyAutomaticAppliesCommonAndSubmarineChecks() {
        assertFalse(test(TargetPredicateKind.FRIENDLY_AUTOMATIC,
                facts().entityInvulnerable(true).monsterOrSlime(true).build(),
                NEUTRAL, policy()));
        assertFalse(test(TargetPredicateKind.FRIENDLY_AUTOMATIC,
                facts().invisible(true).hostDetectsInvisible(false).monsterOrSlime(true).build(),
                NEUTRAL, policy()));
        assertFalse(test(TargetPredicateKind.FRIENDLY_AUTOMATIC,
                facts().lineOfSightRequired(true).hasLineOfSight(false).monsterOrSlime(true).build(),
                NEUTRAL, policy()));
        assertFalse(test(TargetPredicateKind.FRIENDLY_AUTOMATIC,
                facts().submarine(true).build(), BANNED, policy(false, false, false)));
        assertTrue(test(TargetPredicateKind.FRIENDLY_AUTOMATIC,
                facts().submarine(true).build(), BANNED, policy(false, false, true)));
    }

    @Test
    void friendlyAutomaticPreservesPvpAndHostilePriority() {
        TargetPredicateFacts friendlyShip = facts().friendlyShip(true).shipOwner(true).build();
        TargetPredicateFacts hostileShip = facts().hostileShip(true).shipOwner(true).build();

        assertTrue(test(TargetPredicateKind.FRIENDLY_AUTOMATIC,
                friendlyShip, BANNED, policy(true, false, false)));
        assertTrue(test(TargetPredicateKind.FRIENDLY_AUTOMATIC,
                hostileShip, ALLY, policy(false, false, false)));
    }

    @Test
    void missileKeepsCurrentGenericOwnerFallbackUntilCutover() {
        TargetPredicateFacts missile = facts()
                .abyssMissile(true)
                .shipOwner(true)
                .build();

        assertTrue(test(TargetPredicateKind.FRIENDLY_AUTOMATIC,
                missile, NEUTRAL, policy(false, false, false)));
        assertFalse(test(TargetPredicateKind.FRIENDLY_AUTOMATIC,
                missile, ALLY, policy(false, true, false)));
    }

    @Test
    void genericOwnerAndCustomClassUseCurrentAllianceRule() {
        TargetPredicateFacts owner = facts().shipOwner(true).build();
        TargetPredicateFacts custom = facts().customAttackClassListed(true).build();

        assertTrue(test(TargetPredicateKind.FRIENDLY_AUTOMATIC, owner, NEUTRAL, policy()));
        assertFalse(test(TargetPredicateKind.FRIENDLY_AUTOMATIC, owner, ALLY, policy()));
        assertTrue(test(TargetPredicateKind.FRIENDLY_AUTOMATIC, custom, NEUTRAL, policy()));
        assertFalse(test(TargetPredicateKind.FRIENDLY_AUTOMATIC, custom, ALLY, policy()));
    }

    @Test
    void invisibleFriendlyRevengeRequiresCapturedDetection() {
        TargetPredicateFacts undetected = facts()
                .invisible(true)
                .hostDetectsInvisible(false)
                .shipOwner(true)
                .build();
        TargetPredicateFacts detected = facts()
                .invisible(true)
                .hostDetectsInvisible(true)
                .shipOwner(true)
                .build();

        assertFalse(test(TargetPredicateKind.FRIENDLY_REVENGE,
                undetected, NEUTRAL, policy()));
        assertTrue(test(TargetPredicateKind.FRIENDLY_REVENGE,
                detected, NEUTRAL, policy()));
        assertFalse(test(TargetPredicateKind.FRIENDLY_REVENGE,
                detected, ALLY, policy()));
        assertFalse(test(TargetPredicateKind.FRIENDLY_REVENGE,
                facts().build(), SAME_OWNER, policy()));
    }

    @Test
    void hostileRevengeRejectsDeadPlayerBeforePlayerBranch() {
        TargetPredicateFacts deadPlayer = facts().player(true).targetAlive(false).build();
        TargetPredicateFacts alivePlayer = facts().player(true).build();
        TargetPredicateFacts invulnerablePlayer = facts()
                .player(true)
                .playerInvulnerable(true)
                .build();

        assertFalse(test(TargetPredicateKind.HOSTILE_REVENGE,
                deadPlayer, NEUTRAL, policy()));
        assertTrue(test(TargetPredicateKind.HOSTILE_REVENGE,
                alivePlayer, NEUTRAL, policy()));
        assertFalse(test(TargetPredicateKind.HOSTILE_REVENGE,
                invulnerablePlayer, NEUTRAL, policy()));
    }

    @Test
    void hostileSelectorsPreserveVisibilityAndOwnershipBranches() {
        TargetPredicateFacts friendlyShip = facts().friendlyShip(true).shipOwner(true).build();
        TargetPredicateFacts hostileShip = facts().hostileShip(true).shipOwner(true).build();
        TargetPredicateFacts invisibleOwner = facts()
                .invisible(true)
                .hostDetectsInvisible(true)
                .shipOwner(true)
                .build();

        assertTrue(test(TargetPredicateKind.HOSTILE_AUTOMATIC,
                friendlyShip, SAME_OWNER, policy()));
        assertFalse(test(TargetPredicateKind.HOSTILE_AUTOMATIC,
                hostileShip, NEUTRAL, policy()));
        assertFalse(test(TargetPredicateKind.HOSTILE_REVENGE,
                invisibleOwner, NEUTRAL, policy()));
        assertFalse(test(TargetPredicateKind.HOSTILE_AUTOMATIC,
                facts().shipOwner(true).build(), SAME_OWNER, policy()));
        assertTrue(test(TargetPredicateKind.HOSTILE_AUTOMATIC,
                facts().shipOwner(true).build(), NEUTRAL, policy()));
    }

    @Test
    void hostileAutomaticPlayerUsesConfigBeforeOtherTraits() {
        TargetPredicateFacts player = facts()
                .player(true)
                .hostileShip(true)
                .invisible(true)
                .build();
        TargetPredicateFacts invulnerable = facts()
                .player(true)
                .playerInvulnerable(true)
                .build();

        assertTrue(test(TargetPredicateKind.HOSTILE_AUTOMATIC,
                player, NEUTRAL, new TargetPredicatePolicy(false, false, false, 0, true)));
        assertFalse(test(TargetPredicateKind.HOSTILE_AUTOMATIC,
                player, NEUTRAL, new TargetPredicatePolicy(false, false, false, 0, false)));
        assertFalse(test(TargetPredicateKind.HOSTILE_AUTOMATIC,
                invulnerable, NEUTRAL, new TargetPredicatePolicy(false, false, false, 0, true)));
    }

    @Test
    void pureBoundaryRejectsNullInputs() {
        ClassifiedTargetObservation classified = ClassifiedTargetObservation.classify(
                facts().build(), NEUTRAL);

        assertThrows(NullPointerException.class,
                () -> TargetEntityClassifier.classify(null));
        assertThrows(NullPointerException.class,
                () -> TargetTraitClassifier.classify(null));
        assertThrows(NullPointerException.class,
                () -> TargetPredicateEvaluator.test(null, classified, policy()));
        assertThrows(NullPointerException.class,
                () -> new ClassifiedTargetObservation(null, NEUTRAL,
                        TargetTraitClassifier.classify(facts().build())));
    }

    private static boolean test(
            TargetPredicateKind kind,
            TargetPredicateFacts facts,
            RelationClassification relation,
            TargetPredicatePolicy policy) {
        return TargetPredicateEvaluator.test(
                kind,
                ClassifiedTargetObservation.classify(facts, relation),
                policy);
    }

    private static TargetPredicatePolicy policy() {
        return policy(false, false, false);
    }

    private static TargetPredicatePolicy policy(boolean pvp, boolean antiAir, boolean antiSubmarine) {
        return new TargetPredicatePolicy(pvp, antiAir, antiSubmarine, 0, true);
    }

    private static TargetPredicatePolicy policyWithPlayerMode(int mode) {
        return new TargetPredicatePolicy(false, false, false, mode, true);
    }

    private static FactsBuilder facts() {
        return new FactsBuilder();
    }

    private static final class FactsBuilder {
        private boolean hostPresent = true;
        private boolean targetPresent = true;
        private boolean targetAlive = true;
        private boolean sameEntity;
        private boolean player;
        private boolean playerInvulnerable;
        private boolean entityInvulnerable;
        private boolean invisible;
        private boolean hostDetectsInvisible;
        private boolean lineOfSightRequired;
        private boolean hasLineOfSight = true;
        private boolean airplane;
        private boolean abyssMissile;
        private boolean submarine;
        private boolean friendlyShip;
        private boolean mount;
        private boolean hostileShip;
        private boolean monsterOrSlime;
        private boolean shipOwner;
        private boolean customAttackClassListed;

        FactsBuilder hostPresent(boolean value) {
            hostPresent = value;
            return this;
        }

        FactsBuilder targetPresent(boolean value) {
            targetPresent = value;
            return this;
        }

        FactsBuilder targetAlive(boolean value) {
            targetAlive = value;
            return this;
        }

        FactsBuilder sameEntity(boolean value) {
            sameEntity = value;
            return this;
        }

        FactsBuilder player(boolean value) {
            player = value;
            return this;
        }

        FactsBuilder playerInvulnerable(boolean value) {
            playerInvulnerable = value;
            return this;
        }

        FactsBuilder entityInvulnerable(boolean value) {
            entityInvulnerable = value;
            return this;
        }

        FactsBuilder invisible(boolean value) {
            invisible = value;
            return this;
        }

        FactsBuilder hostDetectsInvisible(boolean value) {
            hostDetectsInvisible = value;
            return this;
        }

        FactsBuilder lineOfSightRequired(boolean value) {
            lineOfSightRequired = value;
            return this;
        }

        FactsBuilder hasLineOfSight(boolean value) {
            hasLineOfSight = value;
            return this;
        }

        FactsBuilder airplane(boolean value) {
            airplane = value;
            return this;
        }

        FactsBuilder abyssMissile(boolean value) {
            abyssMissile = value;
            return this;
        }

        FactsBuilder shipOwner(boolean value) {
            shipOwner = value;
            return this;
        }

        FactsBuilder friendlyShip(boolean value) {
            friendlyShip = value;
            return this;
        }

        FactsBuilder submarine(boolean value) {
            submarine = value;
            return this;
        }

        FactsBuilder hostileShip(boolean value) {
            hostileShip = value;
            return this;
        }

        FactsBuilder monsterOrSlime(boolean value) {
            monsterOrSlime = value;
            return this;
        }

        FactsBuilder customAttackClassListed(boolean value) {
            customAttackClassListed = value;
            return this;
        }

        TargetPredicateFacts build() {
            return new TargetPredicateFacts(
                    hostPresent,
                    targetPresent,
                    targetAlive,
                    sameEntity,
                    player,
                    playerInvulnerable,
                    entityInvulnerable,
                    invisible,
                    hostDetectsInvisible,
                    lineOfSightRequired,
                    hasLineOfSight,
                    airplane,
                    abyssMissile,
                    submarine,
                    friendlyShip,
                    mount,
                    hostileShip,
                    monsterOrSlime,
                    shipOwner,
                    customAttackClassListed);
        }
    }
}
