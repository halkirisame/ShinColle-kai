package com.lulan.shincolle.client;

import net.minecraftforge.common.ForgeConfigSpec;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ShipInteractionClientConfigTest {
    @Test
    void clientSpecContainsOnlyOneDefaultOffBoolean() {
        var values = ShipInteractionClientConfig.CLIENT_SPEC.getValues();
        assertEquals(1, values.valueMap().size());
        ForgeConfigSpec.BooleanValue swapped = values.get("swapShipClicks");
        assertFalse(swapped.getDefault());
    }
}
