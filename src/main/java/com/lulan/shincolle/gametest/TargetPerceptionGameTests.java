package com.lulan.shincolle.gametest;

import com.lulan.shincolle.ai.TargetShadowComparison;
import com.lulan.shincolle.ai.TargetShadowComparison.Outcome;
import com.lulan.shincolle.ai.domain.AiDecisionId;
import com.lulan.shincolle.ai.domain.AiTickContext;
import com.lulan.shincolle.ai.domain.PerceptionFreshnessPolicy;
import com.lulan.shincolle.ai.domain.RawEntityObservation;
import com.lulan.shincolle.ai.domain.ShipAiCompatibilityRules;
import com.lulan.shincolle.ai.domain.ShipTargetPerceptionSnapshot;
import com.lulan.shincolle.ai.domain.SpatialQuery;
import com.lulan.shincolle.ai.domain.TargetClassificationObservation;
import com.lulan.shincolle.ai.domain.TargetObservationProfiler;
import com.lulan.shincolle.ai.domain.TargetPredicateKind;
import com.lulan.shincolle.ai.domain.TargetPredicatePolicy;
import com.lulan.shincolle.ai.observation.MinecraftEntityObservationAdapter;
import com.lulan.shincolle.ai.observation.MinecraftTargetClassificationAdapter;
import com.lulan.shincolle.ai.observation.MinecraftTargetPerceptionProducer;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.entity.BasicEntityShipHostile;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;

