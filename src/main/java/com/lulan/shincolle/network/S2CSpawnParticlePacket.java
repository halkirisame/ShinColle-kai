package com.lulan.shincolle.network;

import com.lulan.shincolle.utility.ParticleHelper;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server-to-Client particle spawn packet.
 * <p>
 * Triggers client-side particle effects for ShinColle entities.
 * Different type values correspond to different particle effects
 * (attacks, skill effects, emotional indicators, etc.).
 * <p>
 * ParticlePacketRoute separates position effects from entity-attached
 * effects using both the effect number and the payload format.
 */
public class S2CSpawnParticlePacket {

    private final byte type;
    private final int entityId;
    private final byte[] payload;
    private static final long ENTITY_WAIT_NANOS = 500_000_000L;

    /**
     * Construct a new particle spawn packet.
     *
     * @param type     the particle effect type
     * @param entityId the source entity ID
     * @param payload  additional particle parameters (position offsets, colors,
     *                 etc.)
     */
    public S2CSpawnParticlePacket(byte type, int entityId, byte[] payload) {
        this.type = type;
        this.entityId = entityId;
        this.payload = payload != null ? payload : new byte[0];
    }

    public static S2CSpawnParticlePacket position(int type, int entityId, double x, double y, double z,
                                                 double vx, double vy, double vz) {
        return new S2CSpawnParticlePacket((byte) type, entityId,
                parameters(x, y, z, vx, vy, vz));
    }

    public static S2CSpawnParticlePacket entityParameters(byte type, int entityId, double a, double b, double c) {
        return new S2CSpawnParticlePacket(type, entityId, parameters(a, b, c));
    }

    public static S2CSpawnParticlePacket targetParameters(byte type, int entityId, int targetId,
                                                         double a, double b, double c) {
        if (!Double.isFinite(a) || !Double.isFinite(b) || !Double.isFinite(c)) {
            throw new IllegalArgumentException("Particle parameter must be finite");
        }
        java.nio.ByteBuffer buf = java.nio.ByteBuffer.allocate(28);
        buf.putInt(targetId).putDouble(a).putDouble(b).putDouble(c);
        return new S2CSpawnParticlePacket(type, entityId, buf.array());
    }

    private static byte[] parameters(double... values) {
        java.nio.ByteBuffer buf = java.nio.ByteBuffer.allocate(values.length * Double.BYTES);
        for (double value : values) {
            if (!Double.isFinite(value)) throw new IllegalArgumentException("Particle parameter must be finite");
            buf.putDouble(value);
        }
        return buf.array();
    }

    public ParticlePacketRoute route() {
        return ParticlePacketRoute.resolve(Byte.toUnsignedInt(type), payload.length);
    }

    /**
     * Decoder constructor - reads from network buffer.
     */
    public S2CSpawnParticlePacket(FriendlyByteBuf buf) {
        this.type = buf.readByte();
        this.entityId = buf.readInt();
        int len = buf.readVarInt();
        this.payload = buf.readByteArray(len);
    }

    /**
     * Encode this packet into a network buffer.
     */
    public void encode(FriendlyByteBuf buf) {
        buf.writeByte(type);
        buf.writeInt(entityId);
        buf.writeVarInt(payload.length);
        buf.writeByteArray(payload);
    }

