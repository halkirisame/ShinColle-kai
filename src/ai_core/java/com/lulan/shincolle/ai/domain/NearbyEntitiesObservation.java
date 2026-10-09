package com.lulan.shincolle.ai.domain;

import java.util.List;
import java.util.Objects;

public record NearbyEntitiesObservation(
        TimedObservation<List<RawEntityObservation>> observation) {
    public NearbyEntitiesObservation {
        Objects.requireNonNull(observation, "observation");
        observation = new TimedObservation<>(
                List.copyOf(observation.value()), observation.observedAtTick());
    }

    public static NearbyEntitiesObservation observed(
            List<RawEntityObservation> entities, long tick) {
        return new NearbyEntitiesObservation(new TimedObservation<>(entities, tick));
    }

    public long ageAt(long currentTick) {
        return this.observation.ageAt(currentTick);
    }

    public boolean isFreshAt(long currentTick, long maxAge) {
        return this.observation.isFreshAt(currentTick, maxAge);
    }
}
