package com.lulan.shincolle.network;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class S2CSpawnParticlePacketWireTest {
    @Test
    void actualOutOfFuelSenderPayloadRoutesToRedRejectionAtShipAndDestination() throws ReflectiveOperationException {
        Method positionPayload = C2SGUIInputPacket.class.getDeclaredMethod(
                "positionPayload", double.class, double.class, double.class);
        positionPayload.setAccessible(true);
        Field particleType = C2SGUIInputPacket.class.getDeclaredField("OutOfFuelParticle");
        particleType.setAccessible(true);
        byte type = particleType.getByte(null);
        assertEquals(ParticlePacketRoute.ORDER_REJECTED_TYPE, type);

        double[][] positions = {{12.5D, 65.8D, -3D}, {0D, -60D, 128D}};
        for (double[] position : positions) {
            byte[] payload = (byte[]) positionPayload.invoke(null, position[0], position[1], position[2]);
            assertEquals(ParticlePacketRoute.ORDER_REJECTED,
                    ParticlePacketRoute.resolve(Byte.toUnsignedInt(type), payload.length));
            S2CSpawnParticlePacket received = roundTrip(new S2CSpawnParticlePacket(type, 42, payload));
            assertEquals(ParticlePacketRoute.ORDER_REJECTED, received.route());
            ByteBuffer values = ByteBuffer.wrap(received.getPayload());
            for (double expected : position) {
                assertEquals(expected, values.getDouble());
            }
            assertEquals(0, values.remaining());
        }
    }

    @Test
    void fistAndHealingRoundTripWithDifferentPayloadContracts() {
        S2CSpawnParticlePacket fist = roundTrip(S2CSpawnParticlePacket.position(23, 42,
                -1D, -60D, 0D, 2D, 3D, 4D));
        assertEquals(ParticlePacketRoute.POSITION, fist.route());
        ByteBuffer values = ByteBuffer.wrap(fist.getPayload());
        for (double expected : new double[]{-1D, -60D, 0D, 2D, 3D, 4D}) {
            assertEquals(expected, values.getDouble());
        }
        assertEquals(0, values.remaining());
        assertEquals(ParticlePacketRoute.HEAL,
                roundTrip(new S2CSpawnParticlePacket((byte) 23, 42, null)).route());
    }

    @Test
    void entityParametersDoNotDecodeAsPositionAndTargetIdentitySurvives() {
        S2CSpawnParticlePacket sparkle = roundTrip(S2CSpawnParticlePacket.entityParameters(
                ParticlePacketRoute.CRANE_TRANSFER_TYPE, 42, 0D, 0.1D, 0D));
        assertEquals(ParticlePacketRoute.CRANE_TRANSFER, sparkle.route());
        assertEquals(24, sparkle.getPayload().length);
        S2CSpawnParticlePacket beam = roundTrip(S2CSpawnParticlePacket.targetParameters(
                ParticlePacketRoute.PURPLE_BEAM_TYPE, 42, 99, 1.7D, 0.08D, 0.02D));
        ByteBuffer values = ByteBuffer.wrap(beam.getPayload());
        assertEquals(99, values.getInt());
        assertEquals(1.7D, values.getDouble());
        assertEquals(0.08D, values.getDouble());
        assertEquals(0.02D, values.getDouble());
        assertEquals(ParticlePacketRoute.PURPLE_BEAM, beam.route());
    }

    @Test
    void finiteParametersAreRequiredBeforeSending() {
        assertThrows(IllegalArgumentException.class, () -> S2CSpawnParticlePacket.position(48, 1,
                0D, Double.NaN, 0D, 0D, 0D, 0D));
    }

    @Test
    void supportLineKeepsSourceAndTargetThroughWireRoundTrip() {
        S2CSpawnParticlePacket received = roundTrip(S2CSpawnParticlePacket.targetParameters(
                ParticlePacketRoute.SUPPORT_LINE_TYPE, 42, 99, 0D, 0D, 0D));
        assertEquals(ParticlePacketRoute.SUPPORT_LINE, received.route());
        assertEquals(42, received.getEntityId());
        ByteBuffer values = ByteBuffer.wrap(received.getPayload());
        assertEquals(99, values.getInt());
        for (int i = 0; i < 3; i++) {
            assertEquals(0D, values.getDouble());
        }
        assertEquals(0, values.remaining());
        assertThrows(IllegalArgumentException.class, () -> S2CSpawnParticlePacket.targetParameters(
                ParticlePacketRoute.SUPPORT_LINE_TYPE, 42, 99, Double.NaN, 0D, 0D));
    }

    private static S2CSpawnParticlePacket roundTrip(S2CSpawnParticlePacket packet) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            packet.encode(buf);
            S2CSpawnParticlePacket decoded = new S2CSpawnParticlePacket(buf);
            assertEquals(packet.getType(), decoded.getType());
            assertEquals(packet.getEntityId(), decoded.getEntityId());
            assertEquals(0, buf.readableBytes());
            return decoded;
        } finally {
            buf.release();
        }
    }
}
