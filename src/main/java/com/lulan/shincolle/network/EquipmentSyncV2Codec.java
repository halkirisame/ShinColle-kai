package com.lulan.shincolle.network;

import com.lulan.shincolle.api.attribute.ShipAttributeLayout;
import com.lulan.shincolle.api.attribute.ShipAttributeType;
import com.lulan.shincolle.api.attribute.ShipAttributeValues;
import com.lulan.shincolle.api.equipment.ShipAttackEffect;
import com.lulan.shincolle.equipdata.EquipDataSnapshot;
import com.lulan.shincolle.equipdata.EquipAvailability;
import com.lulan.shincolle.equipdata.EquipDefinition;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Validates and serializes the v4 complete equipment-definition snapshot.
 *
 * <p>The server uses {@link #validateSnapshotForServer(EquipDataSnapshot)} before publishing a
 * reload generation. The same validation is performed by {@link #encode(EquipDataSnapshot,
 * FriendlyByteBuf)} so programmatically created snapshots cannot bypass the packet limits.</p>
 */
public final class EquipmentSyncV2Codec {

    public static final int SCHEMA_VERSION = 4;
    public static final int MAX_DEFINITIONS = 4096;
    public static final int MAX_STATS_PER_DEFINITION = 256;
    public static final int MAX_DISTINCT_ATTRIBUTE_IDS = 4096;
    public static final int MAX_ATTACK_EFFECTS_PER_DEFINITION = 64;
    public static final int MAX_PAYLOAD_BYTES = 1_048_576;

    private static final int MAX_COMPATIBLE = 32;
    private static final int MAX_RESOURCE_LOCATION_LENGTH = 256;
    private static final int MAX_COMPATIBLE_LENGTH = 128;
    private static final int MAX_MATERIAL_LENGTH = 128;
    private static final int MAX_AVAILABILITY_LENGTH = 32;

    private EquipmentSyncV2Codec() {
    }

    /**
     * Performs all generation-wide v4 checks, including the actual encoded payload size.
     *
     * @throws IllegalArgumentException when this generation cannot be published or sent
     */
    public static void validateSnapshotForServer(EquipDataSnapshot snapshot) {
        encodeToBytes(snapshot);
    }

    /** Encodes through a bounded temporary buffer so a partial oversized payload is never written. */
    public static void encode(EquipDataSnapshot snapshot, FriendlyByteBuf destination) {
        Objects.requireNonNull(destination, "destination");
        destination.writeBytes(encodeToBytes(snapshot));
    }

