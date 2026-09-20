package com.lulan.shincolle.network;

import com.lulan.shincolle.entity.IShipEmotion;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Starts a transient attack animation on the client copy of an entity. */
public final class S2CAttackAnimationPacket {

    public static final int STANDARD_DURATION_TICKS = 50;
    public static final int MAX_DURATION_TICKS = 200;
    private static final int PAYLOAD_BYTES = Integer.BYTES * 2;

    private final int entityId;
    private final int durationTicks;
    private final boolean valid;

    public S2CAttackAnimationPacket(int entityId, int durationTicks) {
        this.entityId = entityId;
        this.durationTicks = durationTicks;
        this.valid = valuesAreValid(entityId, durationTicks);
    }

    /** Decodes an all-or-nothing fixed-width payload. */
    public S2CAttackAnimationPacket(FriendlyByteBuf buf) {
        int decodedEntityId = -1;
        int decodedDurationTicks = 0;
        boolean decodedValid = false;
        try {
            if (buf.readableBytes() != PAYLOAD_BYTES) {
                throw new IllegalArgumentException("Attack animation payload must be exactly 8 bytes");
            }
            decodedEntityId = buf.readInt();
            decodedDurationTicks = buf.readInt();
            decodedValid = valuesAreValid(decodedEntityId, decodedDurationTicks);
        } catch (RuntimeException exception) {
            decodedValid = false;
        } finally {
            if (buf.isReadable()) {
                buf.skipBytes(buf.readableBytes());
            }
        }
        this.entityId = decodedEntityId;
        this.durationTicks = decodedDurationTicks;
        this.valid = decodedValid;
    }

    public void encode(FriendlyByteBuf buf) {
        if (!valid) {
            throw new IllegalStateException("Cannot encode an invalid attack animation packet");
        }
        buf.writeInt(entityId);
        buf.writeInt(durationTicks);
    }

    public void handle(Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context context = ctxSupplier.get();
        context.enqueueWork(() -> {
            if (context.getDirection().getReceptionSide().isClient()) {
                applyToClient();
            }
        });
        context.setPacketHandled(true);
    }

    @OnlyIn(Dist.CLIENT)
    private boolean applyToClient() {
        if (!valid) {
            return false;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return false;
        }
        Entity entity = minecraft.level.getEntity(entityId);
        if (!(entity instanceof IShipEmotion emotion)) {
            return false;
        }
        return applyTo(emotion);
    }

    boolean applyTo(IShipEmotion emotion) {
        if (!valid || emotion == null) {
            return false;
        }
        emotion.setAttackTick(durationTicks);
        return true;
    }

    public boolean isValid() {
        return valid;
    }

    public int entityId() {
        return entityId;
    }

    public int durationTicks() {
        return durationTicks;
    }

    private static boolean valuesAreValid(int entityId, int durationTicks) {
        return entityId >= 0 && durationTicks > 0 && durationTicks <= MAX_DURATION_TICKS;
    }
}
