package com.lulan.shincolle.client;

import com.lulan.shincolle.entity.ShipLevelCapSummary;

import java.util.Objects;

/** Client-side display cache for the ship level caps sent by the current server. */
public final class ClientShipLevelCaps {

    private static volatile ShipLevelCapSummary current = ShipLevelCapSummary.DEFAULT;

    private ClientShipLevelCaps() {
    }

    public static ShipLevelCapSummary current() {
        return current;
    }

    public static void install(ShipLevelCapSummary summary) {
        current = Objects.requireNonNull(summary, "summary");
    }

    public static void clear() {
        current = ShipLevelCapSummary.DEFAULT;
    }
}
