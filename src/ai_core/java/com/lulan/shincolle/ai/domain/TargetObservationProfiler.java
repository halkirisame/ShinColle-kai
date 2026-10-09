package com.lulan.shincolle.ai.domain;

public interface TargetObservationProfiler {
    TargetObservationProfiler NOOP = new TargetObservationProfiler() {
        @Override
        public void recordClassification() {
        }

        @Override
        public void recordRelationLookup() {
        }

        @Override
        public void recordLineOfSightQuery() {
        }
    };

    void recordClassification();

    void recordRelationLookup();

    void recordLineOfSightQuery();
}
