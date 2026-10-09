package com.lulan.shincolle.ai.domain;

import java.util.List;

/** Boundary around world spatial queries for combat-target observations. */
@FunctionalInterface
public interface SpatialCandidateProvider {

    List<RawEntityObservation> query(SpatialQuery query);
}
