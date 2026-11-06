package com.lulan.shincolle.client.shipdata;

import com.lulan.shincolle.shipdata.ShipDefinition;
import com.lulan.shincolle.shipdata.ShipPresentation;
import com.lulan.shincolle.shipdata.ShipPresentationClientAccess;
import com.lulan.shincolle.shipdata.ShipPresentationSnapshot;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientShipDefinitionsCreativeTabTest {
    @BeforeAll
    static void bootstrapMinecraft() throws ReflectiveOperationException {
        net.minecraft.SharedConstants.tryDetectVersion();
        var bootstrapped = net.minecraft.server.Bootstrap.class.getDeclaredField("isBootstrapped");
        bootstrapped.setAccessible(true);
        if (bootstrapped.getBoolean(null)) return;
        bootstrapped.setBoolean(null, true);
        net.minecraft.core.registries.BuiltInRegistries.bootStrap();
    }

    @Test
    void loginReloadAndDisconnectInvalidateCreativeCacheAndReplaceVisibleIds() {
        var previous = CreativeModeTabs.CACHED_PARAMETERS;
        ClientShipDefinitions.initialize();
        try {
            ClientShipDefinitions.clear();
            assertTrue(ShipPresentationClientAccess.snapshot().definitions().isEmpty());
            var view = new ShipPresentation("Sample", ResourceLocation.parse("addon:visual"),
                    new ShipDefinition.Dimensions(1, 2), ResourceLocation.parse("shincolle_kai:destroyer"), 0, 0);
            var login = new ShipPresentationSnapshot(1, Map.of("addon:alpha", view, "addon:beta", view));
            var cached = new CreativeModeTab.ItemDisplayParameters(FeatureFlags.DEFAULT_FLAGS, false, null);
            CreativeModeTabs.CACHED_PARAMETERS = cached;
            assertSame(cached, CreativeModeTabs.CACHED_PARAMETERS);
            assertTrue(ShipPresentationClientAccess.install(login));
            assertSame(login, ShipPresentationClientAccess.snapshot());
            assertNull(CreativeModeTabs.CACHED_PARAMETERS, "Login must force the next screen rebuild");

            CreativeModeTabs.CACHED_PARAMETERS = cached;
            var reload = new ShipPresentationSnapshot(2, Map.of("addon:gamma", view));
            assertTrue(ShipPresentationClientAccess.install(reload));
            assertEquals(java.util.Set.of("addon:gamma"), ShipPresentationClientAccess.snapshot().definitions().keySet());
            assertNull(CreativeModeTabs.CACHED_PARAMETERS, "Reload must remove stale tab entries");

            CreativeModeTabs.CACHED_PARAMETERS = cached;
            ClientShipDefinitions.clear();
            assertTrue(ShipPresentationClientAccess.snapshot().definitions().isEmpty());
            assertNull(CreativeModeTabs.CACHED_PARAMETERS, "Disconnect must invalidate the previous world's eggs");
        } finally {
            ClientShipDefinitions.clear();
            ShipPresentationClientAccess.bind(null);
            CreativeModeTabs.CACHED_PARAMETERS = previous;
        }
    }

    @Test
    void missingClientEndpointHasNoDefinitions() {
        ShipPresentationClientAccess.bind(null);
        assertTrue(ShipPresentationClientAccess.snapshot().definitions().isEmpty());
    }
}
