package com.lulan.shincolle.shipdata;

import java.util.Map;
import java.util.function.Supplier;

/** Server authority, published in one reference swap only after candidate construction succeeds. */
public final class ShipDefinitionRegistry {
    private static final ShipDefinitionRegistry SERVER = new ShipDefinitionRegistry();
    private volatile ShipDefinitionSnapshot snapshot = ShipDefinitionSnapshot.EMPTY;

    public static ShipDefinitionRegistry server() {
        return SERVER;
    }

    public ShipDefinitionSnapshot snapshot() {
        return snapshot;
    }

    public ShipDefinition get(ShipDefinitionId id) {
        return snapshot.get(id);
    }

    public Map<ShipDefinitionId, ShipDefinition> all() {
        return snapshot.all();
    }

    public long generation() {
        return snapshot.generation();
    }

    synchronized void reload(Supplier<Map<ShipDefinitionId, ShipDefinition>> prepare) {
        Map<ShipDefinitionId, ShipDefinition> candidate = prepare.get();
        ShipDefinitionSnapshot next = new ShipDefinitionSnapshot(Math.incrementExact(snapshot.generation()), candidate);
        snapshot = next;
    }
}
