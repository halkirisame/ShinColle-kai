package com.lulan.shincolle.ai.domain;

/** Observer for Stage 1 spatial query and raw-candidate counts. */
@FunctionalInterface
public interface SpatialCandidateProfiler {

    SpatialCandidateProfiler NONE = rawCandidateCount -> {
    };

    /** Called exactly once after cheap structural filtering for each completed world query. */
    void recordQuery(int rawCandidateCount);
}
