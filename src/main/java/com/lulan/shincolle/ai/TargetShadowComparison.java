package com.lulan.shincolle.ai;

import com.lulan.shincolle.ai.domain.AiDecisionId;
import com.lulan.shincolle.ai.domain.AiTickContext;
import com.lulan.shincolle.ai.domain.PerceptionFreshnessPolicy;
import com.lulan.shincolle.ai.domain.RawEntityObservation;
import com.lulan.shincolle.ai.domain.ShipTargetPerceptionSnapshot;
import com.lulan.shincolle.ai.domain.ShipAiCompatibilityRules;
import com.lulan.shincolle.ai.domain.SpatialQuery;
import com.lulan.shincolle.ai.domain.TargetCandidate;
import com.lulan.shincolle.ai.domain.TargetCandidateSelector;
import com.lulan.shincolle.ai.domain.TargetHandle;
import com.lulan.shincolle.ai.domain.TargetObservationProfiler;
import com.lulan.shincolle.ai.domain.TargetPredicateKind;
import com.lulan.shincolle.ai.domain.TargetPredicatePolicy;
import com.lulan.shincolle.ai.domain.TargetState;
import com.lulan.shincolle.ai.observation.MinecraftEntityObservationAdapter;
import com.lulan.shincolle.ai.observation.MinecraftSpatialCandidateProvider;
import com.lulan.shincolle.ai.observation.MinecraftTargetPerceptionProducer;
import com.lulan.shincolle.ai.observation.MinecraftTargetResolver;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.entity.BasicEntityShipHostile;
import com.lulan.shincolle.handler.ConfigHandler;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;
import com.lulan.shincolle.utility.DebugProfiler;
import com.lulan.shincolle.utility.LogHelper;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** Diagnostic-only comparison. Never changes a target, consumes a world draw, or owns combat authority. */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public final class TargetShadowComparison {
    public enum Outcome {
        MATCH, DIFF_NON_LIVING, DIFF_TIE_BREAK, DRAW_NOT_TAKEN, UNKNOWN
    }

    private static final String PREFIX = "shincolle.ai.range_target.shadow.";
    private static boolean enabled;
    private static final long[] COUNTS = new long[Outcome.values().length];
    private static long comparisons;
    private static long spatialQueries;
    private static long rawCandidates;
    private static long selections;
    private static long classifications;
    private static long relationLookups;
    private static long lineOfSightQueries;
    private static long errors;
    /** Reflection-only fault injection for isolated synchronous GameTests; null in production. */
    private static Runnable testFailure;

    private TargetShadowComparison() {
    }

    public static void setEnabled(boolean value) {
        enabled = value;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static long count(Outcome outcome) {
        return COUNTS[outcome.ordinal()];
    }

    public static Metrics metrics() {
        return new Metrics(comparisons, spatialQueries, rawCandidates, selections,
                classifications, relationLookups, lineOfSightQueries, errors);
    }

    public static void resetCounters() {
        Arrays.fill(COUNTS, 0L);
        comparisons = spatialQueries = rawCandidates = selections = classifications = 0L;
        relationLookups = lineOfSightQueries = errors = 0L;
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        enabled = false;
        testFailure = null;
        resetCounters();
    }

    /** Called once per completed legacy scan, including the no-target result. */
    public static void compare(Mob source, Entity legacyTarget, int range, int drawnIndex,
                               boolean legacyDrew, ProfilerFiller profiler) {
        if (!enabled) {
            return;
        }
        try {
            comparisons++;
            if (testFailure != null) {
                testFailure.run();
            }
            runComparison(source, legacyTarget, range, drawnIndex, legacyDrew, profiler);
        } catch (Throwable failure) {
            errors++;
            // Even broken diagnostics must not escape into legacy selection.
            try {
                DebugProfiler.count(profiler, PREFIX + "error");
                LogHelper.diag("DIAG: target shadow error source=" + source + " failure=" + failure);
            } catch (Throwable ignored) {
                // The legacy result remains authoritative.
            }
        }
    }

    private static void runComparison(Mob source, Entity legacyTarget, int range, int drawnIndex,
                                      boolean legacyDrew, ProfilerFiller profiler) {
        if (!(source.level() instanceof ServerLevel level)) {
            throw new IllegalArgumentException("Target shadow requires a server level");
        }
        RawEntityObservation self = MinecraftEntityObservationAdapter.observe(source);
        SpatialQuery query = new SpatialQuery(self.handle(), self.position(),
                MinecraftEntityObservationAdapter.boundsOf(source), range,
                ShipAiCompatibilityRules.targetSearchVerticalInflation(range));
        MinecraftSpatialCandidateProvider provider = new MinecraftSpatialCandidateProvider(level, size -> {
            spatialQueries++;
            rawCandidates += size;
            DebugProfiler.count(profiler, PREFIX + "spatial_query");
            if (profiler != null) {
                profiler.incrementCounter(PREFIX + "raw_candidates", size);
            }
        });
        MinecraftTargetResolver resolver = new MinecraftTargetResolver(level);
        TargetPredicateKind kind = source instanceof BasicEntityShipHostile
                ? TargetPredicateKind.HOSTILE_AUTOMATIC : TargetPredicateKind.FRIENDLY_AUTOMATIC;
        BasicEntityShip ship = source instanceof BasicEntityShip friendly ? friendly : null;
        TargetPredicatePolicy policy = new TargetPredicatePolicy(ship != null && ship.getStateFlag(ID.F.PVPFirst),
                ship != null && ship.getStateFlag(ID.F.AntiAir), ship != null && ship.getStateFlag(ID.F.AntiSS),
                ConfigHandler.shipAttackPlayer(), ConfigHandler.mobShipsAttackPlayer());
        TargetObservationProfiler observationProfiler = new TargetObservationProfiler() {
            @Override
            public void recordClassification() {
                classifications++;
                DebugProfiler.count(profiler, PREFIX + "classification");
            }

            @Override
            public void recordRelationLookup() {
                relationLookups++;
                DebugProfiler.count(profiler, PREFIX + "relation_lookup");
            }

            @Override
            public void recordLineOfSightQuery() {
                lineOfSightQueries++;
                DebugProfiler.count(profiler, PREFIX + "line_of_sight_query");
            }
        };
        AiTickContext context = new AiTickContext(source.tickCount,
                new AiDecisionId(source.tickCount, comparisons));
        ShipTargetPerceptionSnapshot snapshot = MinecraftTargetPerceptionProducer.captureNow(
                source, legacyTarget, query, context, kind, policy, provider, resolver,
                PerceptionFreshnessPolicy.AUTOMATIC_TARGETING,
                observationProfiler);
        List<RawEntityObservation> raw = snapshot.nearbyEntities().observation().value();
        List<TargetCandidate> candidates = new ArrayList<>(raw.size());
        for (RawEntityObservation observation : raw) {
            var classified = snapshot.classifications().get(observation.handle());
            if (classified != null) {
                double x = self.position().x() - observation.position().x();
                double y = self.position().y() - observation.position().y();
                double z = self.position().z() - observation.position().z();
                candidates.add(new TargetCandidate(observation.handle(),
                        classified.classification().value(), x * x + y * y + z * z));
            }
        }
        boolean[] shadowDrew = {false};
        TargetState shadow = TargetCandidateSelector.select(source.tickCount, self.handle(), candidates, kind, policy,
                bound -> {
                    shadowDrew[0] = true;
                    if (bound != 3) {
                        throw new IllegalArgumentException("Unexpected shadow random bound: " + bound);
                    }
                    return drawnIndex;
                }, (input, eligible) -> {
                    selections++;
                    DebugProfiler.count(profiler, PREFIX + "selection");
                });
        TargetHandle legacyHandle = legacyTarget == null ? null
                : MinecraftEntityObservationAdapter.observe(legacyTarget).handle();
        TargetHandle shadowHandle = shadow.selectedTarget().orElse(null);
        Entity shadowTarget = shadowHandle == null ? null : resolver.resolve(shadowHandle).orElseThrow();
        Outcome outcome;
        if (Objects.equals(legacyHandle, shadowHandle)) {
            outcome = Outcome.MATCH;
        } else if (shadowTarget != null && !(shadowTarget instanceof LivingEntity)) {
            outcome = Outcome.DIFF_NON_LIVING;
        } else if (legacyTarget != null && shadowTarget != null
                && Double.compare(source.distanceToSqr(legacyTarget), source.distanceToSqr(shadowTarget)) == 0) {
            outcome = Outcome.DIFF_TIE_BREAK;
        } else {
            outcome = Outcome.UNKNOWN;
        }
        record(outcome, profiler);
        boolean missingDraw = shadowDrew[0] && !legacyDrew;
        if (missingDraw) {
            record(Outcome.DRAW_NOT_TAKEN, profiler);
        }
        if ((outcome != Outcome.MATCH || missingDraw) && LogHelper.diagEnabled()) {
            LogHelper.diag("DIAG: target shadow source=" + self + " tick=" + source.tickCount
                    + " kind=" + kind + " policy=" + policy + " bounds=" + query.bounds()
                    + " legacy=" + legacyHandle + " shadow=" + shadowHandle + " outcome=" + outcome
                    + " legacyDrew=" + legacyDrew + " shadowDrew=" + shadowDrew[0] + " draw=" + drawnIndex
                    + " raw=" + raw + " classified=" + candidates + " eligible=" + shadow.orderedEligibleCandidates());
        }
    }

    private static void record(Outcome outcome, ProfilerFiller profiler) {
        COUNTS[outcome.ordinal()]++;
        DebugProfiler.count(profiler, PREFIX + outcome.name());
    }

    public record Metrics(
            long comparisons,
            long spatialQueries,
            long rawCandidates,
            long selections,
            long classifications,
            long relationLookups,
            long lineOfSightQueries,
            long errors) {
    }
}
