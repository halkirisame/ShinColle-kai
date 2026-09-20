package com.lulan.shincolle.gametest;

import com.lulan.shincolle.api.attribute.CoreShipAttributes;
import com.lulan.shincolle.api.attribute.ShipAttributeLayer;
import com.lulan.shincolle.api.attribute.ShipAttributeLayout;
import com.lulan.shincolle.api.attribute.ShipAttributeValues;
import com.lulan.shincolle.network.ShipAttributeSyncV2Codec;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;
import com.lulan.shincolle.reference.unitclass.AttrsAdv;
import io.netty.buffer.Unpooled;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.Arrays;

/** Boundary, revision and atomic-apply tests for dynamic entity attribute synchronization. */
@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ShipAttributeSyncV2GameTests {

    private ShipAttributeSyncV2GameTests() {
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void fullRoundTripAppliesAllLayersAndLegacyBridge(GameTestHelper helper) {
        AttrsAdv server = populatedAttrs();
        byte[] payload = ShipAttributeSyncV2Codec.encode(server, 3L,
                ShipAttributeSyncV2Codec.ALL_FIELDS_MASK);
        ShipAttributeSyncV2Codec.Snapshot snapshot = ShipAttributeSyncV2Codec.decode(payload);
        AttrsAdv client = new AttrsAdv();

        if (!snapshot.applyTo(client)) {
            throw new AssertionError("Fresh full snapshot was rejected");
        }
        for (ShipAttributeLayer layer : ShipAttributeLayer.values()) {
            if (!server.shipAttributes(layer).asMap().equals(client.shipAttributes(layer).asMap())) {
                throw new AssertionError("Applied layer differs: " + layer);
            }
        }
        if (!Arrays.equals(server.getAttrsBonus(), client.getAttrsBonus())) {
            throw new AssertionError("Applied bonus differs");
        }
        if (Float.compare(server.getMinMOV(), client.getMinMOV()) != 0) {
            throw new AssertionError("Applied MinMOV differs");
        }
        if (Float.compare(client.getAttrsEquip(ID.Attrs.ATK_L),
                client.shipAttributes(ShipAttributeLayer.EQUIPMENT).get(CoreShipAttributes.ATK_L)) != 0) {
            throw new AssertionError("Legacy equipment array was not mirrored");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void staleRevisionDoesNotMutateClientState(GameTestHelper helper) {
        AttrsAdv client = new AttrsAdv();
        ShipAttributeSyncV2Codec.Snapshot newer = ShipAttributeSyncV2Codec.decode(
                ShipAttributeSyncV2Codec.encode(populatedAttrs(), 8L, ShipAttributeSyncV2Codec.RAW_MASK));
        newer.applyTo(client);
        float accepted = client.getAttrsRaw(ID.Attrs.HP);

        AttrsAdv staleSource = populatedAttrs();
        staleSource.setAttrsRaw(ID.Attrs.HP, accepted + 100F);
        ShipAttributeSyncV2Codec.Snapshot stale = ShipAttributeSyncV2Codec.decode(
                ShipAttributeSyncV2Codec.encode(staleSource, 7L, ShipAttributeSyncV2Codec.RAW_MASK));
        if (stale.applyTo(client)) {
            throw new AssertionError("Older revision was applied");
        }
        if (Float.compare(accepted, client.getAttrsRaw(ID.Attrs.HP)) != 0
                || client.lastAppliedAttributeSyncRevision() != 8L) {
            throw new AssertionError("Older revision partially changed client state");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void malformedPayloadsAreRejectedBeforeApply(GameTestHelper helper) {
        AttrsAdv client = new AttrsAdv();
        float original = client.getAttrsRaw(ID.Attrs.HP);

        expectDecodeFailure(payload(buf -> {
            buf.writeVarInt(99);
            buf.writeVarLong(0L);
            buf.writeByte(0);
        }), "schema");
        expectDecodeFailure(payload(buf -> {
            buf.writeVarInt(ShipAttributeSyncV2Codec.SCHEMA_VERSION);
            buf.writeVarLong(0L);
            buf.writeByte(0x80);
        }), "reserved mask");
        expectDecodeFailure(payload(buf -> {
            buf.writeVarInt(ShipAttributeSyncV2Codec.SCHEMA_VERSION);
            buf.writeVarLong(0L);
            buf.writeByte(ShipAttributeSyncV2Codec.BONUS_MASK);
            buf.writeVarInt(5);
            buf.writeBytes(new byte[5]);
        }), "bonus length");
        expectDecodeFailure(payload(buf -> {
            buf.writeVarInt(ShipAttributeSyncV2Codec.SCHEMA_VERSION);
            buf.writeVarLong(0L);
            buf.writeByte(ShipAttributeSyncV2Codec.RAW_MASK);
            buf.writeVarInt(ShipAttributeLayout.current().size() - 1);
        }), "canonical count mismatch");
        expectDecodeFailure(payload(buf -> {
            buf.writeVarInt(ShipAttributeSyncV2Codec.SCHEMA_VERSION);
            buf.writeVarLong(0L);
            buf.writeByte(ShipAttributeSyncV2Codec.RAW_MASK);
            buf.writeVarInt(ShipAttributeSyncV2Codec.MAX_ATTRIBUTES_PER_LAYER + 1);
        }), "attribute count limit");
        expectDecodeFailure(layerPayload(Float.NaN, null, false), "non-finite");
        expectDecodeFailure(layerPayload(1F, ShipAttributeLayout.current().idAt(0), false), "duplicate");
        expectDecodeFailure(layerPayload(1F,
                ResourceLocation.fromNamespaceAndPath("missing", "attribute"), true), "unknown");

        byte[] valid = ShipAttributeSyncV2Codec.encode(populatedAttrs(), 1L,
                ShipAttributeSyncV2Codec.RAW_MASK);
        expectDecodeFailure(Arrays.copyOf(valid, valid.length - 1), "truncated");
        byte[] trailing = Arrays.copyOf(valid, valid.length + 1);
        trailing[trailing.length - 1] = 1;
        expectDecodeFailure(trailing, "trailing");

        if (Float.compare(original, client.getAttrsRaw(ID.Attrs.HP)) != 0
                || client.lastAppliedAttributeSyncRevision() != -1L) {
            throw new AssertionError("Decode failure changed live client state");
        }
        helper.succeed();
    }

    private static AttrsAdv populatedAttrs() {
        AttrsAdv attrs = new AttrsAdv();
        byte[] bonus = new byte[attrs.getAttrsBonus().length];
        for (int i = 0; i < bonus.length; i++) {
            bonus[i] = (byte) (10 + i);
        }
        attrs.setAttrsBonus(bonus);
        for (ShipAttributeLayer layer : ShipAttributeLayer.values()) {
            ShipAttributeValues.Builder values = ShipAttributeValues.builder(ShipAttributeLayout.current());
            for (int i = 0; i < CoreShipAttributes.LEGACY_ORDER.size(); i++) {
                values.set(CoreShipAttributes.LEGACY_ORDER.get(i), layer.ordinal() * 100F + i + 1F);
            }
            attrs.setShipAttributes(layer, values.build());
        }
        attrs.setMinMOV(0.45F);
        return attrs;
    }

    private static byte[] layerPayload(float firstValue, ResourceLocation replacementId,
                                       boolean replaceFirst) {
        ShipAttributeLayout layout = ShipAttributeLayout.current();
        return payload(buf -> {
            buf.writeVarInt(ShipAttributeSyncV2Codec.SCHEMA_VERSION);
            buf.writeVarLong(0L);
            buf.writeByte(ShipAttributeSyncV2Codec.RAW_MASK);
            buf.writeVarInt(layout.size());
            for (int i = 0; i < layout.size(); i++) {
                ResourceLocation id = layout.idAt(i);
                if (replacementId != null && (replaceFirst ? i == 0 : i == 1)) {
                    id = replacementId;
                }
                buf.writeUtf(id.toString(), 256);
                buf.writeFloat(i == 0 ? firstValue : i + 1F);
            }
        });
    }

    private static byte[] payload(java.util.function.Consumer<FriendlyByteBuf> writer) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            writer.accept(buffer);
            byte[] result = new byte[buffer.readableBytes()];
            buffer.readBytes(result);
            return result;
        } finally {
            buffer.release();
        }
    }

    private static void expectDecodeFailure(byte[] payload, String name) {
        try {
            ShipAttributeSyncV2Codec.decode(payload);
            throw new AssertionError("Expected " + name + " payload rejection");
        } catch (RuntimeException expected) {
            // Expected rejection.
        }
    }
}