    /** Decodes a complete snapshot without modifying any live client state. */
    public static EquipDataSnapshot decode(FriendlyByteBuf source) {
        Objects.requireNonNull(source, "source");
        if (source.readableBytes() > MAX_PAYLOAD_BYTES) {
            throw new IllegalArgumentException("equipment synchronization payload exceeds " + MAX_PAYLOAD_BYTES
                    + " bytes");
        }

        int schemaVersion = source.readVarInt();
        if (schemaVersion != SCHEMA_VERSION) {
            throw new IllegalArgumentException("unsupported schema version " + schemaVersion);
        }

        int definitionCount = readCount(source, MAX_DEFINITIONS, "definition count");
        Map<ResourceLocation, DefinitionData> decodedDefinitions = new HashMap<>();
        Set<ResourceLocation> packetAttributeIds = new HashSet<>();
        for (int i = 0; i < definitionCount; i++) {
            DefinitionData definition = readDefinition(source, packetAttributeIds);
            if (decodedDefinitions.put(definition.id(), definition) != null) {
                throw new IllegalArgumentException("duplicate definition id " + definition.id());
            }
        }

        int itemVariantCount = readCount(source, definitionCount, "item/variant index count");
        List<ItemVariantEntry> itemVariantEntries = new ArrayList<>(itemVariantCount);
        Set<ItemVariantKey> itemVariantKeys = new HashSet<>();
        for (int i = 0; i < itemVariantCount; i++) {
            ResourceLocation item = readResourceLocation(source);
            int variant = source.readVarInt();
            ResourceLocation definitionId = readResourceLocation(source);
            if (!decodedDefinitions.containsKey(definitionId)) {
                throw new IllegalArgumentException("item/variant index references missing definition " + definitionId);
            }
            ItemVariantKey key = new ItemVariantKey(item, variant);
            if (!itemVariantKeys.add(key)) {
                throw new IllegalArgumentException("duplicate item/variant index " + item + '#' + variant);
            }
            itemVariantEntries.add(new ItemVariantEntry(item, variant, definitionId));
        }

        int legacyCount = readCount(source, definitionCount, "legacy index count");
        List<LegacyEntry> legacyEntries = new ArrayList<>(legacyCount);
        Set<Integer> legacyIds = new HashSet<>();
        for (int i = 0; i < legacyCount; i++) {
            int legacyId = source.readVarInt();
            ResourceLocation definitionId = readResourceLocation(source);
            if (!decodedDefinitions.containsKey(definitionId)) {
                throw new IllegalArgumentException("legacy index references missing definition " + definitionId);
            }
            if (!legacyIds.add(legacyId)) {
                throw new IllegalArgumentException("duplicate legacy index " + legacyId);
            }
            legacyEntries.add(new LegacyEntry(legacyId, definitionId));
        }

        if (source.isReadable()) {
            throw new IllegalArgumentException("unexpected trailing bytes in equipment synchronization payload");
        }

        ShipAttributeLayout layout = packetLayout(packetAttributeIds);
        Map<ResourceLocation, EquipDefinition> definitions = new HashMap<>();
        for (DefinitionData data : decodedDefinitions.values()) {
            ShipAttributeValues.Builder stats = ShipAttributeValues.builder(layout);
            data.stats().forEach(stats::set);
            EquipDefinition definition = new EquipDefinition(data.id(), data.item(), data.variant(), data.equipType(),
                    data.legacyEquipId(), stats.build(), data.attackEffects(), data.compatible(), data.enchantType(),
                    data.developMaterial(), data.developAmount(), data.rareMean(), data.rollType(),
                    data.availability());
            definitions.put(definition.id(), definition);
        }

        Map<ResourceLocation, Map<Integer, EquipDefinition>> itemVariants = new HashMap<>();
        for (ItemVariantEntry entry : itemVariantEntries) {
            EquipDefinition definition = definitions.get(entry.definitionId());
            itemVariants.computeIfAbsent(entry.item(), unused -> new HashMap<>()).put(entry.variant(), definition);
        }
        Map<Integer, EquipDefinition> legacyDefinitions = new HashMap<>();
        for (LegacyEntry entry : legacyEntries) {
            legacyDefinitions.put(entry.legacyId(), definitions.get(entry.definitionId()));
        }
        return new EquipDataSnapshot(definitions, itemVariants, legacyDefinitions);
    }

    private static byte[] encodeToBytes(EquipDataSnapshot snapshot) {
        validateSnapshotStructure(snapshot);
        FriendlyByteBuf temporary = new FriendlyByteBuf(Unpooled.buffer());
        try {
            writeSnapshot(temporary, snapshot);
            int size = temporary.readableBytes();
            if (size > MAX_PAYLOAD_BYTES) {
                throw new IllegalArgumentException("equipment synchronization payload exceeds " + MAX_PAYLOAD_BYTES
                        + " bytes: " + size);
            }
            byte[] result = new byte[size];
            temporary.readBytes(result);
            return result;
        } finally {
            temporary.release();
        }
    }

