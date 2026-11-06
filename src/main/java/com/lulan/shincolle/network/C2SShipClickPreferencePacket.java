package com.lulan.shincolle.network;

import com.lulan.shincolle.handler.ShipInteractionPreferences;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Only the sender's session preference can be changed. */
public record C2SShipClickPreferencePacket(boolean swapped) {
    public static C2SShipClickPreferencePacket decode(FriendlyByteBuf buf) {
        if (buf.readableBytes() != 1) {
            throw new DecoderException("Invalid ship click preference size");
        }
        int value = buf.readUnsignedByte();
        if (value > 1) {
            throw new DecoderException("Invalid ship click preference boolean");
        }
        return new C2SShipClickPreferencePacket(value == 1);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(swapped);
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        // Registered with consumerMainThread and PLAY_TO_SERVER.
        ServerPlayer sender = context.getSender();
        if (sender != null) {
            ShipInteractionPreferences.setSwapClicks(sender.getUUID(), swapped);
        }
        context.setPacketHandled(true);
    }
}
