package com.lulan.shincolle.ai.observation;

import com.lulan.shincolle.ai.domain.AiTickContext;
import com.lulan.shincolle.ai.domain.CurrentTargetRawObservation;
import com.lulan.shincolle.ai.domain.NearbyEntitiesObservation;
import com.lulan.shincolle.ai.domain.PerceptionFreshnessPolicy;
import com.lulan.shincolle.ai.domain.RawEntityObservation;
import com.lulan.shincolle.ai.domain.ShipTargetPerceptionSnapshot;
import com.lulan.shincolle.ai.domain.SpatialCandidateProvider;
import com.lulan.shincolle.ai.domain.SpatialQuery;
import com.lulan.shincolle.ai.domain.TargetClassificationObservation;
import com.lulan.shincolle.ai.domain.TargetHandle;
import com.lulan.shincolle.ai.domain.TargetObservationProfiler;
import com.lulan.shincolle.ai.domain.TargetPredicateKind;
import com.lulan.shincolle.ai.domain.TargetPredicatePolicy;
import com.lulan.shincolle.ai.domain.TargetResolver;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Builds immutable target-perception snapshots without owning target authority. */
public final class MinecraftTargetPerceptionProducer {
    private MinecraftTargetPerceptionProducer() {
    }

    public static ShipTargetPerceptionSnapshot captureNow(
            Entity source,
            Entity currentTarget,
            SpatialQuery query,
            AiTickContext context,
            TargetPredicateKind kind,
            TargetPredicatePolicy policy,
            SpatialCandidateProvider provider,
            TargetResolver<Entity> resolver,
            PerceptionFreshnessPolicy freshnessPolicy,
            TargetObservationProfiler profiler) {
        Objects.requireNonNull(provider, "provider");
        NearbyEntitiesObservation nearby = NearbyEntitiesObservation.observed(
                provider.query(query), context.tick());
        return captureObserved(source, currentTarget, context, kind, policy,
                nearby, resolver, freshnessPolicy, profiler);
    }

    public static Optional<ShipTargetPerceptionSnapshot> captureScheduled(
            Entity source,
            Entity currentTarget,
            SpatialQuery query,
            AiTickContext context,
            TargetPredicateKind kind,
            TargetPredicatePolicy policy,
            NearbyObservationProducer nearbyProducer,
            TargetResolver<Entity> resolver,
            PerceptionFreshnessPolicy freshnessPolicy,
            TargetObservationProfiler profiler) {
        Objects.requireNonNull(nearbyProducer, "nearbyProducer");
        return nearbyProducer.observe(query, context).map(nearby ->
                captureObserved(source, currentTarget, context, kind, policy,
                        nearby, resolver, freshnessPolicy, profiler));
    }

    private static ShipTargetPerceptionSnapshot captureObserved(
            Entity source,
            Entity currentTarget,
            AiTickContext context,
            TargetPredicateKind kind,
            TargetPredicatePolicy policy,
            NearbyEntitiesObservation nearby,
            TargetResolver<Entity> resolver,
            PerceptionFreshnessPolicy freshnessPolicy,
            TargetObservationProfiler profiler) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(policy, "policy");
        Objects.requireNonNull(nearby, "nearby");
        Objects.requireNonNull(resolver, "resolver");
        Objects.requireNonNull(freshnessPolicy, "freshnessPolicy");
        Objects.requireNonNull(profiler, "profiler");
        if (!(source.level() instanceof ServerLevel)) {
            throw new IllegalArgumentException("Target perception requires a server level");
        }
        if (!nearby.isFreshAt(context.tick(), freshnessPolicy.maxCandidateAge())) {
            throw new IllegalArgumentException("Nearby target observation is stale");
        }

        CurrentTargetRawObservation current =
                MinecraftEntityObservationAdapter.observeCurrent(currentTarget, context.tick());
        if (!current.isFreshAt(context.tick(), freshnessPolicy.maxCurrentTargetAge())) {
            throw new IllegalArgumentException("Current target observation is stale");
        }
        Set<TargetHandle> orderedHandles = new LinkedHashSet<>();
        current.observation().value().ifPresent(raw -> orderedHandles.add(raw.handle()));
        for (RawEntityObservation raw : nearby.observation().value()) {
            orderedHandles.add(raw.handle());
        }

        Map<TargetHandle, TargetClassificationObservation> classifications =
                new LinkedHashMap<>();
        for (TargetHandle handle : orderedHandles) {
            resolver.resolve(handle).ifPresent(target -> {
                TargetClassificationObservation classified =
                        MinecraftTargetClassificationAdapter.classify(
                                source, target, kind, policy, context.tick(), profiler);
                if (!handle.equals(classified.target())) {
                    throw new IllegalArgumentException(
                            "Resolved entity does not match requested target handle");
                }
                classifications.put(handle, classified);
            });
        }
        return new ShipTargetPerceptionSnapshot(
                context, current, nearby, classifications);
    }
}
