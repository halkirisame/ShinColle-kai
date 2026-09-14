package com.lulan.shincolle.network;

import com.lulan.shincolle.client.ClientShipLevelCaps;
import com.lulan.shincolle.entity.ShipLevelCapSummary;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class S2CShipLevelCapPacketWireTest {

    @AfterEach
    void clearClientCache() {
        ClientShipLevelCaps.clear();
    }

    @Test
    void configuredCapsRoundTripAsExactlyEightBytes() {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        new S2CShipLevelCapPacket(new ShipLevelCapSummary(600, 1000)).encode(buf);

        assertEquals(8, buf.readableBytes());
        S2CShipLevelCapPacket decoded = new S2CShipLevelCapPacket(buf);
        assertTrue(decoded.isValid());
        assertEquals(new ShipLevelCapSummary(600, 1000), decoded.summary());
        assertEquals(0, buf.readableBytes());
    }

    @Test
    void nonPositiveTruncatedAndTrailingPayloadsAreRejected() {
        assertFalse(decodeInts(0, 1000).isValid());
        assertFalse(decodeInts(-1, 1000).isValid());
        assertFalse(decodeInts(600, 0).isValid());
        assertFalse(decodeInts(600, -1).isValid());

        FriendlyByteBuf truncated = new FriendlyByteBuf(Unpooled.buffer());
        truncated.writeInt(600);
        assertFalse(new S2CShipLevelCapPacket(truncated).isValid());

        FriendlyByteBuf trailing = new FriendlyByteBuf(Unpooled.buffer());
        trailing.writeInt(600);
        trailing.writeInt(1000);
        trailing.writeByte(1);
        assertFalse(new S2CShipLevelCapPacket(trailing).isValid());
    }

    @Test
    void invalidPacketDoesNotReplaceTheCurrentClientSummary() {
        ShipLevelCapSummary installed = new ShipLevelCapSummary(600, 1000);
        ClientShipLevelCaps.install(installed);
        S2CShipLevelCapPacket invalid = decodeInts(0, 1000);

        assertFalse(invalid.applyToClient());
        assertSame(installed, ClientShipLevelCaps.current());
        assertEquals(new ShipLevelCapSummary(600, 1000), ClientShipLevelCaps.current());
    }

    private static S2CShipLevelCapPacket decodeInts(int unmarriedCap, int absoluteCap) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeInt(unmarriedCap);
        buf.writeInt(absoluteCap);
        return new S2CShipLevelCapPacket(buf);
    }
}