    private static void validateSnapshotStructure(EquipDataSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        if (snapshot.byId().size() > MAX_DEFINITIONS) {
            throw new IllegalArgumentException("definition count exceeds " + MAX_DEFINITIONS);
        }

        Set<ResourceLocation> definitionIds = new HashSet<>();
        Set<ResourceLocation> attributeIds = new HashSet<>();
        for (Map.Entry<ResourceLocation, EquipDefinition> entry : snapshot.byId().entrySet()) {
            EquipDefinition definition = entry.getValue();
            if (!entry.getKey().equals(definition.id()) || !definitionIds.add(definition.id())) {
                throw new IllegalArgumentException("byId contains a non-canonical definition key " + entry.getKey());
            }
            validateDefinition(definition, attributeIds);
        }
        if (attributeIds.size() > MAX_DISTINCT_ATTRIBUTE_IDS) {
            throw new IllegalArgumentException("distinct attribute ID count exceeds " + MAX_DISTINCT_ATTRIBUTE_IDS);
        }

        int itemVariantCount = 0;
        for (Map.Entry<ResourceLocation, Map<Integer, EquipDefinition>> itemEntry
                : snapshot.byItemVariant().entrySet()) {
            validateResourceLocation(itemEntry.getKey());
            itemVariantCount += itemEntry.getValue().size();
            for (EquipDefinition definition : itemEntry.getValue().values()) {
                requireCanonicalDefinition(snapshot, definition, "item/variant index");
            }
        }
        if (itemVariantCount > snapshot.byId().size()) {
            throw new IllegalArgumentException("item/variant index count exceeds definition count");
        }

        if (snapshot.byLegacyId().size() > snapshot.byId().size()) {
            throw new IllegalArgumentException("legacy index count exceeds definition count");
        }
        for (EquipDefinition definition : snapshot.byLegacyId().values()) {
            requireCanonicalDefinition(snapshot, definition, "legacy index");
        }
    }

    private static void validateDefinition(EquipDefinition definition, Set<ResourceLocation> attributeIds) {
        Objects.requireNonNull(definition, "definition");
        validateResourceLocation(definition.id());
        validateResourceLocation(definition.item());
        if (definition.compatible().size() > MAX_COMPATIBLE) {
            throw new IllegalArgumentException("compatible count exceeds " + MAX_COMPATIBLE + " for " + definition.id());
        }
        for (String compatible : definition.compatible()) {
            validateString(compatible, MAX_COMPATIBLE_LENGTH, "compatible value");
        }
        validateString(definition.developMaterial(), MAX_MATERIAL_LENGTH, "develop material");

        List<Map.Entry<ResourceLocation, Float>> stats = nonZeroStats(definition);
        if (stats.size() > MAX_STATS_PER_DEFINITION) {
            throw new IllegalArgumentException("stats pair count exceeds " + MAX_STATS_PER_DEFINITION + " for "
                    + definition.id());
        }
        for (Map.Entry<ResourceLocation, Float> stat : stats) {
            validateResourceLocation(stat.getKey());
            requireFinite(stat.getValue(), "stat value for " + stat.getKey());
            attributeIds.add(stat.getKey());
        }
        if (definition.attackEffects().size() > MAX_ATTACK_EFFECTS_PER_DEFINITION) {
            throw new IllegalArgumentException("attack effect count exceeds "
                    + MAX_ATTACK_EFFECTS_PER_DEFINITION + " for " + definition.id());
        }
        definition.attackEffects().forEach((effectId, effect) -> {
            validateResourceLocation(effectId);
            if (effect == null || !effectId.equals(effect.effectId())) {
                throw new IllegalArgumentException("invalid attack effect entry " + effectId
                        + " for " + definition.id());
            }
            if (!ForgeRegistries.MOB_EFFECTS.containsKey(effectId)) {
                throw new IllegalArgumentException("unregistered MobEffect " + effectId
                        + " for " + definition.id());
            }
        });
    }

    private static void requireCanonicalDefinition(EquipDataSnapshot snapshot, EquipDefinition definition,
                                                   String indexName) {
        if (definition == null || snapshot.byId().get(definition.id()) != definition) {
            throw new IllegalArgumentException(indexName + " references a definition outside the canonical byId map");
        }
    }