    /**
     * Handle the packet on the client main thread.
     * Dispatches to ParticleHelper to spawn attack/effect particles.
     */
    public void handle(Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            // [PORT] 1.10.2 -> 1.20.1: hard-guard client-only particle handling by
            // reception side.
            if (ctx.getDirection().getReceptionSide().isClient()) {
                handleClient();
            }
        });
        ctx.setPacketHandled(true);
    }

    @OnlyIn(Dist.CLIENT)
    private void handleClient() {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.level == null)
            return;

        ParticlePacketRoute route = route();
        if (route == ParticlePacketRoute.INVALID) return;
        if (route == ParticlePacketRoute.FLUID_FILL || route == ParticlePacketRoute.CRANE_TRANSFER
                || route == ParticlePacketRoute.EQUIPMENT_SMOKE || route == ParticlePacketRoute.MOUNT_CANNON_SMOKE
                || route == ParticlePacketRoute.PURPLE_BEAM || route == ParticlePacketRoute.YAMATO_CHARGE
                || route == ParticlePacketRoute.YAMATO_BEAM
                || route == ParticlePacketRoute.DUAL_BEAM || route == ParticlePacketRoute.SUPPORT_LINE) {
            showEntityRoute(mc, route, System.nanoTime() + ENTITY_WAIT_NANOS);
            return;
        }

        if (route == ParticlePacketRoute.REPAIR_GODDESS) {
            Entity saved = mc.level.getEntity(entityId);
            if (saved != null) {
                ParticleHelper.spawnSparkleParticle(saved, 8, 0.1F, saved.getBbWidth() * 2F, 0F, 0F,
                        0F, 0.03F, 0F, 1F, saved.getBbHeight() * 0.4F);
            }
            return;
        }

        if (type == com.lulan.shincolle.ai.ShipSkillAttackGate.VISUAL_PACKET_TYPE) {
            Entity source = mc.level.getEntity(entityId);
            if (source == null || payload.length != 49) return;
            FriendlyByteBuf skill = new FriendlyByteBuf(Unpooled.wrappedBuffer(payload));
            try {
                int effect = skill.readUnsignedByte();
                if (effect >= SkillVisualEffect.values().length) return;
                double[] values = new double[6];
                for (int i = 0; i < values.length; i++) {
                    values[i] = skill.readDouble();
                    if (!Double.isFinite(values[i])) return;
                }
                com.lulan.shincolle.client.particle.SkillAttackVisuals.show(SkillVisualEffect.values()[effect], source,
                        values[0], values[1], values[2], values[3], values[4], values[5]);
            } finally {
                skill.release();
            }
            return;
        }

        double x, y, z, lookX = 0, lookY = 1, lookZ = 0;

        // Healing sparkle (type 23). The original spawns this through
        // spawnAttackParticleAtEntity, so it needs the entity rather than a
        // position and cannot go through the position-based switch below.
        if (route == ParticlePacketRoute.HEAL) {
            Entity healed = mc.level.getEntity(entityId);
            if (healed == null) {
                return;
            }
            // Original 1.10.2 arguments: type 2, scale 0.075, radius width*1.5,
            // two unused slots, RGBA, height*0.4. The heal packet carries no
            // colour payload, so the original's green heal tint is used.
            ParticleHelper.spawnSparkleParticle(healed, 2,
                    0.075F, healed.getBbWidth() * 1.5F, 0F, 0F,
                    0.3F, 1F, 0.3F, 1F, healed.getBbHeight() * 0.4F);
            return;
        }

        // Special handling for emotion particles (type 36)
        if (route == ParticlePacketRoute.EMOTION) {
            Entity entity = mc.level.getEntity(entityId);
            if (entity == null)
                return;
            // Decode height and emotion type from 4-byte payload
            if (payload.length >= 4) {
                int heightEncoded = ((payload[0] & 0xFF) << 8) | (payload[1] & 0xFF);
                float h = heightEncoded / 100.0F;
                int emotionType = payload[3] & 0xFF;
                ParticleHelper.spawnEmotionParticle(entity, emotionType);
            }
            return;
        }

        if (payload.length >= 48) {
            // Full payload: 6 doubles (position + look direction)
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(payload));
            try {
                x = buf.readDouble();
                y = buf.readDouble();
                z = buf.readDouble();
                lookX = buf.readDouble();
                lookY = buf.readDouble();
                lookZ = buf.readDouble();
            } finally {
                buf.release();
            }
        } else if (payload.length >= 24) {
            // Partial payload: 3 doubles (position only)
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(payload));
            try {
                x = buf.readDouble();
                y = buf.readDouble();
                z = buf.readDouble();
            } finally {
                buf.release();
            }
        } else {
            // No position payload: derive from entity
            Entity entity = mc.level.getEntity(entityId);
            if (entity == null)
                return;
            x = entity.getX();
            y = entity.getY();
            z = entity.getZ();
            if (type >= 10 && type <= 13 || type == 34) lookY = entity.getBbHeight() * 1.3D;
        }

        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                || !Double.isFinite(lookX) || !Double.isFinite(lookY) || !Double.isFinite(lookZ)) return;
        if (route == ParticlePacketRoute.ORDER_REJECTED) {
            ParticleHelper.spawnOrderRejectedParticle(mc.level, x, y, z, lookX, lookY, lookZ);
            return;
        }

        com.lulan.shincolle.utility.LogHelper.debug("DEBUG: particle packet received: type=" + type
                + " entityId=" + entityId + " payloadLen=" + payload.length
                + " resolved=(" + x + "," + y + "," + z + ") vec=(" + lookX + "," + lookY + "," + lookZ + ")");
        ParticleHelper.spawnAttackParticleAt(mc.level, x, y, z, lookX, lookY, lookZ, type);
    }

    /** Entity-attached effects may arrive before the entities they name; wait briefly for them. */
    @OnlyIn(Dist.CLIENT)
    private void showEntityRoute(net.minecraft.client.Minecraft mc, ParticlePacketRoute route, long deadline) {
        if (mc.level == null) return;
        Entity source = mc.level.getEntity(entityId);
        java.nio.ByteBuffer values = java.nio.ByteBuffer.wrap(payload);
        Entity target = null;
        boolean needsTarget = route == ParticlePacketRoute.PURPLE_BEAM || route == ParticlePacketRoute.DUAL_BEAM
                || route == ParticlePacketRoute.YAMATO_BEAM || route == ParticlePacketRoute.SUPPORT_LINE;
        if (needsTarget) {
            target = mc.level.getEntity(values.getInt());
        }
        if ((source == null || needsTarget && target == null) && System.nanoTime() < deadline) {
            mc.tell(() -> showEntityRoute(mc, route, deadline));
            return;
        }
        double a = values.getDouble();
        double b = values.getDouble();
        double c = values.getDouble();
        if (!Double.isFinite(a) || !Double.isFinite(b) || !Double.isFinite(c)) return;
        com.lulan.shincolle.client.particle.EntityParticleVisuals.show(route, source, target, a, b, c,
                type == ParticlePacketRoute.YAMATO_BOSS_BEAM_TYPE);
    }

    // ========== Getters ==========

    public byte getType() {
        return type;
    }

    public int getEntityId() {
        return entityId;
    }

    public byte[] getPayload() {
        return payload;
    }
}
