package com.lulan.shincolle.shipdata;

import java.util.Map;

/** Capture this object when several lookups must observe the same reload generation. */
public record ShipDefinitionSnapshot(long generation, Map<ShipDefinitionId, ShipDefinition> all) {
    public static final ShipDefinitionSnapshot EMPTY = new ShipDefinitionSnapshot(0, Map.of());

    public ShipDefinitionSnapshot {
        if (generation < 0) {
            throw new IllegalArgumentException("generation must not be negative");
        }
        all = Map.copyOf(all);
        all.forEach((id, definition) -> {
            if (!id.equals(definition.id())) {
                throw new IllegalArgumentException("snapshot key does not match definition ID");
            }
        });
    }

    /** Returns null when this generation does not contain the ID. */
    public ShipDefinition get(ShipDefinitionId id) {
        return all.get(id);
    }
}