import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Entity.RemovalReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TargetPerceptionGameTests {
    private TargetPerceptionGameTests() {
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "target_perception")
    public static void currentAndNearbyUseOneClassificationObject(GameTestHelper helper) {
        try (GameTestEntities entities = GameTestEntities.open(helper)) {
            BasicEntityShip source = friendly(helper, entities);
            BasicEntityShipHostile target = hostile(helper, entities, 2D);
            RawEntityObservation sourceRaw = MinecraftEntityObservationAdapter.observe(source);
            SpatialQuery query = query(source, sourceRaw, 16);
            CountingProfiler profiler = new CountingProfiler();

            ShipTargetPerceptionSnapshot snapshot = MinecraftTargetPerceptionProducer.captureNow(
                    source, target, query, context(source.tickCount),
                    TargetPredicateKind.FRIENDLY_AUTOMATIC, TargetPredicatePolicy.neutral(),
                    ignored -> List.of(MinecraftEntityObservationAdapter.observe(target)),
                    handle -> handle.uuid().equals(target.getUUID())
                            ? java.util.Optional.of(target) : java.util.Optional.empty(),
                    PerceptionFreshnessPolicy.AUTOMATIC_TARGETING, profiler);

            RawEntityObservation current = snapshot.currentTarget().observation().value().orElseThrow();
            RawEntityObservation nearby = snapshot.nearbyEntities().observation().value().stream()
                    .filter(raw -> raw.handle().uuid().equals(target.getUUID()))
                    .findFirst().orElseThrow();
            TargetClassificationObservation currentClassification =
                    snapshot.classifications().get(current.handle());
            helper.assertTrue(current.handle().equals(nearby.handle()),
                    "Current and nearby handles differ for the same entity");
            helper.assertTrue(currentClassification == snapshot.classifications().get(nearby.handle()),
                    "Current and nearby did not share one classification datum");
            helper.assertTrue(profiler.classifications.get() == 1,
                    "The common current/nearby handle was classified more than once");
            helper.succeed();
        }
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "target_perception")
    public static void cheapPrerequisitesGateLineOfSightAndRelations(GameTestHelper helper) {
        ServerPlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(
                UUID.fromString("00000000-0000-0000-0000-000000000175"),
                "shincolle_perception"));
        helper.getLevel().addNewPlayer(player);
        try (GameTestEntities entities = GameTestEntities.open(helper)) {
            BasicEntityShip source = friendly(helper, entities);
            source.setStateFlag(ID.F.OnSightChase, true);
            BasicEntityShipHostile eligible = hostile(helper, entities, 2D);
            BasicEntityShipHostile invisible = hostile(helper, entities, 4D);
            invisible.setInvisible(true);
            Arrow invulnerable = entities.add(EntityType.ARROW.create(helper.getLevel()));
            helper.assertTrue(invulnerable != null, "Could not create invulnerable projectile fixture");
            invulnerable.moveTo(position(helper).add(6D, 0D, 0D));
            helper.assertTrue(helper.getLevel().addFreshEntity(invulnerable),
                    "Could not add invulnerable projectile fixture");
            player.moveTo(position(helper).add(8D, 0D, 0D));
            player.getAbilities().invulnerable = false;
            CountingProfiler profiler = new CountingProfiler();
            TargetPredicatePolicy policy = TargetPredicatePolicy.neutral();

            List<TargetClassificationObservation> results = List.of(
                    classify(source, eligible, policy, profiler),
                    classify(source, player, policy, profiler),
                    classify(source, invulnerable, policy, profiler),
                    classify(source, invisible, policy, profiler));

            helper.assertTrue(results.get(0).lineOfSight().measurementRequired(),
                    "Eligible target did not measure LOS");
            helper.assertTrue(results.subList(1, 4).stream()
                            .noneMatch(result -> result.lineOfSight().measurementRequired()),
                    "Cheaply rejected target measured LOS");
            helper.assertTrue(profiler.classifications.get() == 4,
                    "Classification count did not match target count");
            helper.assertTrue(profiler.lineOfSightQueries.get() == 1,
                    "LOS query count did not stop at cheap prerequisites");
            helper.assertTrue(profiler.relationLookups.get() == 0,
                    "Policy-independent rejected targets performed relation lookups");
            helper.succeed();
        } finally {
            helper.getLevel().removePlayerImmediately(player, RemovalReason.DISCARDED);
        }
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "target_perception")
    public static void shadowUsesCommonPipelineWhenOnAndDoesNothingWhenOff(GameTestHelper helper) {
        TargetShadowComparison.setEnabled(true);
        TargetShadowComparison.resetCounters();
        try (GameTestEntities entities = GameTestEntities.open(helper)) {
            BasicEntityShip source = friendly(helper, entities);
            BasicEntityShipHostile target = hostile(helper, entities, 2D);

            TargetShadowComparison.compare(source, target, 16, 0, false, null);

            TargetShadowComparison.Metrics on = TargetShadowComparison.metrics();
            helper.assertTrue(TargetShadowComparison.count(Outcome.MATCH) == 1,
                    "Common perception shadow no longer matched legacy target");
            helper.assertTrue(on.spatialQueries() == 1 && on.rawCandidates() == 1,
                    "Shadow did not use one common producer query");
            helper.assertTrue(on.classifications() == 1,
                    "Current and nearby duplicate was not classified once");
            helper.assertTrue(source.getTarget() == null,
                    "Diagnostic perception changed target authority");

            TargetShadowComparison.setEnabled(false);
            TargetShadowComparison.resetCounters();
            TargetShadowComparison.compare(source, target, 16, 0, false, null);
            helper.assertTrue(TargetShadowComparison.metrics().equals(
                            new TargetShadowComparison.Metrics(0, 0, 0, 0, 0, 0, 0, 0)),
                    "Disabled shadow performed observation work");
            helper.succeed();
        } finally {
            TargetShadowComparison.setEnabled(false);
            TargetShadowComparison.resetCounters();
        }
    }

    private static TargetClassificationObservation classify(
            Entity source,
            Entity target,
            TargetPredicatePolicy policy,
            TargetObservationProfiler profiler) {
        return MinecraftTargetClassificationAdapter.classify(
                source, target, TargetPredicateKind.FRIENDLY_AUTOMATIC,
                policy, source.tickCount, profiler);
    }

    private static BasicEntityShip friendly(GameTestHelper helper, GameTestEntities entities) {
        BasicEntityShip ship = entities.add(ModEntities.BB_KONGOU.get().create(helper.getLevel()));
        helper.assertTrue(ship != null, "Could not create friendly perception source");
        ship.setNoAi(true);
        ship.setPlayerUID(175);
        ship.setEntitySit(false);
        ship.setStateFlag(ID.F.OnSightChase, false);
        ship.setStateFlag(ID.F.AntiAir, false);
        ship.setStateFlag(ID.F.AntiSS, false);
        ship.setStateFlag(ID.F.PVPFirst, false);
        ship.tickCount = 100;
        ship.moveTo(position(helper));
        helper.assertTrue(helper.getLevel().addFreshEntity(ship),
                "Could not add friendly perception source");
        return ship;
    }

    private static BasicEntityShipHostile hostile(
            GameTestHelper helper, GameTestEntities entities, double xOffset) {
        BasicEntityShipHostile ship = entities.add(
                ModEntities.BB_KIRISHIMA_MOB.get().create(helper.getLevel()));
        helper.assertTrue(ship != null, "Could not create hostile perception target");
        ship.setNoAi(true);
        ship.moveTo(position(helper).add(xOffset, 0D, 0D));
        helper.assertTrue(helper.getLevel().addFreshEntity(ship),
                "Could not add hostile perception target");
        return ship;
    }

    private static SpatialQuery query(
            BasicEntityShip source, RawEntityObservation sourceRaw, int range) {
        return new SpatialQuery(sourceRaw.handle(), sourceRaw.position(),
                MinecraftEntityObservationAdapter.boundsOf(source), range,
                ShipAiCompatibilityRules.targetSearchVerticalInflation(range));
    }

    private static Vec3 position(GameTestHelper helper) {
        return helper.absoluteVec(new Vec3(0.5D, 20D, 0.5D));
    }

    private static AiTickContext context(long tick) {
        return new AiTickContext(tick, new AiDecisionId(tick, 0));
    }

    private static final class CountingProfiler implements TargetObservationProfiler {
        private final AtomicInteger classifications = new AtomicInteger();
        private final AtomicInteger relationLookups = new AtomicInteger();
        private final AtomicInteger lineOfSightQueries = new AtomicInteger();

        @Override
        public void recordClassification() {
            this.classifications.incrementAndGet();
        }

        @Override
        public void recordRelationLookup() {
            this.relationLookups.incrementAndGet();
        }

        @Override
        public void recordLineOfSightQuery() {
            this.lineOfSightQueries.incrementAndGet();
        }
    }
}
