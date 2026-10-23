package com.lulan.shincolle.network;

import com.lulan.shincolle.visualdata.ShipVisualEvent;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class S2CShipVisualEventPacketTest {
    @Test
    void onlyExplicitAttackEventsRoundTrip() {
        for (ShipVisualEvent event : new ShipVisualEvent[]{ShipVisualEvent.LIGHT_ATTACK, ShipVisualEvent.HEAVY_ATTACK}) {
            var packet = new S2CShipVisualEventPacket(42, event);
            var buffer = new FriendlyByteBuf(Unpooled.buffer());
            try {
                packet.encode(buffer);
                assertEquals(8, buffer.readableBytes());
                assertEquals(packet, S2CShipVisualEventPacket.decode(buffer));
                assertEquals(0, buffer.readableBytes());
            } finally {
                buffer.release();
            }
        }
    }

    @Test
    void invalidPayloadsAreConsumedAndNeverAccepted() {
        for (int length : new int[]{0, 1, 4, 7, 9, 12}) {
            var buffer = new FriendlyByteBuf(Unpooled.buffer());
            try {
                buffer.writeZero(length);
                assertFalse(S2CShipVisualEventPacket.decode(buffer).valid());
                assertEquals(0, buffer.readableBytes());
            } finally {
                buffer.release();
            }
        }
        for (int[] values : new int[][]{{-1, 0}, {42, -1}, {42, 2}, {42, Integer.MAX_VALUE}}) {
            var buffer = new FriendlyByteBuf(Unpooled.buffer());
            try {
                buffer.writeInt(values[0]).writeInt(values[1]);
                assertFalse(S2CShipVisualEventPacket.decode(buffer).valid());
            } finally {
                buffer.release();
            }
        }
        assertFalse(new S2CShipVisualEventPacket(1, ShipVisualEvent.IDLE).valid());
    }
}
