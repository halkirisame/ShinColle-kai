package com.lulan.shincolle.network;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class C2SShipClickPreferencePacketTest {
    @Test
    void bothPreferencesRoundTripAsOneByte() {
        for (boolean swapped : new boolean[]{false, true}) {
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            try {
                var sent = new C2SShipClickPreferencePacket(swapped);
                sent.encode(buf);
                assertEquals(1, buf.readableBytes());
                assertEquals(sent, C2SShipClickPreferencePacket.decode(buf));
                assertEquals(0, buf.readableBytes());
            } finally {
                buf.release();
            }
        }
    }

    @Test
    void malformedPayloadsAreRejectedAtDecode() {
        for (byte[] payload : new byte[][]{new byte[0], {0, 1}, {2}, {(byte) 255}}) {
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(payload));
            try {
                assertThrows(DecoderException.class, () -> C2SShipClickPreferencePacket.decode(buf));
            } finally {
                buf.release();
            }
        }
    }
}
