package com.lulan.shincolle.network;

import com.lulan.shincolle.visualdata.ShipVisualEvent;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Consumer;
import java.util.function.Supplier;

/** Typed attack presentation for data-defined ships; no combat authority or clip names. */
public record S2CShipVisualEventPacket(int entityId, ShipVisualEvent event) {
    private static Consumer<S2CShipVisualEventPacket> client = ignored -> { };

    public static void bindClient(Consumer<S2CShipVisualEventPacket> endpoint) {
        client = endpoint;
    }

    public static S2CShipVisualEventPacket decode(FriendlyByteBuf input) {
        if (input.readableBytes() != 8) {
            input.skipBytes(input.readableBytes());
            return new S2CShipVisualEventPacket(-1, null);
        }
        int entity = input.readInt();
        int kind = input.readInt();
        return new S2CShipVisualEventPacket(entity, kind == 0 ? ShipVisualEvent.LIGHT_ATTACK :
                kind == 1 ? ShipVisualEvent.HEAVY_ATTACK : null);
    }

    public boolean valid() {
        return entityId >= 0 && event != null && event.isAttack();
    }

    public void encode(FriendlyByteBuf output) {
        if (!valid()) throw new IllegalArgumentException("Invalid ship visual event");
        output.writeInt(entityId);
        output.writeInt(event == ShipVisualEvent.LIGHT_ATTACK ? 0 : 1);
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        var context = supplier.get();
        context.enqueueWork(() -> {
            if (context.getDirection().getReceptionSide().isClient() && valid()) client.accept(this);
        });
        context.setPacketHandled(true);
    }
}
