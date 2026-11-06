package com.lulan.shincolle.client.shipdata;

import com.lulan.shincolle.shipdata.ShipPresentation;
import com.lulan.shincolle.shipdata.ShipPresentationClientAccess;
import com.lulan.shincolle.shipdata.ShipPresentationSnapshot;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.Map;

/** Separate from the server snapshot, including on an integrated server. */
@OnlyIn(Dist.CLIENT)
public final class ClientShipDefinitions {
    private static volatile ShipPresentationSnapshot snapshot = new ShipPresentationSnapshot(-1, Map.of());

    private ClientShipDefinitions() {
    }

    public static void initialize() {
        ShipPresentationClientAccess.bind(new ShipPresentationClientAccess.Endpoint() {
            @Override
            public void install(ShipPresentationSnapshot next) {
                ClientShipDefinitions.install(next);
            }

            @Override
            public ShipPresentation get(String id, long generation) {
                return ClientShipDefinitions.get(id, generation);
            }

            @Override
            public ShipPresentationSnapshot snapshot() {
                return snapshot;
            }
        });
    }

    public static void install(ShipPresentationSnapshot next) {
        snapshot = next;
        // The creative screen rebuilds and refreshes its selected tab on its next tick.
        CreativeModeTabs.CACHED_PARAMETERS = null;
    }

    public static void clear() {
        install(new ShipPresentationSnapshot(-1, Map.of()));
    }

    public static ShipPresentation get(String id, long generation) {
        ShipPresentationSnapshot current = snapshot;
        return generation == current.generation() ? current.definitions().get(id) : null;
    }
}
