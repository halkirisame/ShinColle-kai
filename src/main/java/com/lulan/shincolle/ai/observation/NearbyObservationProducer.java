package com.lulan.shincolle.ai.observation;

import com.lulan.shincolle.ai.domain.AiTickContext;
import com.lulan.shincolle.ai.domain.NearbyEntitiesObservation;
import com.lulan.shincolle.ai.domain.PerceptionFreshnessPolicy;
import com.lulan.shincolle.ai.domain.RawEntityObservation;
import com.lulan.shincolle.ai.domain.SpatialCandidateProvider;
import com.lulan.shincolle.ai.domain.SpatialQuery;
import com.lulan.shincolle.ai.domain.TargetHandle;
import com.lulan.shincolle.ai.domain.TargetScanSchedule;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Per-entity scheduled cache for nearby combat-target observations. */
public final class NearbyObservationProducer {
    private final TargetHandle source;
    private final SpatialCandidateProvider provider;
    private final int intervalTicks;
    private final PerceptionFreshnessPolicy freshnessPolicy;
    private TargetScanSchedule schedule;
    private Optional<NearbyEntitiesObservation> cached = Optional.empty();

    public NearbyObservationProducer(
            TargetHandle source,
            SpatialCandidateProvider provider,
            long createdAtTick,
            int intervalTicks,
            PerceptionFreshnessPolicy freshnessPolicy) {
        this.source = Objects.requireNonNull(source, "source");
        this.provider = Objects.requireNonNull(provider, "provider");
        this.intervalTicks = intervalTicks;
        this.freshnessPolicy = Objects.requireNonNull(freshnessPolicy, "freshnessPolicy");
        this.schedule = TargetScanSchedule.initial(source, createdAtTick, intervalTicks);
    }

    public Optional<NearbyEntitiesObservation> observe(
            SpatialQuery query, AiTickContext context) {
        Objects.requireNonNull(query, "query");
        Objects.requireNonNull(context, "context");
        if (!this.source.equals(query.source())) {
            throw new IllegalArgumentException("Spatial query source does not match producer source");
        }
        long tick = context.tick();
        boolean stale = this.cached.isPresent()
                && !this.cached.orElseThrow().isFreshAt(
                tick, this.freshnessPolicy.maxCandidateAge());
        if (this.schedule.isDue(tick) || stale) {
            List<RawEntityObservation> raw = this.provider.query(query);
            this.cached = Optional.of(NearbyEntitiesObservation.observed(raw, tick));
            this.schedule = this.schedule.advanceFrom(tick, this.intervalTicks);
        }
        return this.cached;
    }

    public TargetScanSchedule schedule() {
        return this.schedule;
    }
}
