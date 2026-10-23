package com.lulan.shincolle.shipdata;

import com.google.gson.JsonParser;
import com.lulan.shincolle.client.shipdata.ClientShipDefinitions;
import com.lulan.shincolle.network.S2CShipDefinitionSyncPacket;
import io.netty.buffer.Unpooled;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShipDefinitionEntityDataTest {
    private static ShipDefinition definition() {
        return new ShipDefinitionValidator(id -> false, id -> false, id -> false).validate(
                ShipDefinitionId.parse("addon:sample"), JsonParser.parseString("""
                {"schema":1,"ship_type":"shincolle_kai:destroyer","visual":"addon:visual",
                 "display_name":"🚢船","base_stats":{"hp":22},"growth":{"hp":0.32},
                 "dimensions":{"width":1.6,"height":4.4},"mount_offset":2,"equip_type":2}
                """), warning -> { });
    }

    @Test
    void dormantNbtIsDeepCopiedAndReSavedWithoutAddingOrRemovingAnyFields() {
        CompoundTag input = new CompoundTag();
        input.putString("ShipId", "missing:ship");
        input.putIntArray("StateMinor", new int[]{3, 4, 5});
        CompoundTag unknown = new CompoundTag();
        unknown.putString("value", "keep");
        input.put("UnknownAddonData", unknown);
        GenericShipState state = new GenericShipState();
        state.loaded(input);
        CompoundTag expected = input.copy();
        input.getCompound("UnknownAddonData").putString("value", "changed externally");
        for (int i = 0; i < 3; i++) {
            CompoundTag output = new CompoundTag();
            output.putBoolean("UnwantedNewField", true);
            assertTrue(state.savePreserved(output));
            assertEquals(expected, output);
            output.getCompound("UnknownAddonData").putString("value", "changed output");
        }
        assertTrue(state.dormant());
        assertEquals("missing:ship", state.shipId());
    }

    @Test
    void dormantSaveRetainsEntityTypeWrittenByVanillaSaveWrapper() {
        CompoundTag input = new CompoundTag();
        input.putString("ShipId", "missing:ship");
        input.putString("UnknownAddonData", "keep");
        GenericShipState state = new GenericShipState();
        state.loaded(input);
        CompoundTag worldSave = new CompoundTag();
        worldSave.putString("id", "shincolle_kai:generic_ship");
        worldSave.putFloat("Health", 999);
        assertTrue(state.savePreserved(worldSave));
        CompoundTag expected = input.copy();
        expected.putString("id", "shincolle_kai:generic_ship");
        assertEquals(expected, worldSave);
        assertFalse(input.contains("id"));
    }

    @Test
    void presentationPacketRoundTripsAtomicallyAndGenerationMismatchUsesPlaceholder() {
        ClientShipDefinitions.initialize();
        ShipDefinition definition = definition();
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        ShipDefinitionSnapshot serverBefore = ShipDefinitionRegistry.server().snapshot();
        try {
            new S2CShipDefinitionSyncPacket(new ShipDefinitionSnapshot(7, Map.of(definition.id(), definition))).encode(buf);
            S2CShipDefinitionSyncPacket decoded = new S2CShipDefinitionSyncPacket(buf);
            assertNotNull(decoded.snapshot());
            assertEquals(7, decoded.snapshot().generation());
            assertEquals(ShipPresentation.of(definition), decoded.snapshot().definitions().get("addon:sample"));
            assertTrue(decoded.applyToClient());
            assertNotNull(ClientShipDefinitions.get("addon:sample", 7));
            assertNull(ClientShipDefinitions.get("addon:sample", 6));
            assertNull(ClientShipDefinitions.get("missing:ship", 7));
            assertEquals(serverBefore, ShipDefinitionRegistry.server().snapshot());
        } finally {
            ClientShipDefinitions.clear();
            buf.release();
        }
        assertNull(ClientShipDefinitions.get("addon:sample", 7));
    }

    @Test
    void malformedSnapshotsNeverReplaceCurrentClientData() {
        ClientShipDefinitions.initialize();
        ShipDefinition definition = definition();
        var before = new ShipPresentationSnapshot(9, Map.of("addon:sample", ShipPresentation.of(definition)));
        ClientShipDefinitions.install(before);
        try {
            for (int kind = 0; kind < 4; kind++) {
                FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
                try {
                    if (kind == 0) {
                        buf.writeVarLong(-1);
                        buf.writeVarInt(0);
                    } else if (kind == 1) {
                        buf.writeVarLong(10);
                        buf.writeVarInt(Integer.MAX_VALUE);
                    } else if (kind == 2) {
                        new S2CShipDefinitionSyncPacket(new ShipDefinitionSnapshot(10,
                                Map.of(definition.id(), definition))).encode(buf);
                        buf.writeByte(99);
                    } else {
                        buf.writeVarLong(10);
                        buf.writeVarInt(1);
                    }
                    S2CShipDefinitionSyncPacket decoded = new S2CShipDefinitionSyncPacket(buf);
                    assertNull(decoded.snapshot());
                    assertFalse(decoded.applyToClient());
                    assertEquals(before.definitions().get("addon:sample"), ClientShipDefinitions.get("addon:sample", 9));
                } finally {
                    buf.release();
                }
            }
        } finally {
            ClientShipDefinitions.clear();
        }
    }
}
