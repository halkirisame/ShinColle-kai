package com.lulan.shincolle.shipdata;

import org.junit.jupiter.api.Test;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Inspect class files without loading their client dependencies on a dedicated server. */
class ShipDefinitionSideBoundaryTest {
    @Test
    void packetHasNoClientPackageDependencies() throws IOException {
        assertCommonClass("network/S2CShipDefinitionSyncPacket");
    }

    @Test
    void entityStateHasNoClientPackageDependencies() throws IOException {
        assertCommonClass("shipdata/GenericShipState");
    }

    @Test
    void sharedPresentationEndpointAndDataDoNotLoadClientTypes() throws IOException {
        for (String name : List.of("shipdata/ShipPresentationSnapshot", "shipdata/ShipPresentationClientAccess",
                "shipdata/ShipPresentationClientAccess$Endpoint", "entity/GenericShipEntity", "entity/GenericHostileShipEntity",
                "network/S2CShipVisualEventPacket", "visualdata/ShipVisualEvent", "visualdata/ShipVisualDefinition",
                "visualdata/ShipVisualPosture", "visualdata/ShipVisualPosture$Input",
                "visualdata/ShipVisualValidator", "visualdata/ShipVisualPresentation", "visualdata/ShipVisualPresentation$Expression",
                "visualdata/ShipVisualPresentation$Visibility", "visualdata/ShipVisualRenderMath", "visualdata/ShipVisualPolicy",
                "visualdata/ShipVisualDefinition$Vector", "visualdata/ShipVisualDefinition$HeldItem", "visualdata/ShipVisualDefinition$Lod")) {
            assertCommonClass(name);
        }
        ShipPresentationClientAccess.bind(null);
        assertTrue(!ShipPresentationClientAccess.install(new ShipPresentationSnapshot(1, java.util.Map.of())));
        assertEquals(null, ShipPresentationClientAccess.get("addon:ship", 1));
    }

    private static void assertCommonClass(String name) throws IOException {
        String resource = "/com/lulan/shincolle/" + name + ".class";
        List<String> forbidden = new ArrayList<>();
        try (var stream = ShipDefinitionSideBoundaryTest.class.getResourceAsStream(resource)) {
            assertNotNull(stream, resource);
            DataInputStream input = new DataInputStream(stream);
            assertEquals(0xCAFEBABE, input.readInt());
            input.readInt(); // minor/major version
            int count = input.readUnsignedShort();
            for (int index = 1; index < count; index++) {
                int tag = input.readUnsignedByte();
                switch (tag) {
                    case 1 -> {
                        String value = input.readUTF();
                        if (value.contains("com/lulan/shincolle/client/") || value.contains("net/minecraft/client/")
                                || value.contains("software/bernie/geckolib/renderer/") || value.contains("software/bernie/geckolib/model/")
                                || value.contains("software/bernie/geckolib/loading/") || value.contains("com/mojang/blaze3d/")) {
                            forbidden.add(value);
                        }
                    }
                    case 3, 4, 9, 10, 11, 12, 17, 18 -> input.skipNBytes(4);
                    case 5, 6 -> {
                        input.skipNBytes(8);
                        index++;
                    }
                    case 7, 8, 16, 19, 20 -> input.skipNBytes(2);
                    case 15 -> input.skipNBytes(3);
                    default -> throw new IOException("Unknown constant pool tag " + tag);
                }
            }
        }
        assertTrue(forbidden.isEmpty(), name + " depends on client types: " + forbidden);
    }
}
