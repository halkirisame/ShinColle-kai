package com.lulan.shincolle.ai.domain;

import com.lulan.shincolle.api.target.TargetTrait;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CombatTargetSelectionTest {

    private static final DimensionKey OVERWORLD = new DimensionKey("minecraft", "overworld");
    private static final DimensionKey NETHER = new DimensionKey("minecraft", "the_nether");
    private static final RelationClassification NEUTRAL = relation(false, false, false);
    private static final RelationClassification ALLIED = relation(false, true, false);
    private static final RelationClassification BANNED = relation(false, false, true);
    private static final TargetPredicatePolicy NO_FLAGS = policy(false, false, false, 0);
    private static final TargetHandle SOURCE = handle(-1L);

    @Test
    void antiAirEligibilityUsesPolicyAndBannedRelation() {
        ClassifiedTargetObservation missile = classified(traits(
                false, true, false, false, false, false, false, true, false), BANNED);

        assertFalse(eligible(missile, NO_FLAGS));
        assertTrue(eligible(missile, policy(false, true, false, 0)));
        assertFalse(eligible(withRelation(missile, ALLIED), policy(false, true, false, 0)));
        assertEquals(TargetPriorityTier.ANTI_AIR, priority(missile, NO_FLAGS));
    }

    @Test
    void playerCustomListOverridesRejectedDefaultPolicy() {
        ClassifiedTargetObservation ordinary = player(false, NEUTRAL);
        ClassifiedTargetObservation custom = player(true, NEUTRAL);

        assertFalse(eligible(ordinary, NO_FLAGS));
        assertTrue(eligible(custom, NO_FLAGS));
        assertFalse(eligible(player(true, NEUTRAL, true), policy(false, false, false, 3)));
    }

    @Test
    void genericOwnerFallbackIsRemovedButCustomTargetRemains() {
        ClassifiedTargetObservation genericOwner = classified(traits(
                false, false, false, false, false, false, false, true, false), NEUTRAL);
        ClassifiedTargetObservation custom = classified(traits(
                false, false, false, false, false, false, false, true, true), NEUTRAL);

        assertFalse(eligible(genericOwner, NO_FLAGS));
        assertTrue(eligible(custom, NO_FLAGS));
        assertFalse(eligible(withRelation(custom, ALLIED), NO_FLAGS));
    }

    @Test
    void pvpEligibilityIsGatedAndCustomOverrideUsesNormalPriority() {
        ClassifiedTargetObservation ship = classified(traits(
                false, false, false, true, false, false, false, true, false), BANNED);
        ClassifiedTargetObservation customShip = classified(traits(
                false, false, false, true, false, false, false, true, true), NEUTRAL);

        assertFalse(eligible(ship, NO_FLAGS));
        assertTrue(eligible(ship, policy(true, false, false, 0)));
        assertTrue(eligible(customShip, NO_FLAGS));
        assertEquals(TargetPriorityTier.PVP_FIRST, priority(ship, policy(true, false, false, 0)));
        assertEquals(TargetPriorityTier.NORMAL, priority(customShip, NO_FLAGS));
    }

    @Test
    void highestPriorityTierWinsBeforeLowerTierDistance() {
        TargetCandidate normal = candidate(1L, 1D, monster(), NEUTRAL);
        TargetCandidate submarine = candidate(2L, 50D, submarine(), BANNED);
        TargetCandidate airplane = candidate(3L, 100D, airplane(), BANNED);
        AtomicInteger randomCalls = new AtomicInteger();

        TargetState state = TargetCandidateSelector.select(
                12L,
                SOURCE,
                List.of(normal, submarine, airplane),
                TargetPredicateKind.FRIENDLY_AUTOMATIC,
                policy(false, true, true, 0),
                bound -> randomCalls.getAndIncrement());

        assertEquals(airplane.handle(), state.selectedTarget().orElseThrow());
        assertEquals(List.of(airplane, submarine, normal), state.orderedEligibleCandidates());
        assertEquals(0, randomCalls.get());
    }

    @Test
    void canonicalOrderingUsesDistanceThenUnsignedHandle() {
        TargetCandidate farther = candidate(1L, 4D, monster(), NEUTRAL);
        TargetCandidate unsignedLater = candidate(Long.MIN_VALUE, 1D, monster(), NEUTRAL);
        TargetCandidate unsignedEarlier = candidate(0L, 1D, monster(), NEUTRAL);

        TargetState state = TargetCandidateSelector.select(
                20L,
                SOURCE,
                List.of(farther, unsignedLater, unsignedEarlier),
                TargetPredicateKind.FRIENDLY_AUTOMATIC,
                NO_FLAGS,
                bound -> 0);

        assertEquals(List.of(unsignedEarlier, unsignedLater, farther), state.orderedEligibleCandidates());
        assertEquals(unsignedEarlier.handle(), state.selectedTarget().orElseThrow());
    }

    @Test
    void randomChoosesOnlyFromNearestThreeInHighestTier() {
        TargetCandidate first = candidate(1L, 1D, monster(), NEUTRAL);
        TargetCandidate second = candidate(2L, 2D, monster(), NEUTRAL);
        TargetCandidate third = candidate(3L, 3D, monster(), NEUTRAL);
        TargetCandidate fourth = candidate(4L, 4D, monster(), NEUTRAL);
        AtomicInteger observedBound = new AtomicInteger();

        TargetState state = TargetCandidateSelector.select(
                30L,
                SOURCE,
                List.of(fourth, second, first, third),
                TargetPredicateKind.FRIENDLY_AUTOMATIC,
                NO_FLAGS,
                bound -> {
                    observedBound.set(bound);
                    return 2;
                });

        assertEquals(3, observedBound.get());
        assertEquals(third.handle(), state.selectedTarget().orElseThrow());
    }

    @Test
    void smallPoolsDoNotConsumeRandomAndProfilerSeesFiltering() {
        TargetCandidate accepted = candidate(1L, 1D, monster(), NEUTRAL);
        TargetCandidate rejected = candidate(2L, 2D, missile(), BANNED);
        AtomicInteger randomCalls = new AtomicInteger();
        List<Integer> profile = new ArrayList<>();

        TargetState state = TargetCandidateSelector.select(
                40L,
                SOURCE,
                List.of(rejected, accepted),
                TargetPredicateKind.FRIENDLY_AUTOMATIC,
                NO_FLAGS,
                bound -> randomCalls.getAndIncrement(),
                (candidateCount, eligibleCount) -> {
                    profile.add(candidateCount);
                    profile.add(eligibleCount);
                });

        assertEquals(accepted.handle(), state.selectedTarget().orElseThrow());
        assertEquals(0, randomCalls.get());
        assertEquals(List.of(2, 1), profile);
    }

    @Test
    void invalidRandomIndexIsRejected() {
        List<TargetCandidate> candidates = List.of(
                candidate(1L, 1D, monster(), NEUTRAL),
                candidate(2L, 2D, monster(), NEUTRAL),
                candidate(3L, 3D, monster(), NEUTRAL));

        assertThrows(IllegalArgumentException.class, () -> TargetCandidateSelector.select(
                50L,
                SOURCE,
                candidates,
                TargetPredicateKind.FRIENDLY_AUTOMATIC,
                NO_FLAGS,
                bound -> bound));
    }

    @Test
    void sourceAndOtherDimensionAreRejectedAtSelectionBoundary() {
        TargetCandidate self = new TargetCandidate(SOURCE, monster(), 1D);
        TargetCandidate otherDimension = new TargetCandidate(
                new TargetHandle(UUID.randomUUID(), NETHER), monster(), 1D);

        TargetState state = TargetCandidateSelector.select(
                55L,
                SOURCE,
                List.of(self, otherDimension),
                TargetPredicateKind.FRIENDLY_AUTOMATIC,
                NO_FLAGS,
                bound -> 0);

        assertTrue(state.orderedEligibleCandidates().isEmpty());
        assertTrue(state.selectedTarget().isEmpty());
    }

    @Test
    void targetStateIsImmutableAndTickScoped() {
        TargetCandidate candidate = candidate(1L, 1D, monster(), NEUTRAL);
        List<TargetCandidate> mutable = new ArrayList<>(List.of(candidate));
        TargetState selected = new TargetState(
                60L, SOURCE, mutable, Optional.of(candidate.handle()));
        mutable.clear();
        TargetState nextTick = TargetState.empty(61L, SOURCE);

        assertEquals(List.of(candidate), selected.orderedEligibleCandidates());
        assertEquals(candidate, selected.selectedCandidate().orElseThrow());
        assertTrue(nextTick.selectedTarget().isEmpty());
        assertNotSame(selected, nextTick);
        assertThrows(IllegalArgumentException.class,
                () -> new TargetState(60L, SOURCE, List.of(), Optional.of(candidate.handle())));
    }

    @Test
    void candidateRejectsInvalidDistance() {
        assertThrows(IllegalArgumentException.class,
                () -> new TargetCandidate(handle(1L), monster(), Double.NaN));
        assertThrows(IllegalArgumentException.class,
                () -> new TargetCandidate(handle(1L), monster(), -1D));
        assertThrows(IllegalArgumentException.class, () -> TargetCandidateSelector.select(
                -1L,
                SOURCE,
                List.of(),
                TargetPredicateKind.FRIENDLY_AUTOMATIC,
                NO_FLAGS,
                bound -> 0));
    }

    private static boolean eligible(
            ClassifiedTargetObservation target,
            TargetPredicatePolicy policy) {
        return TargetEligibilityEvaluator.test(
                TargetPredicateKind.FRIENDLY_AUTOMATIC, target, policy);
    }

    private static TargetPriorityTier priority(
            ClassifiedTargetObservation target,
            TargetPredicatePolicy policy) {
        return TargetPriorityClassifier.classify(
                TargetPredicateKind.FRIENDLY_AUTOMATIC, target, policy);
    }

    private static TargetCandidate candidate(
            long leastSignificantBits,
            double distanceSquared,
            ClassifiedTargetObservation observation,
            RelationClassification relation) {
        return new TargetCandidate(
                handle(leastSignificantBits),
                withRelation(observation, relation),
                distanceSquared);
    }

    private static TargetHandle handle(long leastSignificantBits) {
        return new TargetHandle(new UUID(0L, leastSignificantBits), OVERWORLD);
    }

    private static ClassifiedTargetObservation withRelation(
            ClassifiedTargetObservation observation,
            RelationClassification relation) {
        return new ClassifiedTargetObservation(observation.entity(), relation, observation.traits());
    }

    private static ClassifiedTargetObservation player(
            boolean custom,
            RelationClassification relation) {
        return player(custom, relation, false);
    }

    private static ClassifiedTargetObservation player(
            boolean custom,
            RelationClassification relation,
            boolean invulnerable) {
        return new ClassifiedTargetObservation(
                new EntityClassification(true, true, invulnerable, false, false, true, true),
                relation,
                traits(false, false, false, false, false, false, false, false, custom));
    }

    private static ClassifiedTargetObservation missile() {
        return classified(traits(false, true, false, false, false, false, false, true, false), NEUTRAL);
    }

    private static ClassifiedTargetObservation airplane() {
        return classified(traits(true, false, false, false, false, false, false, true, false), NEUTRAL);
    }

    private static ClassifiedTargetObservation submarine() {
        return classified(traits(false, false, true, false, false, false, false, true, false), NEUTRAL);
    }

    private static ClassifiedTargetObservation monster() {
        return classified(traits(false, false, false, false, false, false, true, false, false), NEUTRAL);
    }

    private static ClassifiedTargetObservation classified(
            TargetTraitClassification traits,
            RelationClassification relation) {
        return new ClassifiedTargetObservation(
                new EntityClassification(true, false, false, false, false, true, true),
                relation,
                traits);
    }

    private static TargetTraitClassification traits(
            boolean airplane,
            boolean missile,
            boolean submarine,
            boolean friendlyShip,
            boolean mount,
            boolean hostileShip,
            boolean monster,
            boolean shipOwner,
            boolean custom) {
        Set<TargetTrait> traitSet = new java.util.HashSet<>();
        if (airplane) {
            traitSet.add(TargetTrait.AIRBORNE);
            traitSet.add(TargetTrait.ANTI_AIR_ELIGIBLE);
        }
        if (missile) {
            traitSet.add(TargetTrait.ANTI_AIR_ELIGIBLE);
            traitSet.add(TargetTrait.SPECIAL);
        }
        if (submarine) {
            traitSet.add(TargetTrait.SUBMARINE);
            traitSet.add(TargetTrait.ANTI_SUBMARINE_ELIGIBLE);
        }
        if (friendlyShip || hostileShip) {
            traitSet.add(TargetTrait.SHIP);
        }
        return new TargetTraitClassification(
                airplane,
                missile,
                submarine,
                friendlyShip,
                mount,
                hostileShip,
                monster,
                shipOwner,
                custom,
                traitSet);
    }

    private static RelationClassification relation(boolean sameOwner, boolean allied, boolean banned) {
        return new RelationClassification(sameOwner, allied, banned);
    }

    private static TargetPredicatePolicy policy(
            boolean pvpFirst,
            boolean antiAir,
            boolean antiSubmarine,
            int playerPolicy) {
        return new TargetPredicatePolicy(pvpFirst, antiAir, antiSubmarine, playerPolicy, true);
    }
}
