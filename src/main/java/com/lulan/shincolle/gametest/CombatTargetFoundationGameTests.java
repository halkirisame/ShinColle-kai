package com.lulan.shincolle.gametest;

import com.lulan.shincolle.ai.domain.ObservationPosition;
import com.lulan.shincolle.ai.domain.RawEntityObservation;
import com.lulan.shincolle.ai.domain.SearchBounds;
import com.lulan.shincolle.ai.domain.SpatialQuery;
import com.lulan.shincolle.ai.domain.TargetPredicateFacts;
import com.lulan.shincolle.ai.domain.TargetTraitClassification;
import com.lulan.shincolle.ai.domain.TargetTraitClassifier;
import com.lulan.shincolle.ai.observation.MinecraftEntityObservationAdapter;
import com.lulan.shincolle.ai.observation.MinecraftSpatialCandidateProvider;
import com.lulan.shincolle.ai.observation.MinecraftTargetResolver;
import com.lulan.shincolle.api.target.TargetTrait;
import com.lulan.shincolle.api.target.TargetTraits;
import com.lulan.shincolle.reference.Reference;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/** Minecraft-boundary coverage for the authority-neutral combat-target foundation. */
@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CombatTargetFoundationGameTests {

    private CombatTargetFoundationGameTests() {
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "isolated_provider_observes_and_resolver_finds_non_living_entity")
    public static void providerObservesAndResolverFindsNonLivingEntity(GameTestHelper helper) {
        try (GameTestEntities entities = GameTestEntities.open(helper)) {
            verifyProviderObservesAndResolverFindsNonLivingEntity(helper, entities);
        }
    }

    private static void verifyProviderObservesAndResolverFindsNonLivingEntity(
            GameTestHelper helper, GameTestEntities entities) {
        ServerLevel level = helper.getLevel();
        Vec3 sourcePosition = helper.absoluteVec(new Vec3(0.5D, 1D, 0.5D));
        Vec3 targetPosition = helper.absoluteVec(new Vec3(1.5D, 1D, 0.5D));
        ArmorStand source = entities.add(EntityType.ARMOR_STAND.create(level));
        if (source == null) {
            helper.fail("Could not create the spatial query source");
            return;
        }
        source.moveTo(sourcePosition.x, sourcePosition.y, sourcePosition.z);
        ItemEntity target = entities.add(new ItemEntity(level, targetPosition.x, targetPosition.y, targetPosition.z,
                new ItemStack(Items.STICK)));
        if (!level.addFreshEntity(source) || !level.addFreshEntity(target)) {
            helper.fail("Could not add the combat target foundation test entities");
            return;
        }

        AtomicInteger queryCount = new AtomicInteger();
        AtomicInteger rawCandidateCount = new AtomicInteger();
        MinecraftSpatialCandidateProvider provider = new MinecraftSpatialCandidateProvider(level, count -> {
            queryCount.incrementAndGet();
            rawCandidateCount.set(count);
        });
        RawEntityObservation sourceObservation = MinecraftEntityObservationAdapter.observe(source);
        SpatialQuery query = new SpatialQuery(
                sourceObservation.handle(),
                new ObservationPosition(sourcePosition.x, sourcePosition.y, sourcePosition.z),
                MinecraftEntityObservationAdapter.boundsOf(source),
                3D,
                2D);

        // The upstream Goal inflates the source's bounding box, not a point. Pin the
        // derived volume against the real Minecraft box so a shadow comparison never
        // reports a difference that is only the query volume disagreeing.
        SearchBounds derived = query.bounds();
        AABB upstream = source.getBoundingBox().inflate(3D, 2D, 3D);
        helper.assertTrue(derived.minX() == upstream.minX && derived.maxX() == upstream.maxX
                        && derived.minY() == upstream.minY && derived.maxY() == upstream.maxY
                        && derived.minZ() == upstream.minZ && derived.maxZ() == upstream.maxZ,
                "Derived search volume did not match getBoundingBox().inflate(...). derived="
                        + derived + " upstream=" + upstream);

        List<RawEntityObservation> observations = provider.query(query);

        helper.assertTrue(observations.stream().anyMatch(observation ->
                        observation.handle().uuid().equals(target.getUUID())),
                "Spatial provider did not observe the non-Living item entity");
        helper.assertTrue(observations.stream().noneMatch(observation ->
                        observation.handle().uuid().equals(source.getUUID())),
                "Spatial provider did not exclude the query source");
        helper.assertTrue(queryCount.get() == 1, "Spatial profiler did not record exactly one query");
        helper.assertTrue(rawCandidateCount.get() == observations.size(),
                "Spatial profiler candidate count did not match observations");

        TargetTraits.Registry addonTraits = TargetTraits.detached();
        addonTraits.registerTargetTrait(EntityType.ITEM, TargetTrait.ANTI_AIR_ELIGIBLE);
        TargetTraitClassification addonClassification = TargetTraitClassifier.classify(
                emptyTargetFacts(), addonTraits.traitsFor(target.getType()));
        helper.assertTrue(addonClassification.traits().equals(Set.of(TargetTrait.ANTI_AIR_ELIGIBLE)),
                "Addon-registered entity type did not receive its target trait");
        addonTraits.freeze();
        try {
            addonTraits.registerTargetTrait(EntityType.ITEM, TargetTrait.SPECIAL);
            helper.fail("Frozen addon target trait registry accepted another registration");
            return;
        } catch (IllegalStateException expected) {
            // Expected: addon registration closes at load complete.
        }

        MinecraftTargetResolver resolver = new MinecraftTargetResolver(level);
        helper.assertTrue(resolver.resolve(observations.stream()
                        .filter(observation -> observation.handle().uuid().equals(target.getUUID()))
                        .findFirst().orElseThrow().handle()).orElse(null) == target,
                "Target resolver did not return the loaded non-Living entity");
        helper.assertTrue(resolver.resolve(MinecraftEntityObservationAdapter.observe(source).handle())
                        .orElse(null) == source,
                "Target resolver did not preserve a separate loaded entity identity");
        helper.succeed();
    }

    private static TargetPredicateFacts emptyTargetFacts() {
        return new TargetPredicateFacts(
                true, true, true, false, false, false, false, false, false, false, true,
                false, false, false, false, false, false, false, false, false, false);
    }
}
