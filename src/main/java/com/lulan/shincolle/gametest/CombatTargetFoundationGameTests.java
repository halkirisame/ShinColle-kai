package com.lulan.shincolle.gametest;

import com.lulan.shincolle.ai.domain.ObservationPosition;
import com.lulan.shincolle.ai.domain.RawEntityObservation;
import com.lulan.shincolle.ai.domain.SearchBounds;
import com.lulan.shincolle.ai.domain.SpatialQuery;
import com.lulan.shincolle.ai.domain.TargetObservationProfiler;
import com.lulan.shincolle.ai.domain.TargetPredicateFacts;
import com.lulan.shincolle.ai.domain.TargetPredicateKind;
import com.lulan.shincolle.ai.domain.TargetPredicatePolicy;
import com.lulan.shincolle.ai.domain.TargetTraitClassification;
import com.lulan.shincolle.ai.domain.TargetTraitClassifier;
import com.lulan.shincolle.ai.domain.TargetTraitKind;
import com.lulan.shincolle.ai.observation.TargetTraitAssignment;
import com.lulan.shincolle.ai.observation.MinecraftEntityObservationAdapter;
import com.lulan.shincolle.ai.observation.MinecraftSpatialCandidateProvider;
import com.lulan.shincolle.ai.observation.MinecraftTargetClassificationAdapter;
import com.lulan.shincolle.ai.observation.MinecraftTargetResolver;
import com.lulan.shincolle.api.target.CoreTargetTraits;
import com.lulan.shincolle.init.ModTargetTraits;
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
        GameTestEntities.whenPositionsTicking(helper, () -> verifyRegisteredEntities(helper),
                new Vec3(0.5D, 1D, 0.5D), new Vec3(1.5D, 1D, 0.5D));
    }

    private static void verifyRegisteredEntities(GameTestHelper helper) {
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
        GameTestEntities.assertRegistered(helper, source);
        GameTestEntities.assertRegistered(helper, target);

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

        Set<TargetTraitKind> addonKinds = TargetTraitAssignment.assignedKinds(ModTargetTraits.REGISTRY,
                trait -> trait.id().equals(CoreTargetTraits.ANTI_AIR_ELIGIBLE));
        TargetTraitClassification addonClassification = TargetTraitClassifier.classify(
                emptyTargetFacts(), addonKinds);
        helper.assertTrue(addonClassification.traits().equals(Set.of(TargetTraitKind.ANTI_AIR_ELIGIBLE)),
                "Addon-registered entity type did not receive its target trait");
        helper.assertTrue(TargetTraitAssignment.assignedKinds(() -> null, trait -> true).isEmpty(),
                "A target trait registry that does not exist yet must carry no traits");

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

    /**
     * Goes through the production assignment (registry, then entity-type tag) with no override.
     * The tag file lives in the GameTest-only data set. A marker is used because no other test
     * spawns one and no vanilla or ShinColle code treats it as a combat target.
     */
    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "target_trait_tag_assignment")
    public static void targetTraitTagAssignsTraitThroughTheProductionPath(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        for (var entry : ModTargetTraits.REGISTRY.get().getEntries()) {
            helper.assertTrue(entry.getKey().location().equals(entry.getValue().id()),
                    "A built-in target trait is registered under an ID other than its own: " + entry.getKey());
        }
        helper.assertTrue(EntityType.MARKER.is(CoreTargetTraits.tag(CoreTargetTraits.ANTI_AIR_ELIGIBLE)),
                "The GameTest data set did not put the marker into the anti-air trait tag");

        ArmorStand source = EntityType.ARMOR_STAND.create(level);
        helper.assertTrue(source != null, "Could not create the classification source");
        var marker = EntityType.MARKER.create(level);
        helper.assertTrue(marker != null, "Could not create the tagged entity");
        TargetTraitClassification tagged = MinecraftTargetClassificationAdapter.classify(
                source, marker, TargetPredicateKind.FRIENDLY_AUTOMATIC,
                new TargetPredicatePolicy(false, true, true, 0, false), 0L, TargetObservationProfiler.NOOP)
                .classification().value().traits();
        helper.assertTrue(tagged.hasTrait(TargetTraitKind.ANTI_AIR_ELIGIBLE)
                        && !tagged.hasTrait(TargetTraitKind.ANTI_SUBMARINE_ELIGIBLE),
                "The tagged entity type did not receive exactly the anti-air trait: " + tagged.traits());

        var item = EntityType.ITEM.create(level);
        helper.assertTrue(item != null, "Could not create the untagged entity");
        TargetTraitClassification untagged = MinecraftTargetClassificationAdapter.classify(
                source, item, TargetPredicateKind.FRIENDLY_AUTOMATIC,
                new TargetPredicatePolicy(false, true, true, 0, false), 0L, TargetObservationProfiler.NOOP)
                .classification().value().traits();
        helper.assertTrue(untagged.traits().isEmpty(),
                "An entity type outside every trait tag received traits: " + untagged.traits());
        helper.succeed();
    }

    /** The shared assignment function keeps no state between calls and ignores traits without a kind. */
    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "target_trait_assignment_lifecycle")
    public static void targetTraitAssignmentReadsTheRegistryAtEveryCall(GameTestHelper helper) {
        var real = ModTargetTraits.REGISTRY.get();
        java.util.List<net.minecraftforge.registries.IForgeRegistry<
                com.lulan.shincolle.api.experimental.target.TargetTrait>> seen =
                java.util.Arrays.asList(null, real, null, real);
        int[] next = {0};
        Set<TargetTraitKind> every = Set.of(TargetTraitKind.ANTI_AIR_ELIGIBLE, TargetTraitKind.ANTI_SUBMARINE_ELIGIBLE);
        boolean[] expectedPresent = {false, true, false, true};
        for (boolean expected : expectedPresent) {
            Set<TargetTraitKind> kinds = TargetTraitAssignment.assignedKinds(() -> seen.get(next[0]++), trait -> true);
            helper.assertTrue(kinds.equals(expected ? every : Set.of()),
                    "The assignment kept a result between calls or read a stale registry: " + kinds);
        }

        var addonTrait = new com.lulan.shincolle.api.experimental.target.TargetTrait(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("shincolle_test", "wet"));
        var anti = ModTargetTraits.REGISTRY.get().getEntries().iterator().next();
        var mixed = java.util.List.of(
                java.util.Map.entry(net.minecraft.resources.ResourceKey.create(
                        com.lulan.shincolle.api.experimental.target.TargetTraitRegistries.REGISTRY_KEY,
                        addonTrait.id()), addonTrait),
                anti);
        Set<TargetTraitKind> kinds = TargetTraitAssignment.kindsOfEntries(mixed, trait -> true);
        helper.assertTrue(kinds.size() == 1,
                "A trait without a kind must be skipped and a built-in one read: " + kinds);
        helper.succeed();
    }

    private static TargetPredicateFacts emptyTargetFacts() {
        return new TargetPredicateFacts(
                true, true, true, false, false, false, false, false, false, false, true,
                false, false, false, false, false, false, false, false, false, false);
    }
}
