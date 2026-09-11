package com.lulan.shincolle.network;

import com.lulan.shincolle.ShinColle;
import com.lulan.shincolle.client.ClientShipLevelCaps;
import com.lulan.shincolle.entity.ShipLevelCapSummary;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.Objects;
import java.util.function.Supplier;

/** Synchronizes the server's complete ship level-cap summary to a client. */
public class S2CShipLevelCapPacket {

    private final ShipLevelCapSummary summary;
    private final String decodeError;

    public S2CShipLevelCapPacket(ShipLevelCapSummary summary) {
        this.summary = Objects.requireNonNull(summary, "summary");
        this.decodeError = null;
    }

    /** Decoder constructor. Invalid payloads are retained as errors and never partially applied. */
    public S2CShipLevelCapPacket(FriendlyByteBuf buf) {
        ShipLevelCapSummary decoded = null;
        String error = null;
        try {
            int unmarriedCap = buf.readInt();
            int absoluteCap = buf.readInt();
            if (buf.isReadable()) {
                throw new IllegalArgumentException("Trailing ship level-cap payload data");
            }
            decoded = new ShipLevelCapSummary(unmarriedCap, absoluteCap);
        } catch (RuntimeException exception) {
            error = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
        }
        this.summary = decoded;
        this.decodeError = error;
    }

    public void encode(FriendlyByteBuf buf) {
        if (summary == null) {
            throw new IllegalStateException("Cannot encode an invalid ship level-cap packet");
        }
        buf.writeInt(summary.unmarriedCap());
        buf.writeInt(summary.absoluteCap());
    }

    public void handle(Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context context = ctxSupplier.get();
        context.enqueueWork(this::applyToClient);
        context.setPacketHandled(true);
    }

    /** Applies one complete valid summary; invalid packets leave the current cache untouched. */
    public boolean applyToClient() {
        if (summary == null) {
            ShinColle.LOGGER.error("Rejected ship level-cap synchronization packet: {}", decodeError);
            return false;
        }
        ClientShipLevelCaps.install(summary);
        return true;
    }

    public boolean isValid() {
        return summary != null;
    }

    public ShipLevelCapSummary summary() {
        return summary;
    }

    public String decodeError() {
        return decodeError;
    }
}
