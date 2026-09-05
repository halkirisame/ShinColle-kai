package com.lulan.shincolle.gametest;

import com.lulan.shincolle.api.attribute.ShipAttributeLayout;
import com.lulan.shincolle.api.attribute.ShipAttributeType;
import com.lulan.shincolle.api.attribute.ShipAttributeValues;
import com.lulan.shincolle.api.equipment.ShipAttackEffect;
import com.lulan.shincolle.equipdata.ClientEquipData;
import com.lulan.shincolle.equipdata.EquipAvailability;
import com.lulan.shincolle.equipdata.EquipDataSnapshot;
import com.lulan.shincolle.equipdata.EquipDefinition;
import com.lulan.shincolle.network.EquipmentSyncV2Codec;
import com.lulan.shincolle.network.S2CEquipDataSyncPacket;
import com.lulan.shincolle.reference.Reference;

import io.netty.buffer.Unpooled;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/** Regression coverage for the equipment definition synchronization codec (schema v4). */
@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class EquipmentSyncV2GameTests {

    private static final ResourceLocation ITEM = id(Reference.MOD_ID, "equip_cannon");
    private static final ResourceLocation CORE_HP = id(Reference.MOD_ID, "hp");
    private static final ResourceLocation OPAQUE = id("sync_test", "opaque_precision");
    private static final ResourceLocation POISON = id("minecraft", "poison");

    private EquipmentSyncV2GameTests() {
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void equipmentSyncV2RoundTripUsesOneDetachedOpaqueLayout(GameTestHelper helper) {
        ShipAttributeLayout canonical = ShipAttributeLayout.current();
        if (canonical.indexOf(OPAQUE) >= 0) {
            throw new AssertionError("Opaque test ID unexpectedly exists in the canonical layout");
        }

        ShipAttributeLayout sourceLayout = layoutWithOpaque();
        ShipAttackEffect poison = new ShipAttackEffect(POISON, 2, 120, 35);
        EquipDefinition first = definition("v2_first", 1, 101, sourceLayout,
                Map.of(CORE_HP, 2.5F, OPAQUE, 0.25F), Map.of(POISON, poison),
                EquipAvailability.TREASURE_ONLY);
        EquipDefinition second = definition("v2_second", 2, null, sourceLayout,
                Map.of(OPAQUE, 0.5F));
        EquipDataSnapshot source = snapshot(List.of(first, second),
                Map.of(ITEM, Map.of(1, first, 2, second)), Map.of(101, first));

        S2CEquipDataSyncPacket packet = roundTrip(source);
        if (!packet.isValid()) {
            throw new AssertionError("Schema v4 round-trip failed: " + packet.decodeError());
        }
        EquipDataSnapshot decoded = packet.snapshot();
        EquipDefinition decodedFirst = decoded.get(first.id());
        EquipDefinition decodedSecond = decoded.get(second.id());
        if (decodedFirst == null || decodedSecond == null
                || decodedFirst != decoded.byItemVariant(ITEM, 1)
                || decodedFirst != decoded.byLegacyId(101)) {
            throw new AssertionError("Decoded v2 indexes did not retain canonical definition instances");
        }
        if (decodedFirst.stats().layout() != decodedSecond.stats().layout()
                || decodedFirst.stats().layout().indexOf(OPAQUE) < 0
                || decodedFirst.stats().layout().type(OPAQUE) != decodedSecond.stats().layout().type(OPAQUE)) {
            throw new AssertionError("Decoded v2 definitions did not share one opaque detached layout");
        }
        assertFloat(2.5F, decodedFirst.stats().get(CORE_HP), "core round-trip value");
        assertFloat(0.25F, decodedFirst.stats().get(OPAQUE), "opaque first value");
        assertFloat(0.5F, decodedSecond.stats().get(OPAQUE), "opaque second value");
        if (!poison.equals(decodedFirst.attackEffects().get(POISON))) {
            throw new AssertionError("Attack effect did not survive the schema v4 round trip");
        }
        if (!decodedFirst.availability().canLoot() || decodedFirst.availability().canDevelop()) {
            throw new AssertionError("Availability did not survive the schema v4 round trip");
        }
        if (ShipAttributeLayout.current() != canonical || canonical.indexOf(OPAQUE) >= 0) {
            throw new AssertionError("Equipment packet decoding polluted the canonical attribute layout");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void equipmentSyncV2EncodingIsDeterministicAndRejectsProgrammaticOverflow(GameTestHelper helper) {
        ShipAttributeLayout layout = layoutWithOpaque();
        EquipDefinition first = definition("v2_deterministic_first", 3, 303, layout,
                Map.of(CORE_HP, 3F, OPAQUE, 0.75F));
        EquipDefinition second = definition("v2_deterministic_second", 4, null, layout,
                Map.of(OPAQUE, 0.125F));

        EquipDataSnapshot firstOrder = snapshot(List.of(first, second),
                Map.of(ITEM, Map.of(3, first, 4, second)), Map.of(303, first));
        Map<ResourceLocation, EquipDefinition> reversedDefinitions = new LinkedHashMap<>();
        reversedDefinitions.put(second.id(), second);
        reversedDefinitions.put(first.id(), first);
        Map<ResourceLocation, Map<Integer, EquipDefinition>> reversedVariants = new LinkedHashMap<>();
        reversedVariants.put(ITEM, Map.of(4, second, 3, first));
        EquipDataSnapshot secondOrder = new EquipDataSnapshot(reversedDefinitions, reversedVariants, Map.of(303, first));
        if (!Arrays.equals(encodedBytes(firstOrder), encodedBytes(secondOrder))) {
            throw new AssertionError("Equivalent v2 snapshots encoded to different byte sequences");
        }

        Map<ResourceLocation, ShipAttributeType> tooManyTypes = canonicalTypes();
        Map<ResourceLocation, Float> tooManyValues = new HashMap<>();
        for (int i = 0; i <= EquipmentSyncV2Codec.MAX_STATS_PER_DEFINITION; i++) {
            ResourceLocation attribute = id("sync_overflow", "attribute_" + i);
            tooManyTypes.put(attribute, ShipAttributeType.builder().build());
            tooManyValues.put(attribute, 1F);
        }
        ShipAttributeLayout tooManyLayout = ShipAttributeLayout.detached(tooManyTypes);
        EquipDefinition overflow = definition("v2_programmatic_overflow", 5, null, tooManyLayout, tooManyValues);
        expectIllegalArgument(() -> encodeProgrammatic(snapshot(List.of(overflow),
                Map.of(ITEM, Map.of(5, overflow)), Map.of())), "programmatic stats overflow");

        List<EquipDefinition> tooManyDefinitions = new ArrayList<>();
        for (int i = 0; i <= EquipmentSyncV2Codec.MAX_DEFINITIONS; i++) {
            tooManyDefinitions.add(definition("v2_definition_overflow_" + i, i, null,
                    ShipAttributeLayout.current(), Map.of()));
        }
        expectIllegalArgument(() -> encodeProgrammatic(snapshot(tooManyDefinitions, Map.of(), Map.of())),
                "programmatic definition overflow");

        List<EquipDefinition> distinctDefinitions = definitionsWithDistinctAttributes(
                EquipmentSyncV2Codec.MAX_DISTINCT_ATTRIBUTE_IDS + 1);
        expectIllegalArgument(() -> encodeProgrammatic(snapshot(distinctDefinitions, Map.of(), Map.of())),
                "programmatic distinct attribute overflow");

        String longCompatible = "x".repeat(128);
        List<String> compatible = Collections.nCopies(32, longCompatible);
        List<EquipDefinition> oversizedDefinitions = new ArrayList<>();
        for (int i = 0; i < 512; i++) {
            ResourceLocation id = id("sync_payload", "definition_" + i);
            oversizedDefinitions.add(new EquipDefinition(id, ITEM, i, 0, null,
                    ShipAttributeValues.zero(ShipAttributeLayout.current()), compatible, 0,
                    longCompatible, 0, 0, 0));
        }
        expectIllegalArgument(() -> encodeProgrammatic(snapshot(oversizedDefinitions, Map.of(), Map.of())),
                "programmatic payload byte overflow");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void equipmentSyncV2RejectsMalformedPayloadsAtomically(GameTestHelper helper) {
        EquipDataSnapshot original = ClientEquipData.current();
        ShipAttributeLayout layout = ShipAttributeLayout.current();
        EquipDefinition retained = definition("v2_retained", 6, 606, layout, Map.of(CORE_HP, 6F));
        EquipDataSnapshot retainedSnapshot = snapshot(List.of(retained), Map.of(ITEM, Map.of(6, retained)),
                Map.of(606, retained));
        try {
            ClientEquipData.install(retainedSnapshot);

            FriendlyByteBuf unknownSchema = new FriendlyByteBuf(Unpooled.buffer());
            unknownSchema.writeVarInt(EquipmentSyncV2Codec.SCHEMA_VERSION - 1);
            assertRejected(unknownSchema, retainedSnapshot, "unknown schema");

            FriendlyByteBuf duplicateDefinition = new FriendlyByteBuf(Unpooled.buffer());
            duplicateDefinition.writeVarInt(EquipmentSyncV2Codec.SCHEMA_VERSION);
            duplicateDefinition.writeVarInt(2);
            for (int i = 0; i < 2; i++) {
                writeDefinitionStart(duplicateDefinition, id("sync_test", "duplicate_definition"));
                duplicateDefinition.writeVarInt(0);
                writeDefinitionEnd(duplicateDefinition);
            }
            duplicateDefinition.writeVarInt(0);
            duplicateDefinition.writeVarInt(0);
            assertRejected(duplicateDefinition, retainedSnapshot, "duplicate definition ID");

            FriendlyByteBuf duplicateAttribute = malformedHeader();
            writeDefinitionStart(duplicateAttribute, id("sync_test", "duplicate"));
            duplicateAttribute.writeVarInt(2);
            writeStat(duplicateAttribute, CORE_HP, 1F);
            writeStat(duplicateAttribute, CORE_HP, 2F);
            writeDefinitionEnd(duplicateAttribute);
            duplicateAttribute.writeVarInt(0);
            duplicateAttribute.writeVarInt(0);
            assertRejected(duplicateAttribute, retainedSnapshot, "duplicate stat ID");

            FriendlyByteBuf nonFinite = malformedHeader();
            writeDefinitionStart(nonFinite, id("sync_test", "non_finite"));
            nonFinite.writeVarInt(1);
            writeStat(nonFinite, CORE_HP, Float.NaN);
            writeDefinitionEnd(nonFinite);
            nonFinite.writeVarInt(0);
            nonFinite.writeVarInt(0);
            assertRejected(nonFinite, retainedSnapshot, "non-finite stat");

            for (float infinity : List.of(Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
                FriendlyByteBuf infinite = malformedHeader();
                writeDefinitionStart(infinite, id("sync_test", "infinite_" + (infinity > 0F ? "positive" : "negative")));
                infinite.writeVarInt(1);
                writeStat(infinite, CORE_HP, infinity);
                writeDefinitionEnd(infinite);
                infinite.writeVarInt(0);
                infinite.writeVarInt(0);
                assertRejected(infinite, retainedSnapshot, "infinite stat");
            }

            FriendlyByteBuf tooManyPairs = malformedHeader();
            writeDefinitionStart(tooManyPairs, id("sync_test", "too_many_pairs"));
            tooManyPairs.writeVarInt(EquipmentSyncV2Codec.MAX_STATS_PER_DEFINITION + 1);
            assertRejected(tooManyPairs, retainedSnapshot, "stats pair count overflow");

            FriendlyByteBuf duplicateEffect = malformedHeader();
            writeDefinitionStart(duplicateEffect, id("sync_test", "duplicate_effect"));
            duplicateEffect.writeVarInt(0);
            duplicateEffect.writeVarInt(2);
            writeAttackEffect(duplicateEffect, POISON, 0, 20, 100);
            writeAttackEffect(duplicateEffect, POISON, 1, 40, 50);
            writeDefinitionTail(duplicateEffect);
            duplicateEffect.writeVarInt(0);
            duplicateEffect.writeVarInt(0);
            assertRejected(duplicateEffect, retainedSnapshot, "duplicate attack effect ID");

            FriendlyByteBuf invalidEffect = malformedHeader();
            writeDefinitionStart(invalidEffect, id("sync_test", "invalid_effect"));
            invalidEffect.writeVarInt(0);
            invalidEffect.writeVarInt(1);
            writeAttackEffect(invalidEffect, POISON, 0, 20, 101);
            writeDefinitionTail(invalidEffect);
            invalidEffect.writeVarInt(0);
            invalidEffect.writeVarInt(0);
            assertRejected(invalidEffect, retainedSnapshot, "invalid attack effect chance");

            FriendlyByteBuf tooManyEffects = malformedHeader();
            writeDefinitionStart(tooManyEffects, id("sync_test", "too_many_effects"));
            tooManyEffects.writeVarInt(0);
            tooManyEffects.writeVarInt(EquipmentSyncV2Codec.MAX_ATTACK_EFFECTS_PER_DEFINITION + 1);
            assertRejected(tooManyEffects, retainedSnapshot, "attack effect count overflow");

            FriendlyByteBuf invalidAvailability = malformedHeader();
            writeDefinitionStart(invalidAvailability, id("sync_test", "invalid_availability"));
            invalidAvailability.writeVarInt(0);
            invalidAvailability.writeVarInt(0);
            writeDefinitionTail(invalidAvailability, "command_only");
            invalidAvailability.writeVarInt(0);
            invalidAvailability.writeVarInt(0);
            assertRejected(invalidAvailability, retainedSnapshot, "unknown availability name");

            FriendlyByteBuf tooManyDefinitions = new FriendlyByteBuf(Unpooled.buffer());
            tooManyDefinitions.writeVarInt(EquipmentSyncV2Codec.SCHEMA_VERSION);
            tooManyDefinitions.writeVarInt(EquipmentSyncV2Codec.MAX_DEFINITIONS + 1);
            assertRejected(tooManyDefinitions, retainedSnapshot, "definition count overflow");

            FriendlyByteBuf tooManyDistinct = new FriendlyByteBuf(Unpooled.buffer());
            tooManyDistinct.writeVarInt(EquipmentSyncV2Codec.SCHEMA_VERSION);
            int definitionCount = (EquipmentSyncV2Codec.MAX_DISTINCT_ATTRIBUTE_IDS
                    + EquipmentSyncV2Codec.MAX_STATS_PER_DEFINITION)
                    / EquipmentSyncV2Codec.MAX_STATS_PER_DEFINITION;
            tooManyDistinct.writeVarInt(definitionCount);
            int writtenAttributes = 0;
            for (int definitionIndex = 0; definitionIndex < definitionCount; definitionIndex++) {
                writeDefinitionStart(tooManyDistinct, id("sync_test", "distinct_" + definitionIndex));
                int pairCount = Math.min(EquipmentSyncV2Codec.MAX_STATS_PER_DEFINITION,
                        EquipmentSyncV2Codec.MAX_DISTINCT_ATTRIBUTE_IDS + 1 - writtenAttributes);
                tooManyDistinct.writeVarInt(pairCount);
                for (int pairIndex = 0; pairIndex < pairCount; pairIndex++) {
                    writeStat(tooManyDistinct, id("sync_distinct", "attribute_" + writtenAttributes++), 1F);
                }
                writeDefinitionEnd(tooManyDistinct);
            }
            tooManyDistinct.writeVarInt(0);
            tooManyDistinct.writeVarInt(0);
            assertRejected(tooManyDistinct, retainedSnapshot, "distinct attribute ID overflow");

            FriendlyByteBuf missingIndex = malformedHeader();
            writeDefinitionStart(missingIndex, id("sync_test", "missing_index"));
            missingIndex.writeVarInt(0);
            writeDefinitionEnd(missingIndex);
            missingIndex.writeVarInt(1);
            writeResourceLocation(missingIndex, ITEM);
            missingIndex.writeVarInt(9);
            writeResourceLocation(missingIndex, id("sync_test", "not_present"));
            assertRejected(missingIndex, retainedSnapshot, "missing index definition");

            ResourceLocation indexedDefinition = id("sync_test", "duplicate_index_definition");
            FriendlyByteBuf duplicateItemVariant = malformedHeader();
            writeDefinitionStart(duplicateItemVariant, indexedDefinition);
            duplicateItemVariant.writeVarInt(0);
            writeDefinitionEnd(duplicateItemVariant);
            duplicateItemVariant.writeVarInt(2);
            for (int i = 0; i < 2; i++) {
                writeResourceLocation(duplicateItemVariant, ITEM);
                duplicateItemVariant.writeVarInt(7);
                writeResourceLocation(duplicateItemVariant, indexedDefinition);
            }
            duplicateItemVariant.writeVarInt(0);
            assertRejected(duplicateItemVariant, retainedSnapshot, "duplicate item/variant index");

            FriendlyByteBuf duplicateLegacy = malformedHeader();
            writeDefinitionStart(duplicateLegacy, indexedDefinition);
            duplicateLegacy.writeVarInt(0);
            writeDefinitionEnd(duplicateLegacy);
            duplicateLegacy.writeVarInt(0);
            duplicateLegacy.writeVarInt(2);
            for (int i = 0; i < 2; i++) {
                duplicateLegacy.writeVarInt(707);
                writeResourceLocation(duplicateLegacy, indexedDefinition);
            }
            assertRejected(duplicateLegacy, retainedSnapshot, "duplicate legacy index");

            FriendlyByteBuf trailing = new FriendlyByteBuf(Unpooled.buffer());
            new S2CEquipDataSyncPacket(retainedSnapshot).encode(trailing);
            trailing.writeByte(0);
            assertRejectedWithError(trailing, retainedSnapshot, "trailing byte", "trailing");

            FriendlyByteBuf oversized = new FriendlyByteBuf(Unpooled.buffer(EquipmentSyncV2Codec.MAX_PAYLOAD_BYTES + 1));
            oversized.writeBytes(new byte[EquipmentSyncV2Codec.MAX_PAYLOAD_BYTES + 1]);
            assertRejected(oversized, retainedSnapshot, "payload byte overflow");
        } finally {
            ClientEquipData.install(original);
        }
        helper.succeed();
    }

    /** A new snapshot and the following clear notify once each; repeated clears stay silent. */
    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void equipmentSyncV2ClearNotifiesInstallListenersOnlyWhenStateChanges(GameTestHelper helper) {
        EquipDataSnapshot original = ClientEquipData.current();
        AtomicInteger notifications = new AtomicInteger();
        Runnable listener = notifications::incrementAndGet;
        EquipDefinition installedDefinition = definition("listener_snapshot", 8, 808,
                ShipAttributeLayout.current(), Map.of(CORE_HP, 8F));
        EquipDataSnapshot installed = snapshot(List.of(installedDefinition), Map.of(ITEM, Map.of(8, installedDefinition)),
                Map.of(808, installedDefinition));
        ClientEquipData.addInstallListener(listener);
        try {
            ClientEquipData.install(installed);
            assertInt(1, notifications.get(), "install listener count");

            ClientEquipData.clear();
            assertInt(2, notifications.get(), "clear listener count");

            ClientEquipData.clear();
            assertInt(2, notifications.get(), "repeated clear listener count");
        } finally {
            ClientEquipData.removeInstallListener(listener);
            ClientEquipData.install(original);
        }
        helper.succeed();
    }

    private static EquipDataSnapshot snapshot(List<EquipDefinition> definitions,
                                              Map<ResourceLocation, Map<Integer, EquipDefinition>> variants,
                                              Map<Integer, EquipDefinition> legacy) {
        Map<ResourceLocation, EquipDefinition> byId = new HashMap<>();
        definitions.forEach(definition -> byId.put(definition.id(), definition));
        return new EquipDataSnapshot(byId, variants, legacy);
    }

    private static EquipDefinition definition(String path, int variant, Integer legacyId, ShipAttributeLayout layout,
                                               Map<ResourceLocation, Float> stats) {
        return definition(path, variant, legacyId, layout, stats, Map.of());
    }

    private static EquipDefinition definition(String path, int variant, Integer legacyId, ShipAttributeLayout layout,
                                               Map<ResourceLocation, Float> stats,
                                               Map<ResourceLocation, ShipAttackEffect> attackEffects) {
        return definition(path, variant, legacyId, layout, stats, attackEffects, EquipAvailability.ANY);
    }

    private static EquipDefinition definition(String path, int variant, Integer legacyId, ShipAttributeLayout layout,
                                               Map<ResourceLocation, Float> stats,
                                               Map<ResourceLocation, ShipAttackEffect> attackEffects,
                                               EquipAvailability availability) {
        ShipAttributeValues.Builder values = ShipAttributeValues.builder(layout);
        stats.forEach(values::set);
        return new EquipDefinition(id("sync_test", path), ITEM, variant, 0, legacyId, values.build(),
                attackEffects, List.of("cannon"), 0, "grudge", 1, 2, 0, availability);
    }

    private static ShipAttributeLayout layoutWithOpaque() {
        Map<ResourceLocation, ShipAttributeType> types = canonicalTypes();
        types.put(OPAQUE, ShipAttributeType.builder().build());
        return ShipAttributeLayout.detached(types);
    }

    private static List<EquipDefinition> definitionsWithDistinctAttributes(int count) {
        List<EquipDefinition> definitions = new ArrayList<>();
        int written = 0;
        int definitionIndex = 0;
        while (written < count) {
            Map<ResourceLocation, ShipAttributeType> types = canonicalTypes();
            Map<ResourceLocation, Float> values = new HashMap<>();
            int pairCount = Math.min(EquipmentSyncV2Codec.MAX_STATS_PER_DEFINITION, count - written);
            for (int pairIndex = 0; pairIndex < pairCount; pairIndex++) {
                ResourceLocation attribute = id("sync_programmatic", "attribute_" + written++);
                types.put(attribute, ShipAttributeType.builder().build());
                values.put(attribute, 1F);
            }
            ShipAttributeLayout layout = ShipAttributeLayout.detached(types);
            definitions.add(definition("distinct_programmatic_" + definitionIndex, definitionIndex,
                    null, layout, values));
            definitionIndex++;
        }
        return definitions;
    }

    private static Map<ResourceLocation, ShipAttributeType> canonicalTypes() {
        ShipAttributeLayout canonical = ShipAttributeLayout.current();
        Map<ResourceLocation, ShipAttributeType> types = new HashMap<>();
        canonical.ids().forEach(id -> types.put(id, canonical.type(id)));
        return types;
    }

    private static S2CEquipDataSyncPacket roundTrip(EquipDataSnapshot snapshot) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            new S2CEquipDataSyncPacket(snapshot).encode(buffer);
            return new S2CEquipDataSyncPacket(buffer);
        } finally {
            buffer.release();
        }
    }

    private static byte[] encodedBytes(EquipDataSnapshot snapshot) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            new S2CEquipDataSyncPacket(snapshot).encode(buffer);
            byte[] bytes = new byte[buffer.readableBytes()];
            buffer.getBytes(buffer.readerIndex(), bytes);
            return bytes;
        } finally {
            buffer.release();
        }
    }

    private static void encodeProgrammatic(EquipDataSnapshot snapshot) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            new S2CEquipDataSyncPacket(snapshot).encode(buffer);
        } finally {
            buffer.release();
        }
    }

    private static FriendlyByteBuf malformedHeader() {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        buffer.writeVarInt(EquipmentSyncV2Codec.SCHEMA_VERSION);
        buffer.writeVarInt(1);
        return buffer;
    }

    private static void writeDefinitionStart(FriendlyByteBuf buffer, ResourceLocation definitionId) {
        writeResourceLocation(buffer, definitionId);
        writeResourceLocation(buffer, ITEM);
        buffer.writeVarInt(0);
        buffer.writeVarInt(0);
        buffer.writeBoolean(false);
    }

    private static void writeDefinitionEnd(FriendlyByteBuf buffer) {
        buffer.writeVarInt(0);
        writeDefinitionTail(buffer);
    }

    private static void writeDefinitionTail(FriendlyByteBuf buffer) {
        writeDefinitionTail(buffer, EquipAvailability.ANY.jsonName());
    }

    private static void writeDefinitionTail(FriendlyByteBuf buffer, String availabilityName) {
        buffer.writeVarInt(0);
        buffer.writeVarInt(0);
        buffer.writeUtf("grudge", 128);
        buffer.writeVarInt(0);
        buffer.writeVarInt(0);
        buffer.writeVarInt(0);
        buffer.writeUtf(availabilityName, 32);
    }

    private static void writeAttackEffect(FriendlyByteBuf buffer, ResourceLocation effectId,
                                          int amplifier, int durationTicks, int chancePercent) {
        writeResourceLocation(buffer, effectId);
        buffer.writeVarInt(amplifier);
        buffer.writeVarInt(durationTicks);
        buffer.writeVarInt(chancePercent);
    }

    private static void writeStat(FriendlyByteBuf buffer, ResourceLocation attribute, float value) {
        writeResourceLocation(buffer, attribute);
        buffer.writeFloat(value);
    }

    private static void writeResourceLocation(FriendlyByteBuf buffer, ResourceLocation value) {
        buffer.writeUtf(value.toString(), 256);
    }

    private static void assertRejected(FriendlyByteBuf buffer, EquipDataSnapshot expected, String caseName) {
        assertRejectedWithError(buffer, expected, caseName, null);
    }

    private static void assertRejectedWithError(FriendlyByteBuf buffer, EquipDataSnapshot expected,
                                                String caseName, String expectedErrorPart) {
        try {
            S2CEquipDataSyncPacket packet = new S2CEquipDataSyncPacket(buffer);
            if (packet.isValid() || packet.applyToClient()) {
                throw new AssertionError("Malformed v4 synchronization was accepted: " + caseName);
            }
            if (expectedErrorPart != null && (packet.decodeError() == null
                    || !packet.decodeError().contains(expectedErrorPart))) {
                throw new AssertionError(caseName + " was rejected for the wrong reason: " + packet.decodeError());
            }
            if (ClientEquipData.current() != expected) {
                throw new AssertionError("Malformed v4 synchronization replaced client state: " + caseName);
            }
        } finally {
            buffer.release();
        }
    }

    private static void expectIllegalArgument(Runnable action, String caseName) {
        try {
            action.run();
            throw new AssertionError("Expected v2 validation rejection: " + caseName);
        } catch (IllegalArgumentException expected) {
            // Expected rejection.
        }
    }

    private static void assertFloat(float expected, float actual, String name) {
        if (Float.compare(expected, actual) != 0) {
            throw new AssertionError(name + ": expected " + expected + " but was " + actual);
        }
    }

    private static void assertInt(int expected, int actual, String name) {
        if (expected != actual) {
            throw new AssertionError(name + ": expected " + expected + " but was " + actual);
        }
    }

    private static ResourceLocation id(String namespace, String path) {
        return ResourceLocation.fromNamespaceAndPath(namespace, path);
    }
}
