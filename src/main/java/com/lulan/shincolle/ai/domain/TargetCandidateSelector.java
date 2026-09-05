package com.lulan.shincolle.ai.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.IntUnaryOperator;

/** Pure Stage 2 eligibility, priority, canonical-ordering, and selection pipeline. */
public final class TargetCandidateSelector {
    private static final int RANDOM_POOL_SIZE = 3;
    private static final Comparator<RankedCandidate> CANONICAL_ORDER = Comparator
            .comparing(RankedCandidate::priority)
            .thenComparingDouble(ranked -> ranked.candidate().distanceSquared())
            .thenComparing(ranked -> ranked.candidate().handle());

    private TargetCandidateSelector() {
    }

    public static TargetState select(
            long observedAtTick,
            TargetHandle source,
            List<TargetCandidate> candidates,
            TargetPredicateKind kind,
            TargetPredicatePolicy policy,
            IntUnaryOperator boundedIndexSource) {
        return select(observedAtTick, source, candidates, kind, policy, boundedIndexSource,
                TargetSelectionProfiler.NONE);
    }

    public static TargetState select(
            long observedAtTick,
            TargetHandle source,
            List<TargetCandidate> candidates,
            TargetPredicateKind kind,
            TargetPredicatePolicy policy,
            IntUnaryOperator boundedIndexSource,
            TargetSelectionProfiler profiler) {
        if (observedAtTick < 0L) {
            throw new IllegalArgumentException("Observed tick must be non-negative");
        }
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(candidates, "candidates");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(policy, "policy");
        Objects.requireNonNull(boundedIndexSource, "boundedIndexSource");
        Objects.requireNonNull(profiler, "profiler");

        List<RankedCandidate> ranked = new ArrayList<>();
        for (TargetCandidate candidate : candidates) {
            TargetCandidate checkedCandidate = Objects.requireNonNull(candidate, "candidate");
            if (!checkedCandidate.handle().equals(source)
                    && checkedCandidate.handle().dimension().equals(source.dimension())
                    && TargetEligibilityEvaluator.test(kind, checkedCandidate.observation(), policy)) {
                ranked.add(new RankedCandidate(
                        checkedCandidate,
                        TargetPriorityClassifier.classify(kind, checkedCandidate.observation(), policy)));
            }
        }
        ranked.sort(CANONICAL_ORDER);
        profiler.recordSelection(candidates.size(), ranked.size());
        if (ranked.isEmpty()) {
            return TargetState.empty(observedAtTick, source);
        }

        TargetPriorityTier highestPriority = ranked.get(0).priority();
        int highestTierSize = 0;
        while (highestTierSize < ranked.size()
                && ranked.get(highestTierSize).priority() == highestPriority) {
            highestTierSize++;
        }
        int selectedIndex = 0;
        if (highestTierSize > 2) {
            selectedIndex = boundedIndexSource.applyAsInt(RANDOM_POOL_SIZE);
            if (selectedIndex < 0 || selectedIndex >= RANDOM_POOL_SIZE) {
                throw new IllegalArgumentException("Bounded index source returned an invalid index");
            }
        }

        List<TargetCandidate> orderedCandidates = ranked.stream()
                .map(RankedCandidate::candidate)
                .toList();
        return new TargetState(
                observedAtTick,
                source,
                orderedCandidates,
                Optional.of(ranked.get(selectedIndex).candidate().handle()));
    }

    private record RankedCandidate(TargetCandidate candidate, TargetPriorityTier priority) {
    }
}
