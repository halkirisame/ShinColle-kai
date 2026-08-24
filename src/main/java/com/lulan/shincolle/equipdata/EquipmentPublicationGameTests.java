package com.lulan.shincolle.equipdata;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lulan.shincolle.api.attribute.ShipAttributeLayout;
import com.lulan.shincolle.api.attribute.ShipAttributeType;
import com.lulan.shincolle.api.equipment.ShipAttackEffect;
import com.lulan.shincolle.reference.Reference;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Prevents the published schema and copyable examples from drifting away from the runtime loader. */
@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class EquipmentPublicationGameTests {

    private static final Path PUBLICATION_ROOT = Path.of(
            System.getProperty("shincolle.publicationRoot", ".")).toAbsolutePath().normalize();
    private static final Path SCHEMA = projectPath("docs", "schemas", "shincolle-equipment.schema.json");
    private static final Path DATAPACK_EXAMPLE = projectPath("examples", "equipment_datapack", "data",
            "shincolle_example", "equipment", "observation_spyglass.json");
    private static final Path ADDON_EXAMPLE = projectPath("examples", "java_addon", "src", "main", "resources",
            "data", "shincolle_example", "equipment", "sonar_module.json");
    private static final ResourceLocation CUSTOM_ATTRIBUTE = id("shincolle_example", "sonar_precision");
    private static final ResourceLocation GLOWING = id("minecraft", "glowing");

    private EquipmentPublicationGameTests() {
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void publishedSchemaMatchesRuntimeVocabulary(GameTestHelper helper) {
        JsonObject schema = readJson(SCHEMA);
        assertSetEquals(EquipmentJsonFormat.REQUIRED_FIELDS,
                strings(schema.getAsJsonArray("required")), "required fields");

        JsonObject properties = schema.getAsJsonObject("properties");
        assertSetEquals(EquipmentJsonFormat.TOP_LEVEL_FIELDS, properties.keySet(), "top-level fields");
        assertSetEquals(EquipmentJsonFormat.EQUIP_TYPES.keySet(), enumValues(properties, "equip_type"),
                "equip types");
        assertSetEquals(EquipmentJsonFormat.COMPATIBILITY,
                strings(properties.getAsJsonObject("compatible").getAsJsonObject("items")
                        .getAsJsonArray("enum")), "compatibility values");
        assertSetEquals(EquipmentJsonFormat.ENCHANT_TYPES, enumValues(properties, "enchant_type"),
                "enchant types");

        JsonObject developProperties = properties.getAsJsonObject("develop").getAsJsonObject("properties");
        assertSetEquals(EquipmentJsonFormat.DEVELOP_FIELDS, developProperties.keySet(), "develop fields");
        assertSetEquals(EquipmentJsonFormat.DEVELOP_MATERIALS,
                enumValues(developProperties, "material"), "develop materials");

        int maxProperties = properties.getAsJsonObject("stats").get("maxProperties").getAsInt();
        if (maxProperties != EquipmentJsonFormat.MAX_STATS) {
            throw new AssertionError("stats maxProperties: expected " + EquipmentJsonFormat.MAX_STATS
                    + " but was " + maxProperties);
        }
        int maxCompatible = properties.getAsJsonObject("compatible").get("maxItems").getAsInt();
        if (maxCompatible != EquipmentJsonFormat.MAX_COMPATIBLE) {
            throw new AssertionError("compatible maxItems: expected " + EquipmentJsonFormat.MAX_COMPATIBLE
                    + " but was " + maxCompatible);
        }
        JsonObject attackEffects = properties.getAsJsonObject("attack_effects");
        if (attackEffects.get("maxItems").getAsInt() != EquipmentJsonFormat.MAX_ATTACK_EFFECTS) {
            throw new AssertionError("attack_effects maxItems does not match the runtime limit");
        }
        JsonObject attackEffectItem = attackEffects.getAsJsonObject("items");
        assertSetEquals(EquipmentJsonFormat.ATTACK_EFFECT_FIELDS,
                attackEffectItem.getAsJsonObject("properties").keySet(), "attack effect fields");
        assertSetEquals(EquipmentJsonFormat.ATTACK_EFFECT_FIELDS,
                strings(attackEffectItem.getAsJsonArray("required")), "required attack effect fields");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void publishedExamplesParseThroughRuntimeLoader(GameTestHelper helper) {
        EquipDefinition datapack = EquipDataLoader.parse(
                id("shincolle_example", "observation_spyglass"), readJson(DATAPACK_EXAMPLE));
        if (!datapack.item().equals(id("minecraft", "spyglass")) || datapack.variant() != 0) {
            throw new AssertionError("JSON-only datapack example resolved to an unexpected item/variant");
        }
        ShipAttackEffect glowing = datapack.attackEffects().get(GLOWING);
        if (glowing == null || glowing.amplifier() != 0 || glowing.durationTicks() != 100
                || glowing.chancePercent() != 25) {
            throw new AssertionError("JSON-only datapack attack effect was not parsed");
        }

        ShipAttributeLayout addonLayout = layoutWithCustomAttribute();
        EquipDefinition addon = parseWithLayout(id("shincolle_example", "sonar_module"),
                readJson(ADDON_EXAMPLE), addonLayout);
        if (Float.compare(addon.stats().get(CUSTOM_ATTRIBUTE), 0.15F) != 0) {
            throw new AssertionError("Java addon example custom attribute was not parsed");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void runtimeRejectsVocabularyTyposAndInvalidIntegers(GameTestHelper helper) {
        for (Map.Entry<String, Integer> entry : EquipmentJsonFormat.EQUIP_TYPES.entrySet()) {
            JsonObject definition = minimalDefinition();
            definition.addProperty("equip_type", entry.getKey());
            EquipDefinition parsed = EquipDataLoader.parse(id("schema_test", entry.getKey()), definition);
            if (parsed.equipType() != entry.getValue()) {
                throw new AssertionError("equip_type mapping drift for " + entry.getKey());
            }
        }

        JsonObject unknownField = minimalDefinition();
        unknownField.addProperty("equip_typo", "radar_lo");
        assertRejected(unknownField, "unknown top-level field");

        JsonObject missingRequired = minimalDefinition();
        missingRequired.remove("item");
        assertRejected(missingRequired, "missing required field");

        JsonObject badCompatible = minimalDefinition();
        JsonArray compatible = new JsonArray();
        compatible.add("submarine");
        badCompatible.add("compatible", compatible);
        assertRejected(badCompatible, "unknown compatibility");

        JsonObject badEnchant = minimalDefinition();
        badEnchant.addProperty("enchant_type", "magic");
        assertRejected(badEnchant, "unknown enchant type");

        JsonObject badMaterial = minimalDefinition();
        JsonObject develop = new JsonObject();
        develop.addProperty("material", "iron");
        badMaterial.add("develop", develop);
        assertRejected(badMaterial, "unknown develop material");

        JsonObject fractionalVariant = minimalDefinition();
        fractionalVariant.addProperty("variant", 0.5D);
        assertRejected(fractionalVariant, "fractional variant");

        JsonObject negativeAmount = minimalDefinition();
        JsonObject negativeDevelop = new JsonObject();
        negativeDevelop.addProperty("amount", -1);
        negativeAmount.add("develop", negativeDevelop);
        assertRejected(negativeAmount, "negative develop amount");
        helper.succeed();
    }

    private static EquipDefinition parseWithLayout(ResourceLocation id, JsonObject json, ShipAttributeLayout layout) {
        JsonObject definitionWithoutStats = json.deepCopy();
        JsonObject stats = definitionWithoutStats.remove("stats").getAsJsonObject();
        EquipDefinition base = EquipDataLoader.parse(id, definitionWithoutStats);
        return new EquipDefinition(base.id(), base.item(), base.variant(), base.equipType(), base.legacyEquipId(),
                EquipDataLoader.parseStats(stats, layout), base.attackEffects(), base.compatible(), base.enchantType(),
                base.developMaterial(), base.developAmount(), base.rareMean(), base.rollType());
    }

    private static ShipAttributeLayout layoutWithCustomAttribute() {
        ShipAttributeLayout canonical = ShipAttributeLayout.current();
        Map<ResourceLocation, ShipAttributeType> types = new HashMap<>();
        for (ResourceLocation id : canonical.ids()) {
            types.put(id, canonical.type(id));
        }
        types.put(CUSTOM_ATTRIBUTE, ShipAttributeType.builder().build());
        return ShipAttributeLayout.detached(types);
    }

    private static JsonObject minimalDefinition() {
        JsonObject json = new JsonObject();
        json.addProperty("item", "minecraft:stick");
        json.addProperty("variant", 0);
        json.addProperty("equip_type", "radar_lo");
        return json;
    }

    private static void assertRejected(JsonObject json, String name) {
        try {
            EquipDataLoader.parse(id("schema_test", "invalid"), json);
            throw new AssertionError("Expected loader to reject " + name);
        } catch (IllegalArgumentException expected) {
            // Expected validation failure.
        }
    }

    private static Set<String> enumValues(JsonObject properties, String name) {
        return strings(properties.getAsJsonObject(name).getAsJsonArray("enum"));
    }

    private static Set<String> strings(JsonArray values) {
        Set<String> result = new HashSet<>();
        values.forEach(value -> result.add(value.getAsString()));
        return result;
    }

    private static void assertSetEquals(Set<String> expected, Set<String> actual, String name) {
        if (!expected.equals(actual)) {
            throw new AssertionError(name + ": expected " + expected + " but was " + actual);
        }
    }

    private static JsonObject readJson(Path path) {
        try {
            return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
        } catch (IOException | RuntimeException exception) {
            throw new AssertionError("Failed to read published JSON " + path, exception);
        }
    }

    private static Path projectPath(String first, String... more) {
        return PUBLICATION_ROOT.resolve(Path.of(first, more)).normalize();
    }

    private static ResourceLocation id(String namespace, String path) {
        return ResourceLocation.fromNamespaceAndPath(namespace, path);
    }
}
