package com.lulan.shincolle.network;

import com.lulan.shincolle.api.attribute.ShipAttributeLayer;
import com.lulan.shincolle.api.attribute.ShipAttributeLayout;
import com.lulan.shincolle.api.attribute.ShipAttributeValues;
import com.lulan.shincolle.reference.unitclass.AttrsAdv;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Bounded schema-v2 codec for complete or dirty ship-attribute layers. */
public final class ShipAttributeSyncV2Codec {

    public static final int SCHEMA_VERSION = 2;
    public static final int MAX_ATTRIBUTES_PER_LAYER = 4096;
    public static final int MAX_PAYLOAD_BYTES = 1_048_576;

    public static final int BONUS_MASK = 1;
    public static final int RAW_MASK = 1 << 1;
    public static final int EQUIPMENT_MASK = 1 << 2;
    public static final int MORALE_MASK = 1 << 3;
    public static final int POTION_MASK = 1 << 4;
    public static final int FORMATION_MASK = 1 << 5;
    public static final int BUFFED_MASK = 1 << 6;
    public static final int ALL_FIELDS_MASK = (1 << 7) - 1;

    private static final int MAX_RESOURCE_LOCATION_LENGTH = 256;

    private ShipAttributeSyncV2Codec() {
    }

    public static byte[] encode(AttrsAdv attrs, long revision, int fieldMask) {
        Objects.requireNonNull(attrs, "attrs");
        validateHeader(revision, fieldMask);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buffer.writeVarInt(SCHEMA_VERSION);
            buffer.writeVarLong(revision);
            buffer.writeByte(fieldMask);
            if ((fieldMask & BONUS_MASK) != 0) {
                writeBonus(buffer, attrs.getAttrsBonus());
            }
            for (ShipAttributeLayer layer : syncedLayers()) {
                if ((fieldMask & maskFor(layer)) != 0) {
                    writeLayer(buffer, attrs.shipAttributes(layer));
                }
            }
            if ((fieldMask & (FORMATION_MASK | BUFFED_MASK)) != 0) {
                requireFinite(attrs.getMinMOV(), "MinMOV");
                buffer.writeFloat(attrs.getMinMOV());
            }
            if (buffer.readableBytes() > MAX_PAYLOAD_BYTES) {
                throw new IllegalArgumentException("ship attribute payload exceeds " + MAX_PAYLOAD_BYTES
                        + " bytes");
            }
            byte[] payload = new byte[buffer.readableBytes()];
            buffer.readBytes(payload);
            return payload;
        } finally {
            buffer.release();
        }
    }

    public static Snapshot decode(byte[] payload) {
        Objects.requireNonNull(payload, "payload");
        if (payload.length > MAX_PAYLOAD_BYTES) {
            throw new IllegalArgumentException("ship attribute payload exceeds " + MAX_PAYLOAD_BYTES + " bytes");
        }
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.wrappedBuffer(payload));
        try {
            return decode(buffer);
        } finally {
            buffer.release();
        }
    }

    public static Snapshot decode(FriendlyByteBuf buffer) {
        Objects.requireNonNull(buffer, "buffer");
        if (buffer.readableBytes() > MAX_PAYLOAD_BYTES) {
            throw new IllegalArgumentException("ship attribute payload exceeds " + MAX_PAYLOAD_BYTES + " bytes");
        }
        int schema = buffer.readVarInt();
        if (schema != SCHEMA_VERSION) {
            throw new IllegalArgumentException("unsupported ship attribute schema " + schema);
        }
        long revision = buffer.readVarLong();
        int fieldMask = buffer.readUnsignedByte();
        validateHeader(revision, fieldMask);

        byte[] bonus = (fieldMask & BONUS_MASK) == 0 ? null : readBonus(buffer);
        ShipAttributeLayout layout = ShipAttributeLayout.current();
        EnumMap<ShipAttributeLayer, ShipAttributeValues> layers = new EnumMap<>(ShipAttributeLayer.class);
        for (ShipAttributeLayer layer : syncedLayers()) {
            if ((fieldMask & maskFor(layer)) != 0) {
                layers.put(layer, readLayer(buffer, layout));
            }
        }
        Float minMOV = null;
        if ((fieldMask & (FORMATION_MASK | BUFFED_MASK)) != 0) {
            minMOV = buffer.readFloat();
            requireFinite(minMOV, "MinMOV");
        }
        if (buffer.isReadable()) {
            throw new IllegalArgumentException("unexpected trailing bytes in ship attribute payload");
        }
        return new Snapshot(revision, fieldMask, bonus, layers, minMOV);
    }

    public static int maskFor(ShipAttributeLayer layer) {
        return switch (Objects.requireNonNull(layer, "layer")) {
            case RAW -> RAW_MASK;
            case EQUIPMENT -> EQUIPMENT_MASK;
            case MORALE -> MORALE_MASK;
            case POTION -> POTION_MASK;
            case FORMATION -> FORMATION_MASK;
            case BUFFED -> BUFFED_MASK;
        };
    }

    private static ShipAttributeLayer[] syncedLayers() {
        return ShipAttributeLayer.values();
    }

    private static void writeBonus(FriendlyByteBuf buffer, byte[] bonus) {
        if (bonus == null || bonus.length != 6) {
            throw new IllegalArgumentException("ship attribute bonus length must be 6");
        }
        buffer.writeVarInt(bonus.length);
        buffer.writeBytes(bonus);
    }

    private static byte[] readBonus(FriendlyByteBuf buffer) {
        int count = buffer.readVarInt();
        if (count != 6 || buffer.readableBytes() < count) {
            throw new IllegalArgumentException("ship attribute bonus length must be 6");
        }
        byte[] bonus = new byte[count];
        buffer.readBytes(bonus);
        return bonus;
    }

    private static void writeLayer(FriendlyByteBuf buffer, ShipAttributeValues values) {
        ShipAttributeLayout layout = values.layout();
        if (layout.size() > MAX_ATTRIBUTES_PER_LAYER) {
            throw new IllegalArgumentException("ship attribute count exceeds " + MAX_ATTRIBUTES_PER_LAYER);
        }
        buffer.writeVarInt(layout.size());
        for (ResourceLocation id : layout.ids()) {
            writeResourceLocation(buffer, id);
            float value = values.get(id);
            requireFinite(value, "ship attribute " + id);
            buffer.writeFloat(value);
        }
    }

    private static ShipAttributeValues readLayer(FriendlyByteBuf buffer, ShipAttributeLayout layout) {
        int count = buffer.readVarInt();
        if (count != layout.size() || count > MAX_ATTRIBUTES_PER_LAYER) {
            throw new IllegalArgumentException("ship attribute layer count does not match canonical layout: "
                    + count + " != " + layout.size());
        }
        ShipAttributeValues.Builder values = ShipAttributeValues.builder(layout);
        Set<ResourceLocation> seen = new HashSet<>();
        for (int i = 0; i < count; i++) {
            ResourceLocation id = readResourceLocation(buffer);
            if (layout.indexOf(id) < 0) {
                throw new IllegalArgumentException("unknown ship attribute " + id);
            }
            if (!seen.add(id)) {
                throw new IllegalArgumentException("duplicate ship attribute " + id);
            }
            float value = buffer.readFloat();
            requireFinite(value, "ship attribute " + id);
            values.set(id, value);
        }
        if (seen.size() != layout.size()) {
            throw new IllegalArgumentException("ship attribute layer does not contain the canonical ID set");
        }
        return values.build();
    }

    private static ResourceLocation readResourceLocation(FriendlyByteBuf buffer) {
        String value = buffer.readUtf(MAX_RESOURCE_LOCATION_LENGTH);
        ResourceLocation id = ResourceLocation.tryParse(value);
        if (id == null) {
            throw new IllegalArgumentException("invalid ship attribute ID " + value);
        }
        return id;
    }

    private static void writeResourceLocation(FriendlyByteBuf buffer, ResourceLocation id) {
        if (id.toString().length() > MAX_RESOURCE_LOCATION_LENGTH) {
            throw new IllegalArgumentException("ship attribute ID exceeds " + MAX_RESOURCE_LOCATION_LENGTH
                    + " characters");
        }
        buffer.writeUtf(id.toString(), MAX_RESOURCE_LOCATION_LENGTH);
    }

    private static void validateHeader(long revision, int fieldMask) {
        if (revision < 0) {
            throw new IllegalArgumentException("ship attribute revision must be non-negative");
        }
        if ((fieldMask & ~ALL_FIELDS_MASK) != 0) {
            throw new IllegalArgumentException("ship attribute field mask contains reserved bits: " + fieldMask);
        }
    }

    private static void requireFinite(float value, String name) {
        if (!Float.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
    }

    /** Fully validated, immutable packet state. */
    public record Snapshot(long revision, int fieldMask, byte[] bonus,
                           Map<ShipAttributeLayer, ShipAttributeValues> layers, Float minMOV) {

        public Snapshot {
            bonus = bonus == null ? null : bonus.clone();
            layers = Map.copyOf(layers);
        }

        @Override
        public byte[] bonus() {
            return bonus == null ? null : bonus.clone();
        }

        public boolean applyTo(AttrsAdv attrs) {
            return attrs.applySyncedShipAttributes(revision, bonus, layers, minMOV);
        }
    }
}
