package com.lulan.shincolle.ai.domain;

import com.lulan.shincolle.api.target.TargetTrait;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CombatTargetFoundationTest {

    private static final DimensionKey OVERWORLD = new DimensionKey("minecraft", "overworld");
    private static final DimensionKey NETHER = new DimensionKey("minecraft", "the_nether");

    @Test
    void missileAndAirplaneReceiveNonExclusiveAntiAirTraits() {
        TargetTraitClassification missile = TargetTraitClassifier.classify(facts(false, true, false));
        TargetTraitClassification airplane = TargetTraitClassifier.classify(facts(true, false, false));

        assertEquals(Set.of(TargetTrait.ANTI_AIR_ELIGIBLE, TargetTrait.SPECIAL), missile.traits());
        assertEquals(Set.of(TargetTrait.AIRBORNE, TargetTrait.ANTI_AIR_ELIGIBLE, TargetTrait.SUMMONED),
                airplane.traits());
        assertTrue(missile.abyssMissile());
        assertTrue(airplane.airplane());
    }

    @Test
    void submarineAndShipTraitsRemainIndependent() {
        TargetTraitClassification classification = TargetTraitClassifier.classify(
                facts(false, false, true, true, false));

        assertTrue(classification.hasTrait(TargetTrait.SUBMARINE));
        assertTrue(classification.hasTrait(TargetTrait.ANTI_SUBMARINE_ELIGIBLE));
        assertTrue(classification.hasTrait(TargetTrait.SHIP));
    }

    @Test
    void registeredTraitsAreUnionedWithoutChangingCapturedFacts() {
        TargetTraitClassification classification = TargetTraitClassifier.classify(
                facts(false, false, false), Set.of(TargetTrait.SPECIAL, TargetTrait.ANTI_AIR_ELIGIBLE));

        assertEquals(Set.of(TargetTrait.SPECIAL, TargetTrait.ANTI_AIR_ELIGIBLE),
                classification.traits());
        assertFalse(classification.airplane());
        assertFalse(classification.abyssMissile());
    }

    @Test
    void resolverRejectsDimensionMismatchWithoutLookup() {
        AtomicBoolean lookedUp = new AtomicBoolean();
        ValidatingTargetResolver<RuntimeTarget> resolver = new ValidatingTargetResolver<>(
                OVERWORLD,
                uuid -> {
                    lookedUp.set(true);
                    return Optional.empty();
                },
                RuntimeTarget::uuid,
                RuntimeTarget::valid);

        assertTrue(resolver.resolve(handle(UUID.randomUUID(), NETHER)).isEmpty());
        assertFalse(lookedUp.get());
    }

    @Test
    void resolverReturnsEmptyForUnloadedMismatchedAndInvalidTargets() {
        UUID requested = UUID.randomUUID();
        RuntimeTarget mismatched = new RuntimeTarget(UUID.randomUUID(), true);
        RuntimeTarget invalid = new RuntimeTarget(requested, false);

        assertTrue(resolver(uuid -> Optional.empty()).resolve(handle(requested, OVERWORLD)).isEmpty());
        assertTrue(resolver(uuid -> Optional.of(mismatched)).resolve(handle(requested, OVERWORLD)).isEmpty());
        assertTrue(resolver(uuid -> Optional.of(invalid)).resolve(handle(requested, OVERWORLD)).isEmpty());
    }

    @Test
    void resolverReturnsLoadedValidTarget() {
        RuntimeTarget target = new RuntimeTarget(UUID.randomUUID(), true);

        assertSame(target, resolver(uuid -> Optional.of(target))
                .resolve(handle(target.uuid(), OVERWORLD)).orElseThrow());
    }

    @Test
    void spatialQueryRejectsInvalidBoundsAndNulls() {
        TargetHandle source = handle(UUID.randomUUID(), OVERWORLD);
        ObservationPosition center = new ObservationPosition(1D, 2D, 3D);

        assertEquals(4D, new SpatialQuery(source, center, 4D, 2D).horizontalRange());
        assertThrows(IllegalArgumentException.class,
                () -> new SpatialQuery(source, center, -1D, 2D));
        assertThrows(IllegalArgumentException.class,
                () -> new SpatialQuery(source, center, 1D, Double.NaN));
        assertThrows(NullPointerException.class,
                () -> new SpatialQuery(null, center, 1D, 2D));
    }

    private static TargetPredicateFacts facts(boolean airplane, boolean abyssMissile, boolean submarine) {
        return facts(airplane, abyssMissile, submarine, false, false);
    }

    private static TargetPredicateFacts facts(
            boolean airplane,
            boolean abyssMissile,
            boolean submarine,
            boolean friendlyShip,
            boolean hostileShip) {
        return new TargetPredicateFacts(
                true, true, true, false, false, false, false, false, false, false, true,
                airplane, abyssMissile, submarine, friendlyShip, false, hostileShip, false, false, false);
    }

    private static TargetHandle handle(UUID uuid, DimensionKey dimension) {
        return new TargetHandle(uuid, dimension);
    }

    private static ValidatingTargetResolver<RuntimeTarget> resolver(
            java.util.function.Function<UUID, Optional<RuntimeTarget>> lookup) {
        return new ValidatingTargetResolver<>(OVERWORLD, lookup, RuntimeTarget::uuid, RuntimeTarget::valid);
    }

    private record RuntimeTarget(UUID uuid, boolean valid) {
    }
}
