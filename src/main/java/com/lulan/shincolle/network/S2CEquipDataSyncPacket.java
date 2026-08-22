package com.lulan.shincolle.network;

import com.lulan.shincolle.ShinColle;
import com.lulan.shincolle.equipdata.ClientEquipData;
import com.lulan.shincolle.equipdata.EquipDataSnapshot;
import com.lulan.shincolle.equipdata.EquipDefinition;
import com.lulan.shincolle.reference.unitclass.Attrs;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/** Synchronizes one complete, already-resolved equipment-data snapshot to a client. */
public class S2CEquipDataSyncPacket {

    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_DEFINITIONS = 4096;

    private static final int MAX_COMPATIBLE = 32;
    private static final int MAX_RESOURCE_LOCATION_LENGTH = 256;
    private static final int MAX_COMPATIBLE_LENGTH = 128;
    private static final int MAX_MATERIAL_LENGTH = 128;
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
            decoded = decodeSnapshot(buf);
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

        buf.writeVarInt(SCHEMA_VERSION);
        List<EquipDefinition> definitions = new ArrayList<>(snapshot.byId().values());
        definitions.sort(Comparator.comparing(definition -> definition.id().toString()));
        buf.writeVarInt(definitions.size());
        for (EquipDefinition definition : definitions) {
            writeDefinition(buf, definition);
        }

        List<ItemVariantEntry> itemVariantEntries = new ArrayList<>();
        snapshot.byItemVariant().forEach((item, variants) -> variants.forEach((variant, definition) ->
                itemVariantEntries.add(new ItemVariantEntry(item, variant, definition.id()))));
        itemVariantEntries.sort(Comparator.comparing((ItemVariantEntry entry) -> entry.item().toString())
                .thenComparingInt(ItemVariantEntry::variant));
        buf.writeVarInt(itemVariantEntries.size());
        for (ItemVariantEntry entry : itemVariantEntries) {
            writeResourceLocation(buf, entry.item());
            buf.writeVarInt(entry.variant());
            writeResourceLocation(buf, entry.definitionId());
        }

        List<Map.Entry<Integer, EquipDefinition>> legacyEntries = new ArrayList<>(
                snapshot.byLegacyId().entrySet());
        legacyEntries.sort(Map.Entry.comparingByKey());
        buf.writeVarInt(legacyEntries.size());
        for (Map.Entry<Integer, EquipDefinition> entry : legacyEntries) {
            buf.writeVarInt(entry.getKey());
            writeResourceLocation(buf, entry.getValue().id());
        }
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

    private static EquipDataSnapshot decodeSnapshot(FriendlyByteBuf buf) {
        int schemaVersion = buf.readVarInt();
        if (schemaVersion != SCHEMA_VERSION) {
            throw new IllegalArgumentException("unsupported schema version " + schemaVersion);
        }

        int definitionCount = readCount(buf, MAX_DEFINITIONS, "definition count");
        Map<ResourceLocation, EquipDefinition> definitions = new HashMap<>();
        for (int i = 0; i < definitionCount; i++) {
            EquipDefinition definition = readDefinition(buf);
            if (definitions.put(definition.id(), definition) != null) {
                throw new IllegalArgumentException("duplicate definition id " + definition.id());
            }
        }

        int itemVariantCount = readCount(buf, definitionCount, "item/variant index count");
        Map<ResourceLocation, Map<Integer, EquipDefinition>> itemVariants = new HashMap<>();
        Set<ItemVariantKey> itemVariantKeys = new HashSet<>();
        for (int i = 0; i < itemVariantCount; i++) {
            ResourceLocation item = readResourceLocation(buf);
            int variant = buf.readVarInt();
            ResourceLocation definitionId = readResourceLocation(buf);
            EquipDefinition definition = definitions.get(definitionId);
            if (definition == null) {
                throw new IllegalArgumentException("item/variant index references missing definition "
                        + definitionId);
            }
            ItemVariantKey key = new ItemVariantKey(item, variant);
            if (!itemVariantKeys.add(key)) {
                throw new IllegalArgumentException("duplicate item/variant index " + item + '#' + variant);
            }
            itemVariants.computeIfAbsent(item, unused -> new HashMap<>()).put(variant, definition);
        }

        int legacyCount = readCount(buf, definitionCount, "legacy index count");
        Map<Integer, EquipDefinition> legacyDefinitions = new HashMap<>();
        for (int i = 0; i < legacyCount; i++) {
            int legacyId = buf.readVarInt();
            ResourceLocation definitionId = readResourceLocation(buf);
            EquipDefinition definition = definitions.get(definitionId);
            if (definition == null) {
                throw new IllegalArgumentException("legacy index references missing definition " + definitionId);
            }
            if (legacyDefinitions.put(legacyId, definition) != null) {
                throw new IllegalArgumentException("duplicate legacy index " + legacyId);
            }
        }

        return new EquipDataSnapshot(definitions, itemVariants, legacyDefinitions);
    }

