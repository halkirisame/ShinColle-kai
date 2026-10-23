package com.lulan.shincolle.shipdata;

import java.util.Map;

/** Immutable wire data; contains no client classes or server-authoritative combat values. */
public record ShipPresentationSnapshot(long generation, Map<String, ShipPresentation> definitions) {
    public ShipPresentationSnapshot {
        definitions = Map.copyOf(definitions);
    }
}
