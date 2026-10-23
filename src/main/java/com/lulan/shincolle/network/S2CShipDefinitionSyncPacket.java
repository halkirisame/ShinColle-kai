package com.lulan.shincolle.network;

import com.lulan.shincolle.ShinColle;
import com.lulan.shincolle.shipdata.ShipDefinition;
import com.lulan.shincolle.shipdata.ShipDefinitionFormat;
import com.lulan.shincolle.shipdata.ShipDefinitionId;
import com.lulan.shincolle.shipdata.ShipDefinitionSnapshot;
import com.lulan.shincolle.shipdata.ShipPresentation;
import com.lulan.shincolle.shipdata.ShipPresentationClientAccess;
import com.lulan.shincolle.shipdata.ShipPresentationSnapshot;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/** Full presentation-only snapshot, installed atomically on login and reload. */
public final class S2CShipDefinitionSyncPacket {
    private final ShipPresentationSnapshot snapshot;

    public S2CShipDefinitionSyncPacket(ShipDefinitionSnapshot source) {
        Map<String, ShipPresentation> definitions = new HashMap<>();
        source.all().forEach((id, definition) -> definitions.put(id.toString(), ShipPresentation.of(definition)));
        snapshot = new ShipPresentationSnapshot(source.generation(), definitions);
    }

    public S2CShipDefinitionSyncPacket(FriendlyByteBuf buf) {
        ShipPresentationSnapshot decoded = null;
        try {
            long generation = buf.readVarLong();
            int size = buf.readVarInt();
            // Every entry consumes more than one byte; bound allocation by the actual payload.
            if (generation < 0 || size < 0 || size > buf.readableBytes()) {
                throw new IllegalArgumentException("Invalid ship snapshot header");
            }
            Map<String, ShipPresentation> definitions = new HashMap<>();
            for (int i = 0; i < size; i++) {
                String id = ShipDefinitionId.parse(buf.readUtf(64)).toString();
                String name = buf.readUtf(128);
                ResourceLocation visual = ShipDefinitionId.parse(buf.readUtf(64)).location();
                float width = buf.readFloat();
                float height = buf.readFloat();
                ResourceLocation type = ShipDefinitionId.parse(buf.readUtf(64)).location();
                double mountOffset = buf.readDouble();
                int equipType = buf.readVarInt();
                if (name.codePointCount(0, name.length()) > 64 || !Float.isFinite(width) || width <= 0
                        || width > (float) ShipDefinitionFormat.MAX_WIDTH || !Float.isFinite(height) || height <= 0
                        || height > (float) ShipDefinitionFormat.MAX_HEIGHT
                        || !Double.isFinite(mountOffset) || mountOffset < 0 || mountOffset > ShipDefinitionFormat.MAX_HEIGHT
                        || equipType < 0 || equipType > 3
                        || !type.getNamespace().equals("shincolle_kai")
                        || !ShipDefinitionFormat.SHIP_TYPES.contains(type.getPath())) {
                    throw new IllegalArgumentException("Invalid ship presentation");
                }
                if (definitions.put(id, new ShipPresentation(name, visual,
                        new ShipDefinition.Dimensions(width, height), type, mountOffset, equipType)) != null) {
                    throw new IllegalArgumentException("Duplicate ship presentation");
                }
            }
            if (buf.isReadable()) {
                throw new IllegalArgumentException("Trailing ship snapshot bytes");
            }
            decoded = new ShipPresentationSnapshot(generation, definitions);
        } catch (RuntimeException exception) {
            ShinColle.LOGGER.error("Rejected ship definition synchronization: {}", exception.getMessage());
        }
        snapshot = decoded;
    }

    public void encode(FriendlyByteBuf buf) {
        if (snapshot == null) {
            throw new IllegalStateException("Invalid ship definition snapshot");
        }
        buf.writeVarLong(snapshot.generation());
        buf.writeVarInt(snapshot.definitions().size());
        snapshot.definitions().forEach((id, view) -> {
            buf.writeUtf(id, 64);
            buf.writeUtf(view.displayName(), 128);
            buf.writeUtf(view.visual().toString(), 64);
            buf.writeFloat(view.dimensions().width());
            buf.writeFloat(view.dimensions().height());
            buf.writeUtf(view.shipType().toString(), 64);
            buf.writeDouble(view.mountOffset());
            buf.writeVarInt(view.equipType());
        });
    }

    public ShipPresentationSnapshot snapshot() {
        return snapshot;
    }

    public boolean applyToClient() {
        return snapshot != null && ShipPresentationClientAccess.install(snapshot);
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(this::applyToClient);
        context.setPacketHandled(true);
    }
}
