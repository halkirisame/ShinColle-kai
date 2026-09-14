package com.lulan.shincolle.ai.domain;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public record ShipTargetPerceptionSnapshot(
        AiTickContext context,
        CurrentTargetRawObservation currentTarget,
        NearbyEntitiesObservation nearbyEntities,
        Map<TargetHandle, TargetClassificationObservation> classifications) {
    public ShipTargetPerceptionSnapshot {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(currentTarget, "currentTarget");
        Objects.requireNonNull(nearbyEntities, "nearbyEntities");
        Objects.requireNonNull(classifications, "classifications");
        long tick = context.tick();
        if (currentTarget.observation().observedAtTick() != tick) {
            throw new IllegalArgumentException("Current target must be observed at the decision tick");
        }
        rejectFuture(nearbyEntities.observation().observedAtTick(), tick, "Nearby entities");

        Set<TargetHandle> orderedInputs = new LinkedHashSet<>();
        currentTarget.observation().value().ifPresent(raw -> orderedInputs.add(raw.handle()));
        for (RawEntityObservation raw : nearbyEntities.observation().value()) {
            orderedInputs.add(raw.handle());
        }

        for (Map.Entry<TargetHandle, TargetClassificationObservation> entry
                : classifications.entrySet()) {
            TargetHandle key = Objects.requireNonNull(entry.getKey(), "classification key");
            TargetClassificationObservation value =
                    Objects.requireNonNull(entry.getValue(), "classification value");
            if (!key.equals(value.target())) {
                throw new IllegalArgumentException("Classification map key and target must match");
            }
            if (!orderedInputs.contains(key)) {
                throw new IllegalArgumentException("Classification target was not observed as input");
            }
            rejectFuture(value.classification().observedAtTick(), tick, "Classification");
            value.lineOfSight().result().ifPresent(result ->
                    rejectFuture(result.observedAtTick(), tick, "LOS"));
        }

        Map<TargetHandle, TargetClassificationObservation> ordered = new LinkedHashMap<>();
        for (TargetHandle handle : orderedInputs) {
            TargetClassificationObservation value = classifications.get(handle);
            if (value != null) {
                ordered.put(handle, value);
            }
        }
        classifications = Collections.unmodifiableMap(ordered);
    }

    private static void rejectFuture(long observedAtTick, long contextTick, String name) {
        if (observedAtTick > contextTick) {
            throw new IllegalArgumentException(name + " observation must not be from the future");
        }
    }
}
