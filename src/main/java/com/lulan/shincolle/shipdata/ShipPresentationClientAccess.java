package com.lulan.shincolle.shipdata;

/** Client-installed endpoint. A dedicated server never loads or installs the implementation. */
public final class ShipPresentationClientAccess {
    private static final ShipPresentationSnapshot EMPTY = new ShipPresentationSnapshot(-1, java.util.Map.of());
    private static volatile Endpoint endpoint;

    private ShipPresentationClientAccess() {
    }

    public interface Endpoint {
        void install(ShipPresentationSnapshot snapshot);

        ShipPresentation get(String id, long generation);

        ShipPresentationSnapshot snapshot();
    }

    public static void bind(Endpoint client) {
        endpoint = client;
    }

    public static boolean install(ShipPresentationSnapshot snapshot) {
        Endpoint client = endpoint;
        if (client == null) return false;
        client.install(snapshot);
        return true;
    }

    public static ShipPresentation get(String id, long generation) {
        Endpoint client = endpoint;
        return client == null ? null : client.get(id, generation);
    }

    public static ShipPresentationSnapshot snapshot() {
        Endpoint client = endpoint;
        return client == null ? EMPTY : client.snapshot();
    }
}
