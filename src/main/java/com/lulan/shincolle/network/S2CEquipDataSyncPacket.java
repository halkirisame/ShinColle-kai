package com.lulan.shincolle.network;

import com.lulan.shincolle.ShinColle;
import com.lulan.shincolle.equipdata.ClientEquipData;
import com.lulan.shincolle.equipdata.EquipDataSnapshot;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/** Synchronizes one complete, already-resolved equipment-data snapshot to a client. */
public class S2CEquipDataSyncPacket {

    public static final int SCHEMA_VERSION = EquipmentSyncV2Codec.SCHEMA_VERSION;
    public static final int MAX_DEFINITIONS = EquipmentSyncV2Codec.MAX_DEFINITIONS;

    private static final Set<ResourceLocation> REPORTED_MISSING_ITEMS = ConcurrentHashMap.newKeySet();

    private final EquipDataSnapshot snapshot;
    private final String decodeError;

    public S2CEquipDataSyncPacket(EquipDataSnapshot snapshot) {
        this.snapshot = snapshot;
        this.decodeError = null;
    }

    /** Decoder constructor. Invalid data is retained as an error and never partially applied. */
    public S2CEquipDataSyncPacket(FriendlyByteBuf buf) {
        EquipDataSnapshot decoded = null;
        String error = null;
        try {
            decoded = EquipmentSyncV2Codec.decode(buf);
        } catch (RuntimeException exception) {
            error = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
        }
        this.snapshot = decoded;
        this.decodeError = error;
    }

    public void encode(FriendlyByteBuf buf) {
        if (snapshot == null) {
            throw new IllegalStateException("Cannot encode an invalid equipment snapshot packet");
        }
        EquipmentSyncV2Codec.encode(snapshot, buf);
    }

    public void handle(Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context context = ctxSupplier.get();
        context.enqueueWork(this::applyToClient);
        context.setPacketHandled(true);
    }

    /** Apply a fully decoded snapshot atomically; invalid packets leave the current client state untouched. */
    public boolean applyToClient() {
        if (snapshot == null) {
            ShinColle.LOGGER.error("Rejected ship equipment synchronization packet: {}", decodeError);
            return false;
        }
        reportMissingItems(snapshot);
        ClientEquipData.install(snapshot);
        return true;
    }

    public boolean isValid() {
        return snapshot != null;
    }

    public EquipDataSnapshot snapshot() {
        return snapshot;
    }

    public String decodeError() {
        return decodeError;
    }

    private static void reportMissingItems(EquipDataSnapshot dataSnapshot) {
        dataSnapshot.all().forEach(definition -> {
            if (!ForgeRegistries.ITEMS.containsKey(definition.item())
                    && REPORTED_MISSING_ITEMS.add(definition.item())) {
                ShinColle.LOGGER.warn("Synchronized ship equipment {} references unregistered client item {}; "
                        + "display will use safe defaults", definition.id(), definition.item());
            }
        });
    }
}