    private static void writeDefinition(FriendlyByteBuf buf, EquipDefinition definition) {
        writeResourceLocation(buf, definition.id());
        writeResourceLocation(buf, definition.item());
        buf.writeVarInt(definition.variant());
        buf.writeVarInt(definition.equipType());
        buf.writeBoolean(definition.legacyEquipId() != null);
        if (definition.legacyEquipId() != null) {
            buf.writeVarInt(definition.legacyEquipId());
        }

        float[] stats = definition.stats();
        buf.writeVarInt(stats.length);
        for (float stat : stats) {
            buf.writeFloat(stat);
        }

        buf.writeVarInt(definition.compatible().size());
        for (String compatible : definition.compatible()) {
            buf.writeUtf(compatible, MAX_COMPATIBLE_LENGTH);
        }
        buf.writeVarInt(definition.enchantType());
        buf.writeUtf(definition.developMaterial(), MAX_MATERIAL_LENGTH);
        buf.writeVarInt(definition.developAmount());
        buf.writeVarInt(definition.rareMean());
        buf.writeVarInt(definition.rollType());
    }

    private static EquipDefinition readDefinition(FriendlyByteBuf buf) {
        ResourceLocation id = readResourceLocation(buf);
        ResourceLocation item = readResourceLocation(buf);
        int variant = buf.readVarInt();
        int equipType = buf.readVarInt();
        Integer legacyId = buf.readBoolean() ? buf.readVarInt() : null;

        int statsLength = buf.readVarInt();
        if (statsLength != Attrs.AttrsLength) {
            throw new IllegalArgumentException("invalid stats length " + statsLength);
        }
        float[] stats = new float[statsLength];
        for (int i = 0; i < statsLength; i++) {
            stats[i] = buf.readFloat();
        }

        int compatibleCount = readCount(buf, MAX_COMPATIBLE, "compatible count");
        List<String> compatible = new ArrayList<>(compatibleCount);
        for (int i = 0; i < compatibleCount; i++) {
            compatible.add(buf.readUtf(MAX_COMPATIBLE_LENGTH));
        }
        int enchantType = buf.readVarInt();
        String developMaterial = buf.readUtf(MAX_MATERIAL_LENGTH);
        int developAmount = buf.readVarInt();
        int rareMean = buf.readVarInt();
        int rollType = buf.readVarInt();
        return new EquipDefinition(id, item, variant, equipType, legacyId, stats, List.copyOf(compatible),
                enchantType, developMaterial, developAmount, rareMean, rollType);
    }

    private static int readCount(FriendlyByteBuf buf, int maximum, String name) {
        int count = buf.readVarInt();
        if (count < 0 || count > maximum) {
            throw new IllegalArgumentException("invalid " + name + ' ' + count);
        }
        return count;
    }

    private static void writeResourceLocation(FriendlyByteBuf buf, ResourceLocation resourceLocation) {
        buf.writeUtf(resourceLocation.toString(), MAX_RESOURCE_LOCATION_LENGTH);
    }

    private static ResourceLocation readResourceLocation(FriendlyByteBuf buf) {
        String value = buf.readUtf(MAX_RESOURCE_LOCATION_LENGTH);
        ResourceLocation resourceLocation = ResourceLocation.tryParse(value);
        if (resourceLocation == null) {
            throw new IllegalArgumentException("invalid ResourceLocation " + value);
        }
        return resourceLocation;
    }

    private static void reportMissingItems(EquipDataSnapshot dataSnapshot) {
        for (EquipDefinition definition : dataSnapshot.all()) {
            if (!ForgeRegistries.ITEMS.containsKey(definition.item())
                    && REPORTED_MISSING_ITEMS.add(definition.item())) {
                ShinColle.LOGGER.warn("Synchronized ship equipment {} references unregistered client item {}; "
                        + "display will use safe defaults", definition.id(), definition.item());
            }
        }
    }

    private record ItemVariantEntry(ResourceLocation item, int variant, ResourceLocation definitionId) {
    }

    private record ItemVariantKey(ResourceLocation item, int variant) {
    }
}
