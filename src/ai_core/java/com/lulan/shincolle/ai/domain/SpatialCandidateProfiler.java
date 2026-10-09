package com.lulan.shincolle.ai.domain;

/** Observer for spatial query and raw-candidate counts. */
@FunctionalInterface
public interface SpatialCandidateProfiler {

    SpatialCandidateProfiler NONE = rawCandidateCount -> {
    };

    /** Called exactly once after cheap structural filtering for each completed world query. */
    void recordQuery(int rawCandidateCount);
}
