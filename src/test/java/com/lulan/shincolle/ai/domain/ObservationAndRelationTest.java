package com.lulan.shincolle.ai.domain;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ObservationAndRelationTest {

    @Test
    void namespacedObservationKeysValidateAndOrderCanonically() {
        DimensionKey overworld = new DimensionKey("minecraft", "overworld");
        DimensionKey nether = new DimensionKey("minecraft", "the_nether");

        assertEquals("minecraft:overworld", overworld.toString());
        assertTrue(overworld.compareTo(nether) < 0);
        assertEquals("shincolle_kai:ship", new EntityTypeKey("shincolle_kai", "ship").toString());
        assertThrows(IllegalArgumentException.class, () -> new DimensionKey("Minecraft", "overworld"));
        assertThrows(IllegalArgumentException.class, () -> new EntityTypeKey("minecraft", "Bad Path"));
    }

    @Test
    void targetHandleUsesDimensionThenUnsignedUuidOrder() {
        DimensionKey overworld = new DimensionKey("minecraft", "overworld");
        DimensionKey nether = new DimensionKey("minecraft", "the_nether");
        TargetHandle low = new TargetHandle(new UUID(0, 0), overworld);
        TargetHandle unsignedHigh = new TargetHandle(new UUID(Long.MIN_VALUE, 0), overworld);

        assertTrue(low.compareTo(unsignedHigh) < 0);
        assertTrue(new TargetHandle(new UUID(0, 0), overworld)
                .compareTo(new TargetHandle(new UUID(0, 0), nether)) < 0);
    }

    @Test
    void relationIdentityRemovesLegacySentinelsFromDomainLogic() {
        assertEquals(RelationIdentity.unaffiliated(), RelationIdentity.fromLegacyPlayerUid(0));
        assertEquals(RelationIdentity.ownerless(), RelationIdentity.fromLegacyPlayerUid(-1));
        assertEquals(RelationIdentity.hostile(2), RelationIdentity.fromLegacyPlayerUid(-2));
        assertEquals(RelationIdentity.hostile(2147483648L),
                RelationIdentity.fromLegacyPlayerUid(Integer.MIN_VALUE));
        assertEquals(RelationIdentity.player(7), RelationIdentity.fromLegacyPlayerUid(7));
        assertThrows(IllegalStateException.class, () -> RelationIdentity.ownerless().playerUid());
    }

    @Test
    void timedCurrentTargetUsesInclusiveFreshnessBoundary() {
        RawEntityObservation target = observation(RelationIdentity.player(7));
        CurrentTargetRawObservation present = CurrentTargetRawObservation.observed(target, 100);
        CurrentTargetRawObservation absent = CurrentTargetRawObservation.absent(100);

        assertTrue(present.observation().value().isPresent());
        assertFalse(absent.observation().value().isPresent());
        assertTrue(present.isFreshAt(105, 5));
        assertFalse(present.isFreshAt(106, 5));
        assertEquals(6, present.ageAt(106));
        assertThrows(IllegalArgumentException.class, () -> present.ageAt(99));
        assertThrows(IllegalArgumentException.class, () -> present.isFreshAt(100, -1));
    }

    @Test
    void rawObservationRejectsNonFinitePositionsAndNullFacts() {
        assertThrows(IllegalArgumentException.class,
                () -> new ObservationPosition(Double.NaN, 0, 0));
        assertThrows(NullPointerException.class,
                () -> new RawEntityObservation(null, new EntityTypeKey("minecraft", "pig"),
                        RelationIdentity.unaffiliated(), new ObservationPosition(0, 0, 0), true));
    }

    @Test
    void hostileAndPlayerRelationsPreserveLegacyTruthTable() {
        RelationClassification hostilePair = RelationClassifier.classify(
                RelationIdentity.hostile(2), RelationIdentity.hostile(3), RelationPolicy.empty());
        RelationClassification sameHostile = RelationClassifier.classify(
                RelationIdentity.hostile(2), RelationIdentity.hostile(2), RelationPolicy.empty());
        RelationClassification crossFaction = RelationClassifier.classify(
                RelationIdentity.player(7), RelationIdentity.hostile(2), RelationPolicy.empty());

        assertEquals(new RelationClassification(false, true, false), hostilePair);
        assertEquals(new RelationClassification(true, true, false), sameHostile);
        assertEquals(new RelationClassification(false, false, true), crossFaction);
        assertEquals(crossFaction, RelationClassifier.classify(
                RelationIdentity.hostile(2), RelationIdentity.player(7), RelationPolicy.empty()));
    }

    @Test
    void playerTeamRelationsAreHostDirectedAndIndependentlyRepresented() {
        RelationPolicy policy = new RelationPolicy(Map.of(
                7, new TeamRelationSnapshot(70, Set.of(80), Set.of(90)),
                8, new TeamRelationSnapshot(80, Set.of(), Set.of()),
                9, new TeamRelationSnapshot(90, Set.of(), Set.of())));

        assertEquals(new RelationClassification(false, true, false), RelationClassifier.classify(
                RelationIdentity.player(7), RelationIdentity.player(8), policy));
        assertEquals(new RelationClassification(false, false, false), RelationClassifier.classify(
                RelationIdentity.player(8), RelationIdentity.player(7), policy));
        assertEquals(new RelationClassification(false, false, true), RelationClassifier.classify(
                RelationIdentity.player(7), RelationIdentity.player(9), policy));
    }

    @Test
    void samePlayerAndMalformedPolicyKeepIndependentLegacyBooleans() {
        RelationPolicy policy = new RelationPolicy(Map.of(
                7, new TeamRelationSnapshot(70, Set.of(70), Set.of(70))));

        assertEquals(new RelationClassification(true, true, true), RelationClassifier.classify(
                RelationIdentity.player(7), RelationIdentity.player(7), policy));
        assertEquals(new RelationClassification(false, false, false), RelationClassifier.classify(
                RelationIdentity.ownerless(), RelationIdentity.player(7), policy));
    }

    @Test
    void relationPolicyDefensivelyCopiesNestedCollectionsAndMaps() {
        Set<Integer> allies = new HashSet<>(Set.of(80));
        Map<Integer, TeamRelationSnapshot> teams = new HashMap<>();
        teams.put(7, new TeamRelationSnapshot(70, allies, Set.of()));
        RelationPolicy policy = new RelationPolicy(teams);
        allies.add(90);
        teams.clear();

        assertEquals(Set.of(80), policy.teamForPlayer(7).orElseThrow().alliedTeamIds());
        assertThrows(UnsupportedOperationException.class,
                () -> policy.teamsByPlayerUid().put(8,
                        new TeamRelationSnapshot(80, Set.of(), Set.of())));
    }

    @Test
    void rawObservationClassifierOverloadUsesOnlyCapturedFacts() {
        RawEntityObservation target = observation(RelationIdentity.hostile(2));

        assertEquals(new RelationClassification(false, false, true), RelationClassifier.classify(
                RelationIdentity.player(7), target, RelationPolicy.empty()));
    }

    private static RawEntityObservation observation(RelationIdentity relationIdentity) {
        return new RawEntityObservation(
                new TargetHandle(new UUID(0, 1), new DimensionKey("minecraft", "overworld")),
                new EntityTypeKey("minecraft", "pig"),
                relationIdentity,
                new ObservationPosition(1, 2, 3),
                true);
    }
}
