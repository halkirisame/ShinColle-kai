package com.lulan.shincolle.gametest;

import com.lulan.shincolle.api.attribute.CoreShipAttributes;
import com.lulan.shincolle.api.attribute.ShipAttributeCombineContext;
import com.lulan.shincolle.api.attribute.ShipAttributeDisplayFormat;
import com.lulan.shincolle.api.attribute.ShipAttributeEnchantRule;
import com.lulan.shincolle.api.attribute.ShipAttributeLayer;
import com.lulan.shincolle.api.attribute.ShipAttributeLayout;
import com.lulan.shincolle.api.attribute.ShipAttributeScaleGroup;
import com.lulan.shincolle.api.attribute.ShipAttributeType;
import com.lulan.shincolle.api.attribute.ShipAttributeValues;
import com.lulan.shincolle.attribute.LegacyShipAttributeBridge;
import com.lulan.shincolle.init.ModShipAttributes;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;
import com.lulan.shincolle.reference.unitclass.Attrs;
import com.lulan.shincolle.reference.unitclass.AttrsAdv;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.IForgeRegistry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Golden tests for the behavior-neutral first stage of the extensible ship-attribute API.
 */
@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ShipAttributeFoundationGameTests {

    private static final float EPSILON = 0.00001F;

    private ShipAttributeFoundationGameTests() {
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void shipAttributeRegistryContainsCoreTwentyOne(GameTestHelper helper) {
        IForgeRegistry<ShipAttributeType> registry = ModShipAttributes.REGISTRY.get();
        List<ResourceLocation> coreInRegistry = registry.getKeys().stream()
                .filter(id -> id.getNamespace().equals(Reference.MOD_ID))
                .filter(CoreShipAttributes.LEGACY_ORDER::contains)
                .sorted()
                .toList();
        List<ResourceLocation> expected = CoreShipAttributes.LEGACY_ORDER.stream().sorted().toList();

        assertEquals(expected, coreInRegistry, "registered core attribute IDs");
        assertEquals(21, CoreShipAttributes.LEGACY_ORDER.size(), "core attribute count");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void legacyAttributeIndexesMatchStableIds(GameTestHelper helper) {
        int[] legacyIndexes = {
                ID.Attrs.HP, ID.Attrs.ATK_L, ID.Attrs.ATK_H, ID.Attrs.ATK_AL, ID.Attrs.ATK_AH,
                ID.Attrs.DEF, ID.Attrs.SPD, ID.Attrs.MOV, ID.Attrs.HIT, ID.Attrs.CRI,
                ID.Attrs.DHIT, ID.Attrs.THIT, ID.Attrs.MISS, ID.Attrs.AA, ID.Attrs.ASM,
                ID.Attrs.DODGE, ID.Attrs.XP, ID.Attrs.GRUDGE, ID.Attrs.AMMO, ID.Attrs.HPRES,
                ID.Attrs.KB
        };

        for (int i = 0; i < legacyIndexes.length; i++) {
            assertEquals(i, legacyIndexes[i], "ID.Attrs index " + i);
            ResourceLocation id = CoreShipAttributes.LEGACY_ORDER.get(i);
            assertEquals(id, LegacyShipAttributeBridge.idFromLegacyIndex(i), "bridge ID " + i);
            assertEquals(i, LegacyShipAttributeBridge.legacyIndex(id), "bridge index " + id);
        }
        assertEquals(-1, LegacyShipAttributeBridge.legacyIndex(id("addon", "unknown")),
                "unknown legacy index");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void shipAttributeLayerDefaultsMatchLegacyArrays(GameTestHelper helper) {
        ShipAttributeLayout layout = ShipAttributeLayout.current();

        assertFloatArrayEquals(Attrs.getResetRawValue(), legacyDefaults(layout, ShipAttributeLayer.RAW),
                "raw defaults");
        assertFloatArrayEquals(new float[Attrs.AttrsLength], legacyDefaults(layout, ShipAttributeLayer.EQUIPMENT),
                "equipment defaults");
        assertFloatArrayEquals(new float[Attrs.AttrsLength], legacyDefaults(layout, ShipAttributeLayer.POTION),
                "potion defaults");
        assertFloatArrayEquals(AttrsAdv.getResetMoraleValue(), legacyDefaults(layout, ShipAttributeLayer.MORALE),
                "morale defaults");
        assertFloatArrayEquals(AttrsAdv.getResetFormationValue(), legacyDefaults(layout, ShipAttributeLayer.FORMATION),
                "formation defaults");
        assertThrows(IllegalArgumentException.class,
                () -> ShipAttributeValues.defaults(layout, ShipAttributeLayer.BUFFED), "buffed defaults");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void legacyAttributeArrayRoundTripsExactly(GameTestHelper helper) {
        float[] source = new float[Attrs.AttrsLength];
        for (int i = 0; i < source.length; i++) {
            source[i] = i * 1.25F - 4F;
        }

        ShipAttributeValues values = LegacyShipAttributeBridge.fromLegacyArray(source);
        float[] roundTrip = LegacyShipAttributeBridge.toLegacyArray(values);
        assertFloatArrayEquals(source, roundTrip, "legacy round trip");

        source[0] = 999F;
        assertFloatEquals(-4F, values.get(CoreShipAttributes.HP), "legacy input defensive copy");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void legacyBridgeIgnoresDetachedCustomAttribute(GameTestHelper helper) {
        Map<ResourceLocation, ShipAttributeType> entries = coreEntries();
        ResourceLocation customId = id("test_addon", "sonar_precision");
        entries.put(customId, ShipAttributeType.builder().build());
        ShipAttributeLayout layout = ShipAttributeLayout.detached(entries);

        float[] legacy = new float[Attrs.AttrsLength];
        legacy[ID.Attrs.HIT] = 7.5F;
        ShipAttributeValues values = LegacyShipAttributeBridge.fromLegacyArray(legacy, layout)
                .toBuilder()
                .set(customId, 99F)
                .build();

        assertFloatArrayEquals(legacy, LegacyShipAttributeBridge.toLegacyArray(values), "custom ignored by bridge");
        assertFloatEquals(99F, values.get(customId), "custom value retained");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void shipAttributeLayoutIsDeterministic(GameTestHelper helper) {
        ResourceLocation zetaA = id("zeta", "a");
        ResourceLocation alphaZ = id("alpha", "z");
        ResourceLocation alphaA = id("alpha", "a");
        ShipAttributeType typeZetaA = ShipAttributeType.builder().build();
        ShipAttributeType typeAlphaZ = ShipAttributeType.builder().build();
        ShipAttributeType typeA = ShipAttributeType.builder().build();

        Map<ResourceLocation, ShipAttributeType> input = new LinkedHashMap<>();
        input.put(zetaA, typeZetaA);
        input.put(alphaZ, typeAlphaZ);
        input.put(alphaA, typeA);
        ShipAttributeLayout layout = ShipAttributeLayout.detached(input);

        assertEquals(List.of(alphaA, alphaZ, zetaA), layout.ids(), "namespaced-string sorted layout IDs");
        assertEquals(0, layout.indexOf(alphaA), "alpha:a index");
        assertEquals(2, layout.indexOf(typeZetaA), "zeta:a type identity index");
        assertEquals(-1, layout.indexOf(id("missing", "attribute")), "unknown ID index");

        input.clear();
        assertEquals(3, layout.size(), "layout detached from input map");
        assertThrows(UnsupportedOperationException.class, () -> layout.ids().add(zetaA), "layout IDs immutable");

        Map<ResourceLocation, ShipAttributeType> duplicateType = new HashMap<>();
        duplicateType.put(alphaA, typeA);
        duplicateType.put(alphaZ, typeA);
        assertThrows(IllegalArgumentException.class,
                () -> ShipAttributeLayout.detached(duplicateType), "duplicate type identity");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void shipAttributeValuesAreDeeplyImmutable(GameTestHelper helper) {
        ShipAttributeLayout layout = ShipAttributeLayout.current();
        ShipAttributeValues.Builder builder = ShipAttributeValues.builder(layout)
                .set(CoreShipAttributes.HP, 20F)
                .add(CoreShipAttributes.HP, 5F);
        ShipAttributeValues first = builder.build();
        builder.set(CoreShipAttributes.HP, 100F);
        ShipAttributeValues second = builder.build();

        assertFloatEquals(25F, first.get(CoreShipAttributes.HP), "built value detached from builder");
        assertFloatEquals(100F, second.get(CoreShipAttributes.HP), "builder remains usable");
        assertThrows(UnsupportedOperationException.class,
                () -> first.asMap().put(CoreShipAttributes.HP, 0F), "value map immutable");

        ShipAttributeValues copied = first.toBuilder().add(CoreShipAttributes.HP, 1F).build();
        assertFloatEquals(25F, first.get(CoreShipAttributes.HP), "toBuilder does not mutate source");
        assertFloatEquals(26F, copied.get(CoreShipAttributes.HP), "toBuilder result");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void shipAttributeFoundationRejectsInvalidInput(GameTestHelper helper) {
        ShipAttributeLayout layout = ShipAttributeLayout.current();
        ResourceLocation unknown = id("missing", "attribute");

        assertThrows(IllegalArgumentException.class,
                () -> ShipAttributeValues.builder(layout).set(unknown, 1F), "unknown ID set");
        assertThrows(IllegalArgumentException.class,
                () -> ShipAttributeValues.zero(layout).get(unknown), "unknown ID get");
        assertThrows(IllegalArgumentException.class,
                () -> LegacyShipAttributeBridge.fromLegacyArray(new float[20], layout), "short legacy array");
        assertThrows(IllegalArgumentException.class,
                () -> LegacyShipAttributeBridge.fromLegacyArray(new float[22], layout), "long legacy array");
        assertThrows(IllegalArgumentException.class,
                () -> ShipAttributeValues.builder(layout).set(CoreShipAttributes.HP, Float.NaN), "NaN value");
        assertThrows(IllegalArgumentException.class,
                () -> ShipAttributeValues.builder(layout).set(CoreShipAttributes.HP, Float.POSITIVE_INFINITY),
                "infinite value");
        assertThrows(IllegalArgumentException.class,
                () -> ShipAttributeType.builder().minimum(2F).maximum(1F).build(), "reversed bounds");
        assertThrows(IllegalArgumentException.class,
                () -> ShipAttributeType.builder().minimum(Float.NaN), "NaN bound");
        assertThrows(IllegalArgumentException.class,
                () -> ShipAttributeType.builder().defaultValue(ShipAttributeLayer.BUFFED, 0F), "buffed default");
        assertThrows(IllegalArgumentException.class,
                () -> new ShipAttributeCombineContext(0F, 0F, 0F, 0F, 0F, Float.NaN), "NaN context");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void coreAttributeCombinersAndBoundsMatchLegacy(GameTestHelper helper) {
        ShipAttributeLayout layout = ShipAttributeLayout.current();
        ShipAttributeCombineContext context = new ShipAttributeCombineContext(2.5F, -0.4F, 1.2F, 0.3F, 0.8F, 1.4F);

        float scaledAll = 2.5F - 0.4F + (1.2F + 0.3F + 0.8F) * 1.4F;
        float scaledNoFormation = 2.5F - 0.4F + (1.2F + 0.3F) * 1.4F;
        float multiplied = (2.5F - 0.4F + 0.3F * 1.4F) * 1.2F * 0.8F;
        float multipliedHeavy = (2.5F - 0.4F + 0.3F * 3F * 1.4F) * 1.2F * 0.8F;
        float defense = (2.5F - 0.4F + (1.2F + 0.3F) * 1.4F) * 0.8F;
        float additive = 2.5F - 0.4F + 1.2F + 0.3F + 0.8F;

        assertCombined(layout, CoreShipAttributes.HP, context, scaledAll);
        assertCombined(layout, CoreShipAttributes.HIT, context, scaledAll);
        assertCombined(layout, CoreShipAttributes.MOV, context, scaledNoFormation);
        assertCombined(layout, CoreShipAttributes.ATK_L, context, multiplied);
        assertCombined(layout, CoreShipAttributes.ATK_H, context, multipliedHeavy);
        assertCombined(layout, CoreShipAttributes.ATK_AL, context, multiplied);
        assertCombined(layout, CoreShipAttributes.ATK_AH, context, multipliedHeavy);
        assertCombined(layout, CoreShipAttributes.SPD, context, multiplied);
        assertCombined(layout, CoreShipAttributes.DEF, context, defense);

        for (ResourceLocation id : List.of(CoreShipAttributes.CRI, CoreShipAttributes.DHIT,
                CoreShipAttributes.THIT, CoreShipAttributes.MISS, CoreShipAttributes.AA, CoreShipAttributes.ASM)) {
            assertCombined(layout, id, context, multiplied);
        }
        for (ResourceLocation id : List.of(CoreShipAttributes.DODGE, CoreShipAttributes.XP,
                CoreShipAttributes.GRUDGE, CoreShipAttributes.AMMO, CoreShipAttributes.HPRES, CoreShipAttributes.KB)) {
            assertCombined(layout, id, context, additive);
        }

        ShipAttributeCombineContext negative = new ShipAttributeCombineContext(-10F, 0F, 1F, 0F, 1F, 1F);
        for (ResourceLocation id : CoreShipAttributes.LEGACY_ORDER) {
            float expectedMinimum = minimumFor(id);
            assertCombined(layout, id, negative, expectedMinimum);
        }

        ShipAttributeType hp = layout.type(CoreShipAttributes.HP);
        if (hp == null) {
            throw new AssertionError("Missing HP ship attribute type");
        }
        assertFloatEquals(-8F, hp.combineUnbounded(negative), "HP before legacy config maximum and minimum");
        assertFloatEquals(1F, hp.constrain(0F), "HP minimum after legacy config maximum");

        ShipAttributeType capped = ShipAttributeType.builder()
                .maximum(2F)
                .build();
        assertFloatEquals(2F, capped.combine(new ShipAttributeCombineContext(3F, 0F, 0F, 0F, 0F, 1F)),
                "custom maximum");
        ShipAttributeType invalidCombiner = ShipAttributeType.builder().combiner(ignored -> Float.NaN).build();
        assertThrows(IllegalArgumentException.class,
                () -> invalidCombiner.combine(new ShipAttributeCombineContext(0F, 0F, 0F, 0F, 0F, 1F)),
                "non-finite combiner result");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void coreAttributePoliciesMatchLegacy(GameTestHelper helper) {
        ShipAttributeLayout layout = ShipAttributeLayout.current();
        Map<ResourceLocation, ShipAttributeScaleGroup> scaled = Map.ofEntries(
                Map.entry(CoreShipAttributes.HP, ShipAttributeScaleGroup.HP),
                Map.entry(CoreShipAttributes.ATK_L, ShipAttributeScaleGroup.ATK),
                Map.entry(CoreShipAttributes.ATK_H, ShipAttributeScaleGroup.ATK),
                Map.entry(CoreShipAttributes.ATK_AL, ShipAttributeScaleGroup.ATK),
                Map.entry(CoreShipAttributes.ATK_AH, ShipAttributeScaleGroup.ATK),
                Map.entry(CoreShipAttributes.DEF, ShipAttributeScaleGroup.DEF),
                Map.entry(CoreShipAttributes.SPD, ShipAttributeScaleGroup.SPD),
                Map.entry(CoreShipAttributes.MOV, ShipAttributeScaleGroup.MOV),
                Map.entry(CoreShipAttributes.HIT, ShipAttributeScaleGroup.HIT));
        Map<ResourceLocation, ShipAttributeEnchantRule> enchantRules = Map.ofEntries(
                Map.entry(CoreShipAttributes.HP, ShipAttributeEnchantRule.MULTIPLY),
                Map.entry(CoreShipAttributes.ATK_L, ShipAttributeEnchantRule.WEAPON_MULTIPLY),
                Map.entry(CoreShipAttributes.ATK_H, ShipAttributeEnchantRule.WEAPON_MULTIPLY),
                Map.entry(CoreShipAttributes.ATK_AL, ShipAttributeEnchantRule.WEAPON_MULTIPLY),
                Map.entry(CoreShipAttributes.ATK_AH, ShipAttributeEnchantRule.WEAPON_MULTIPLY),
                Map.entry(CoreShipAttributes.DEF, ShipAttributeEnchantRule.ARMOR_MULTIPLY),
                Map.entry(CoreShipAttributes.SPD, ShipAttributeEnchantRule.MULTIPLY),
                Map.entry(CoreShipAttributes.MOV, ShipAttributeEnchantRule.SIGNED_MULTIPLY),
                Map.entry(CoreShipAttributes.HIT, ShipAttributeEnchantRule.MULTIPLY),
                Map.entry(CoreShipAttributes.CRI, ShipAttributeEnchantRule.MULTIPLY),
                Map.entry(CoreShipAttributes.DHIT, ShipAttributeEnchantRule.MULTIPLY),
                Map.entry(CoreShipAttributes.THIT, ShipAttributeEnchantRule.MULTIPLY),
                Map.entry(CoreShipAttributes.MISS, ShipAttributeEnchantRule.MULTIPLY),
                Map.entry(CoreShipAttributes.AA, ShipAttributeEnchantRule.MULTIPLY),
                Map.entry(CoreShipAttributes.ASM, ShipAttributeEnchantRule.MULTIPLY),
                Map.entry(CoreShipAttributes.DODGE, ShipAttributeEnchantRule.SIGNED_MULTIPLY),
                Map.entry(CoreShipAttributes.XP, ShipAttributeEnchantRule.WEAPON_ADDITIVE),
                Map.entry(CoreShipAttributes.GRUDGE, ShipAttributeEnchantRule.NON_WEAPON_ADDITIVE),
                Map.entry(CoreShipAttributes.AMMO, ShipAttributeEnchantRule.WEAPON_ADDITIVE),
                Map.entry(CoreShipAttributes.HPRES, ShipAttributeEnchantRule.NON_WEAPON_ADDITIVE),
                Map.entry(CoreShipAttributes.KB, ShipAttributeEnchantRule.NON_WEAPON_ADDITIVE));

        for (ResourceLocation id : CoreShipAttributes.LEGACY_ORDER) {
            ShipAttributeType type = layout.type(id);
            if (type == null) {
                throw new AssertionError("Missing core ship attribute type " + id);
            }
            assertEquals(scaled.getOrDefault(id, ShipAttributeScaleGroup.NONE), type.scaleGroup(),
                    "scale group " + id);
            assertEquals(enchantRules.get(id), type.enchantRule(), "enchant rule " + id);
            assertEquals("ship_attribute." + id.getNamespace() + "." + id.getPath(),
                    type.translationKey(id), "translation key " + id);
        }
        assertEquals(ShipAttributeDisplayFormat.INTEGER, layout.type(CoreShipAttributes.HP).displayFormat(),
                "HP display format");
        assertEquals(ShipAttributeDisplayFormat.PERCENT, layout.type(CoreShipAttributes.DEF).displayFormat(),
                "DEF display format");
        helper.succeed();
    }

    private static Map<ResourceLocation, ShipAttributeType> coreEntries() {
        IForgeRegistry<ShipAttributeType> registry = ModShipAttributes.REGISTRY.get();
        Map<ResourceLocation, ShipAttributeType> entries = new HashMap<>();
        for (ResourceLocation id : CoreShipAttributes.LEGACY_ORDER) {
            ShipAttributeType type = registry.getValue(id);
            if (type == null) {
                throw new AssertionError("Missing core ship attribute " + id);
            }
            entries.put(id, type);
        }
        return entries;
    }

    private static ResourceLocation id(String namespace, String path) {
        return ResourceLocation.fromNamespaceAndPath(namespace, path);
    }

    private static float[] legacyDefaults(ShipAttributeLayout layout, ShipAttributeLayer layer) {
        return LegacyShipAttributeBridge.toLegacyArray(ShipAttributeValues.defaults(layout, layer));
    }

    private static void assertCombined(ShipAttributeLayout layout, ResourceLocation id,
                                       ShipAttributeCombineContext context, float expected) {
        ShipAttributeType type = layout.type(id);
        if (type == null) {
            throw new AssertionError("Missing ship attribute type " + id);
        }
        assertFloatEquals(expected, type.combine(context), "combined " + id);
    }

    private static float minimumFor(ResourceLocation id) {
        if (id.equals(CoreShipAttributes.HP) || id.equals(CoreShipAttributes.ATK_L)
                || id.equals(CoreShipAttributes.ATK_H) || id.equals(CoreShipAttributes.ATK_AL)
                || id.equals(CoreShipAttributes.ATK_AH) || id.equals(CoreShipAttributes.HIT)) {
            return 1F;
        }
        if (id.equals(CoreShipAttributes.SPD)) {
            return 0.2F;
        }
        return 0F;
    }

    private static void assertFloatArrayEquals(float[] expected, float[] actual, String message) {
        assertEquals(expected.length, actual.length, message + " length");
        for (int i = 0; i < expected.length; i++) {
            assertFloatEquals(expected[i], actual[i], message + "[" + i + "]");
        }
    }

    private static void assertFloatEquals(float expected, float actual, String message) {
        if (Math.abs(expected - actual) > EPSILON) {
            throw new AssertionError(message + ": expected " + expected + " but was " + actual);
        }
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (!expected.equals(actual)) {
            throw new AssertionError(message + ": expected " + expected + " but was " + actual);
        }
    }

    private static void assertThrows(Class<? extends Throwable> expected, Runnable action, String message) {
        List<Throwable> thrown = new ArrayList<>();
        try {
            action.run();
        } catch (Throwable error) {
            thrown.add(error);
        }
        if (thrown.size() != 1 || !expected.isInstance(thrown.get(0))) {
            throw new AssertionError(message + ": expected " + expected.getSimpleName()
                    + " but got " + (thrown.isEmpty() ? "nothing" : thrown.get(0)),
                    thrown.isEmpty() ? null : thrown.get(0));
        }
    }
}
