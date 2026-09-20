package com.lulan.shincolle.ai.domain;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Tick-derived shadow result; this is not combat-target authority. */
public record TargetState(
        long observedAtTick,
        TargetHandle source,
        List<TargetCandidate> orderedEligibleCandidates,
        Optional<TargetHandle> selectedTarget) {

    public TargetState {
        if (observedAtTick < 0L) {
            throw new IllegalArgumentException("Observed tick must be non-negative");
        }
        Objects.requireNonNull(source, "source");
        orderedEligibleCandidates = List.copyOf(
                Objects.requireNonNull(orderedEligibleCandidates, "orderedEligibleCandidates"));
        if (orderedEligibleCandidates.stream().anyMatch(candidate ->
                candidate.handle().equals(source)
                        || !candidate.handle().dimension().equals(source.dimension()))) {
            throw new IllegalArgumentException(
                    "Eligible candidates must exclude the source and stay in the source dimension");
        }
        selectedTarget = Objects.requireNonNull(selectedTarget, "selectedTarget");
        TargetHandle selected = selectedTarget.orElse(null);
        if (selected != null
                && orderedEligibleCandidates.stream()
                        .noneMatch(candidate -> candidate.handle().equals(selected))) {
            throw new IllegalArgumentException("Selected target must be an eligible candidate");
        }
    }

    public static TargetState empty(long observedAtTick, TargetHandle source) {
        return new TargetState(observedAtTick, source, List.of(), Optional.empty());
    }

    public Optional<TargetCandidate> selectedCandidate() {
        return selectedTarget.flatMap(selected -> orderedEligibleCandidates.stream()
                .filter(candidate -> candidate.handle().equals(selected))
                .findFirst());
    }
}
