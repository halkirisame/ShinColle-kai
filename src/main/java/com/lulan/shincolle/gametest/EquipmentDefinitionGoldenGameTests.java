package com.lulan.shincolle.gametest;

import com.lulan.shincolle.attribute.LegacyShipAttributeBridge;
import com.lulan.shincolle.equipdata.EquipDataRegistry;
import com.lulan.shincolle.equipdata.EquipDefinition;
import com.lulan.shincolle.reference.Reference;
import com.lulan.shincolle.reference.unitclass.Attrs;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

/**
 * Locks the pre-dynamic definition data so the Stage 3 migration cannot change
 * any existing equipment values or metadata unnoticed.
 */
@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class EquipmentDefinitionGoldenGameTests {

    private static final int EXPECTED_DEFINITION_COUNT = 94;
    private static final String EXPECTED_STATS_DIGEST =
            "33997abbfdd917c2328063742f3a54d18304dfb69a8cb5ebea2dc33645922586";
    private static final String EXPECTED_METADATA_DIGEST =
            "0478088f1d3784d07131ff5de2d47a5cdb8ce304ae831e1c99290c974ace3026";

    /**
     * Namespace the goldens above were computed under, before the 2026-08-25 rename to
     * {@code shincolle_kai}. Ids are normalised back to it so these digests keep locking
     * equipment <em>values and metadata</em> instead of the mod identity — otherwise every
     * future rename would silently invalidate the guard and force a blind golden update.
     */
    private static final String GOLDEN_NAMESPACE = "shincolle";

    private EquipmentDefinitionGoldenGameTests() {
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void existingEquipmentDefinitionsMatchGolden(GameTestHelper helper) {
        List<EquipDefinition> definitions = EquipDataRegistry.server().all().stream()
                .sorted(Comparator.comparing(definition -> definition.id().toString()))
                .toList();

        if (definitions.size() != EXPECTED_DEFINITION_COUNT) {
            throw new AssertionError("Expected " + EXPECTED_DEFINITION_COUNT + " equipment definitions but found "
                    + definitions.size());
        }

        assertDigest(EXPECTED_STATS_DIGEST, canonicalStats(definitions), "stat vectors");
        assertDigest(EXPECTED_METADATA_DIGEST, canonicalMetadata(definitions), "metadata");
        helper.succeed();
    }

    private static String canonicalStats(List<EquipDefinition> definitions) {
        return definitions.stream()
                .map(EquipmentDefinitionGoldenGameTests::canonicalStats)
                .collect(java.util.stream.Collectors.joining("\n"));
    }

    private static String canonicalStats(EquipDefinition definition) {
        float[] stats = LegacyShipAttributeBridge.toLegacyArray(definition.stats());
        if (stats.length != Attrs.AttrsLength) {
            throw new AssertionError("Definition " + definition.id() + " has " + stats.length
                    + " stats; expected " + Attrs.AttrsLength);
        }

        StringBuilder row = new StringBuilder(canonicalId(definition.id())).append('=');
        for (int index = 0; index < Attrs.AttrsLength; index++) {
            if (index > 0) {
                row.append(',');
            }
            row.append(Float.floatToIntBits(stats[index]));
        }
        return row.toString();
    }

    private static String canonicalMetadata(List<EquipDefinition> definitions) {
        return definitions.stream()
                .map(EquipmentDefinitionGoldenGameTests::canonicalMetadata)
                .collect(java.util.stream.Collectors.joining("\n"));
    }

    private static String canonicalMetadata(EquipDefinition definition) {
        String compatible = definition.compatible().stream().sorted().collect(java.util.stream.Collectors.joining(","));
        String legacyId = definition.legacyEquipId() == null ? "-" : definition.legacyEquipId().toString();
        return String.join("|", canonicalId(definition.id()), canonicalId(definition.item()),
                Integer.toString(definition.variant()), Integer.toString(definition.equipType()), legacyId, compatible,
                Integer.toString(definition.enchantType()), definition.developMaterial(),
                Integer.toString(definition.developAmount()), Integer.toString(definition.rareMean()),
                Integer.toString(definition.rollType()));
    }

    /** Maps the mod's own namespace onto the historical one; other namespaces pass through. */
    private static String canonicalId(ResourceLocation id) {
        return Reference.MOD_ID.equals(id.getNamespace())
                ? GOLDEN_NAMESPACE + ':' + id.getPath()
                : id.toString();
    }

    private static void assertDigest(String expected, String canonical, String subject) {
        String actual = sha256(canonical);
        if (!expected.equals(actual)) {
            throw new AssertionError("Unexpected equipment " + subject + " SHA-256: expected " + expected
                    + " but was " + actual);
        }
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new AssertionError("SHA-256 is unavailable", exception);
        }
    }
}
