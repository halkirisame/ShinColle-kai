package com.lulan.shincolle.network;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Round-trips the packet through a byte buffer.
 * <p>
 * GameTests hand the packet object straight to the handler, so nothing there exercises
 * encode and decode. When the SetMove payload was widened to eight values without
 * raising the decoder's cap, every move order threw
 * {@code IllegalArgumentException: Invalid int array length: 8} on the server and dropped
 * the sender's connection, while all 278 GameTests still passed three runs in a row.
 */
class C2SGUIInputPacketWireTest {

    private static C2SGUIInputPacket roundTrip(byte type, int[] values) {
        C2SGUIInputPacket sent = new C2SGUIInputPacket(type, values);
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        sent.encode(buf);
        return new C2SGUIInputPacket(buf);
    }

    @Test
    void setMoveSurvivesTheWireWithTheArrivalReleaseFlag() {
        // player id, unused dim, mode, guard type, x, y, z, release on arrival
        int[] values = {42, 0, 1, 1, 128, -60, -256, 1};
        C2SGUIInputPacket received = roundTrip(C2SGUIInputPacket.SetMove, values);

        assertEquals(C2SGUIInputPacket.SetMove, received.getType());
        assertArrayEquals(values, received.getValues());
    }

    @Test
    void legacySevenValueMoveStillSurvivesTheWire() {
        int[] values = {42, 0, 1, 1, 128, -60, -256};
        C2SGUIInputPacket received = roundTrip(C2SGUIInputPacket.SetMove, values);

        assertArrayEquals(values, received.getValues());
    }

    @Test
    void teamManagementInputFlagSurvivesTheWire() {
        for (int active : new int[]{0, 1}) {
            int[] values = {42, 0, 123, active};
            C2SGUIInputPacket received = roundTrip(C2SGUIInputPacket.AddTeam, values);
            assertEquals(C2SGUIInputPacket.AddTeam, received.getType());
            assertArrayEquals(values, received.getValues());
        }
    }

    @Test
    void shortCommandsSurviveTheWire() {
        int[] values = {42, 0, 3};
        C2SGUIInputPacket received = roundTrip(C2SGUIInputPacket.SetSelect, values);

        assertArrayEquals(values, received.getValues());
    }
}
