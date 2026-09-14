package com.lulan.shincolle.network;

import com.lulan.shincolle.entity.IShipEmotion;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class S2CAttackAnimationPacketWireTest {

    @Test
    void standardAnimationRoundTripsAsExactlyEightBytes() {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        new S2CAttackAnimationPacket(42, 50).encode(buf);

        assertEquals(8, buf.readableBytes());
        S2CAttackAnimationPacket decoded = new S2CAttackAnimationPacket(buf);
        assertTrue(decoded.isValid());
        assertEquals(42, decoded.entityId());
        assertEquals(50, decoded.durationTicks());
        assertEquals(0, buf.readableBytes());
    }

    @Test
    void malformedAndOutOfRangePayloadsAreRejected() {
        FriendlyByteBuf truncated = new FriendlyByteBuf(Unpooled.buffer());
        truncated.writeInt(42);
        assertFalse(new S2CAttackAnimationPacket(truncated).isValid());

        FriendlyByteBuf trailing = new FriendlyByteBuf(Unpooled.buffer());
        trailing.writeInt(42);
        trailing.writeInt(50);
        trailing.writeByte(1);
        assertFalse(new S2CAttackAnimationPacket(trailing).isValid());

        assertFalse(decodeInts(-1, 50).isValid());
        assertFalse(decodeInts(42, 0).isValid());
        assertFalse(decodeInts(42, 201).isValid());
    }

    @Test
    void invalidPacketDoesNotApplyAnAttackTick() {
        AtomicInteger appliedTick = new AtomicInteger(-1);
        IShipEmotion emotion = (IShipEmotion) Proxy.newProxyInstance(
                IShipEmotion.class.getClassLoader(),
                new Class<?>[]{IShipEmotion.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("setAttackTick")) {
                        appliedTick.set((int) args[0]);
                    }
                    return defaultValue(method.getReturnType());
                });

        assertFalse(decodeInts(42, 0).applyTo(emotion));
        assertEquals(-1, appliedTick.get());
        assertTrue(decodeInts(42, 50).applyTo(emotion));
        assertEquals(50, appliedTick.get());
    }

    private static S2CAttackAnimationPacket decodeInts(int entityId, int durationTicks) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeInt(entityId);
        buf.writeInt(durationTicks);
        return new S2CAttackAnimationPacket(buf);
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive() || type == void.class) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == char.class) {
            return '\0';
        }
        return 0;
    }
}