    private static void writeSnapshot(FriendlyByteBuf buf, EquipDataSnapshot snapshot) {
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

        List<Map.Entry<Integer, EquipDefinition>> legacyEntries = new ArrayList<>(snapshot.byLegacyId().entrySet());
        legacyEntries.sort(Map.Entry.comparingByKey());
        buf.writeVarInt(legacyEntries.size());
        for (Map.Entry<Integer, EquipDefinition> entry : legacyEntries) {
            buf.writeVarInt(entry.getKey());
            writeResourceLocation(buf, entry.getValue().id());
        }
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

        List<Map.Entry<ResourceLocation, Float>> stats = nonZeroStats(definition);
        buf.writeVarInt(stats.size());
        for (Map.Entry<ResourceLocation, Float> stat : stats) {
            writeResourceLocation(buf, stat.getKey());
            buf.writeFloat(stat.getValue());
        }

        List<Map.Entry<ResourceLocation, ShipAttackEffect>> attackEffects =
                new ArrayList<>(definition.attackEffects().entrySet());
        attackEffects.sort(Map.Entry.comparingByKey(Comparator.comparing(ResourceLocation::toString)));
        buf.writeVarInt(attackEffects.size());
        for (Map.Entry<ResourceLocation, ShipAttackEffect> entry : attackEffects) {
            ShipAttackEffect effect = entry.getValue();
            writeResourceLocation(buf, entry.getKey());
            buf.writeVarInt(effect.amplifier());
            buf.writeVarInt(effect.durationTicks());
            buf.writeVarInt(effect.chancePercent());
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
        // The stable JSON name, not the enum ordinal: reordering the enum must not be able to
        // change what an already-encoded payload means.
        buf.writeUtf(definition.availability().jsonName(), MAX_AVAILABILITY_LENGTH);
    }

    private static DefinitionData readDefinition(FriendlyByteBuf buf, Set<ResourceLocation> packetAttributeIds) {
        ResourceLocation id = readResourceLocation(buf);
        ResourceLocation item = readResourceLocation(buf);
        int variant = buf.readVarInt();
        int equipType = buf.readVarInt();
        Integer legacyId = buf.readBoolean() ? buf.readVarInt() : null;

        int statCount = readCount(buf, MAX_STATS_PER_DEFINITION, "stats pair count");
        Map<ResourceLocation, Float> stats = new LinkedHashMap<>();
        for (int i = 0; i < statCount; i++) {
            ResourceLocation attributeId = readResourceLocation(buf);
            float value = buf.readFloat();
            requireFinite(value, "stat value for " + attributeId);
            if (value == 0F) {
                throw new IllegalArgumentException("zero stat value must not be encoded for " + attributeId);
            }
            if (stats.put(attributeId, value) != null) {
                throw new IllegalArgumentException("duplicate stat attribute " + attributeId + " in " + id);
            }
            packetAttributeIds.add(attributeId);
            if (packetAttributeIds.size() > MAX_DISTINCT_ATTRIBUTE_IDS) {
                throw new IllegalArgumentException("distinct attribute ID count exceeds " + MAX_DISTINCT_ATTRIBUTE_IDS);
            }
        }

        int attackEffectCount = readCount(buf, MAX_ATTACK_EFFECTS_PER_DEFINITION, "attack effect count");
        Map<ResourceLocation, ShipAttackEffect> attackEffects = new LinkedHashMap<>();
        for (int i = 0; i < attackEffectCount; i++) {
            ResourceLocation effectId = readResourceLocation(buf);
            if (!ForgeRegistries.MOB_EFFECTS.containsKey(effectId)) {
                throw new IllegalArgumentException("unregistered MobEffect " + effectId + " in " + id);
            }
            ShipAttackEffect effect = new ShipAttackEffect(effectId, buf.readVarInt(), buf.readVarInt(),
                    buf.readVarInt());
            if (attackEffects.put(effectId, effect) != null) {
                throw new IllegalArgumentException("duplicate attack effect " + effectId + " in " + id);
            }
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
        EquipAvailability availability = EquipAvailability.fromJsonName(buf.readUtf(MAX_AVAILABILITY_LENGTH));
        return new DefinitionData(id, item, variant, equipType, legacyId, Map.copyOf(stats),
                Map.copyOf(attackEffects), List.copyOf(compatible), enchantType, developMaterial,
                developAmount, rareMean, rollType, availability);
    }

    private static ShipAttributeLayout packetLayout(Set<ResourceLocation> packetAttributeIds) {
        ShipAttributeLayout canonical = ShipAttributeLayout.current();
        Map<ResourceLocation, ShipAttributeType> types = new HashMap<>();
        for (ResourceLocation id : canonical.ids()) {
            types.put(id, canonical.type(id));
        }
        for (ResourceLocation id : packetAttributeIds) {
            types.computeIfAbsent(id, unused -> ShipAttributeType.builder().build());
        }
        return ShipAttributeLayout.detached(types);
    }

    private static List<Map.Entry<ResourceLocation, Float>> nonZeroStats(EquipDefinition definition) {
        List<Map.Entry<ResourceLocation, Float>> result = new ArrayList<>();
        for (Map.Entry<ResourceLocation, Float> stat : definition.stats().asMap().entrySet()) {
            if (stat.getValue() != 0F) {
                result.add(stat);
            }
        }
        result.sort(Map.Entry.comparingByKey(Comparator.comparing(ResourceLocation::toString)));
        return result;
    }

    private static int readCount(FriendlyByteBuf buf, int maximum, String name) {
        int count = buf.readVarInt();
        if (count < 0 || count > maximum) {
            throw new IllegalArgumentException("invalid " + name + ' ' + count);
        }
        return count;
    }

    private static void validateResourceLocation(ResourceLocation id) {
        Objects.requireNonNull(id, "resource location");
        if (id.toString().length() > MAX_RESOURCE_LOCATION_LENGTH) {
            throw new IllegalArgumentException("ResourceLocation is longer than " + MAX_RESOURCE_LOCATION_LENGTH);
        }
    }

    private static ResourceLocation readResourceLocation(FriendlyByteBuf buf) {
        String value = buf.readUtf(MAX_RESOURCE_LOCATION_LENGTH);
        ResourceLocation resourceLocation = ResourceLocation.tryParse(value);
        if (resourceLocation == null) {
            throw new IllegalArgumentException("invalid ResourceLocation " + value);
        }
        return resourceLocation;
    }

    private static void writeResourceLocation(FriendlyByteBuf buf, ResourceLocation id) {
        buf.writeUtf(id.toString(), MAX_RESOURCE_LOCATION_LENGTH);
    }

    private static void validateString(String value, int maximum, String name) {
        if (value == null || value.length() > maximum) {
            throw new IllegalArgumentException(name + " exceeds " + maximum + " characters");
        }
    }

    private static void requireFinite(float value, String name) {
        if (!Float.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
    }

    private record DefinitionData(ResourceLocation id, ResourceLocation item, int variant, int equipType,
                                  Integer legacyEquipId, Map<ResourceLocation, Float> stats,
                                  Map<ResourceLocation, ShipAttackEffect> attackEffects,
                                  List<String> compatible, int enchantType, String developMaterial,
                                  int developAmount, int rareMean, int rollType,
                                  EquipAvailability availability) {
    }

    private record ItemVariantEntry(ResourceLocation item, int variant, ResourceLocation definitionId) {
    }

    private record ItemVariantKey(ResourceLocation item, int variant) {
    }

    private record LegacyEntry(int legacyId, ResourceLocation definitionId) {
    }
}
