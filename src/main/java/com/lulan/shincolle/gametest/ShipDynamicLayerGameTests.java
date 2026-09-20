package com.lulan.shincolle.gametest;

import com.lulan.shincolle.api.attribute.CoreShipAttributes;
import com.lulan.shincolle.api.attribute.ShipAttributeCombineContext;
import com.lulan.shincolle.api.attribute.ShipAttributeCombiners;
import com.lulan.shincolle.api.attribute.ShipAttributeLayer;
import com.lulan.shincolle.api.attribute.ShipAttributeLayout;
import com.lulan.shincolle.api.attribute.ShipAttributeScaleGroup;
import com.lulan.shincolle.api.attribute.ShipAttributeType;
import com.lulan.shincolle.api.attribute.ShipAttributeValues;
import com.lulan.shincolle.attribute.ShipAttributeLayerEngine;
import com.lulan.shincolle.attribute.ShipAttributeLayerState;
import com.lulan.shincolle.equip.ShipEquipmentAttributeMath;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;
import com.lulan.shincolle.reference.unitclass.Attrs;
import com.lulan.shincolle.reference.unitclass.AttrsAdv;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Regression tests for the Stage 5A dynamic ship-attribute layers. */
@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ShipDynamicLayerGameTests {

    private static final float EPSILON = 0.00001F;

    private ShipDynamicLayerGameTests() {
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void detachedCustomLayersAreImmutableAndCopyIndependently(GameTestHelper helper) {
        ResourceLocation customId = id("test_addon", "sonar_precision");
        ShipAttributeType type = ShipAttributeType.builder()
                .defaultValue(ShipAttributeLayer.RAW, 2F)
                .defaultValue(ShipAttributeLayer.MORALE, 1F)
                .defaultValue(ShipAttributeLayer.FORMATION, 1F)
                .build();
        ShipAttributeLayout layout = ShipAttributeLayout.detached(Map.of(customId, type));
        ShipAttributeLayerState state = new ShipAttributeLayerState(layout);

        for (ShipAttributeLayer layer : ShipAttributeLayer.values()) {
            float value = layer.ordinal() + 3F;
            state.set(layer, ShipAttributeValues.builder(layout).set(customId, value).build());
            assertFloatEquals(value, state.get(layer).get(customId), "stored layer " + layer);
        }
        assertThrows(UnsupportedOperationException.class,
                () -> state.get(ShipAttributeLayer.RAW).asMap().put(customId, 0F),
                "layer snapshot map immutable");

        ShipAttributeLayerState copy = state.copy();
        state.set(ShipAttributeLayer.EQUIPMENT,
                ShipAttributeValues.builder(layout).set(customId, 99F).build());
        assertFloatEquals(4F, copy.get(ShipAttributeLayer.EQUIPMENT).get(customId),
                "copied layer remains independent");

        state.reset(ShipAttributeLayer.EQUIPMENT);
        assertFloatEquals(0F, state.get(ShipAttributeLayer.EQUIPMENT).get(customId),
                "reset uses equipment default");
        state.reset(ShipAttributeLayer.BUFFED);
        assertFloatEquals(2F, state.get(ShipAttributeLayer.BUFFED).get(customId),
                "buffed reset uses raw default");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void customCombinersBoundsAndFailuresAreIsolated(GameTestHelper helper) {
        ResourceLocation additiveId = id("test_addon", "additive");
        ResourceLocation multipliedId = id("test_addon", "multiplied");
        ResourceLocation customId = id("test_addon", "custom_combiner");
        ResourceLocation brokenId = id("test_addon", "broken");
        ResourceLocation legacyLimitId = id("test_addon", "legacy_limit_order");
        Map<ResourceLocation, ShipAttributeType> types = new LinkedHashMap<>();
        types.put(additiveId, ShipAttributeType.builder().minimum(0F).maximum(12F).build());
        types.put(multipliedId, ShipAttributeType.builder()
                .combiner(ShipAttributeCombiners.multiplicative(2F))
                .scaleGroup(ShipAttributeScaleGroup.ATK)
                .build());
        types.put(customId, ShipAttributeType.builder()
                .combiner(context -> context.raw() - context.equipment() + context.formation())
                .build());
        types.put(brokenId, ShipAttributeType.builder()
                .defaultValue(ShipAttributeLayer.RAW, 7F)
                .minimum(1F)
                .combiner(context -> Float.NaN)
                .build());
        types.put(legacyLimitId, ShipAttributeType.builder().minimum(5F).build());
        ShipAttributeLayout layout = ShipAttributeLayout.detached(types);

        ShipAttributeValues raw = values(layout, Map.of(
                additiveId, 2F, multipliedId, 3F, customId, 9F, brokenId, 4F));
        raw = raw.toBuilder().set(legacyLimitId, 10F).build();
        ShipAttributeValues equipment = values(layout, Map.of(
                additiveId, 3F, multipliedId, 1F, customId, 2F));
        ShipAttributeValues morale = values(layout, Map.of(additiveId, 4F, multipliedId, 1.5F));
        ShipAttributeValues potion = values(layout, Map.of(additiveId, 5F, multipliedId, 2F));
        ShipAttributeValues formation = values(layout, Map.of(
                additiveId, 6F, multipliedId, 2F, customId, 1F));
        List<ResourceLocation> failures = new ArrayList<>();

        ShipAttributeValues result = ShipAttributeLayerEngine.combine(raw, equipment, morale, potion, formation,
                group -> group == ShipAttributeScaleGroup.ATK ? 3F : 1F,
                id -> id.equals(legacyLimitId) ? 2D : -1D,
                (id, error) -> failures.add(id));

        assertFloatEquals(12F, result.get(additiveId), "custom maximum after additive combine");
        assertFloatEquals(48F, result.get(multipliedId), "custom multiplicative combine and scale");
        assertFloatEquals(8F, result.get(customId), "custom combiner");
        assertFloatEquals(7F, result.get(brokenId), "broken combiner safe raw default");
        assertFloatEquals(5F, result.get(legacyLimitId), "legacy maximum applies before type minimum");
        assertEquals(List.of(brokenId), failures, "only broken attribute isolated");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void coreLayerEngineMatchesRegisteredGolden(GameTestHelper helper) {
        ShipAttributeLayout layout = ShipAttributeLayout.current();
        ShipAttributeValues.Builder raw = ShipAttributeValues.builder(layout);
        ShipAttributeValues.Builder equipment = ShipAttributeValues.builder(layout);
        ShipAttributeValues.Builder morale = ShipAttributeValues.builder(layout);
        ShipAttributeValues.Builder potion = ShipAttributeValues.builder(layout);
        ShipAttributeValues.Builder formation = ShipAttributeValues.builder(layout);
        for (int i = 0; i < CoreShipAttributes.LEGACY_ORDER.size(); i++) {
            ResourceLocation id = CoreShipAttributes.LEGACY_ORDER.get(i);
            raw.set(id, 20F + i);
            equipment.set(id, 1F + i * 0.1F);
            morale.set(id, 1.2F);
            potion.set(id, 0.25F);
            formation.set(id, 1.1F);
        }

        ShipAttributeValues rawValues = raw.build();
        ShipAttributeValues equipmentValues = equipment.build();
        ShipAttributeValues moraleValues = morale.build();
        ShipAttributeValues potionValues = potion.build();
        ShipAttributeValues formationValues = formation.build();
        ShipAttributeValues combined = ShipAttributeLayerEngine.combine(rawValues, equipmentValues,
                moraleValues, potionValues, formationValues,
                group -> group == ShipAttributeScaleGroup.NONE ? 1F : 1.75F,
                ignored -> -1D,
                (id, error) -> {
                    throw new AssertionError("Core attribute failed to combine: " + id, error);
                });

        for (ResourceLocation id : CoreShipAttributes.LEGACY_ORDER) {
            ShipAttributeType type = layout.type(id);
            float scale = type.scaleGroup() == ShipAttributeScaleGroup.NONE ? 1F : 1.75F;
            ShipAttributeCombineContext context = new ShipAttributeCombineContext(
                    rawValues.get(id), equipmentValues.get(id), moraleValues.get(id),
                    potionValues.get(id), formationValues.get(id), scale);
            assertFloatEquals(type.combine(context), combined.get(id), "core engine golden " + id);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void legacyArraysAndDynamicCoreLayersStayEquivalent(GameTestHelper helper) {
        AttrsAdv attrs = new AttrsAdv();
        float[] raw = Attrs.getResetRawValue();
        raw[ID.Attrs.HP] = 37F;
        attrs.setAttrsRaw(raw);
        attrs.setAttrsEquip(ID.Attrs.ATK_L, 8F);
        attrs.setAttrsPotion(ID.Attrs.HIT, 3F);
        attrs.setAttrsFormation(ID.Attrs.DEF, 1.4F);

        assertFloatEquals(37F, attrs.shipAttributes(ShipAttributeLayer.RAW).get(CoreShipAttributes.HP),
                "array setter visible through dynamic raw query");
        assertFloatEquals(8F, attrs.shipAttributes(ShipAttributeLayer.EQUIPMENT).get(CoreShipAttributes.ATK_L),
                "index setter visible through dynamic equipment query");
        assertFloatEquals(3F, attrs.shipAttributes(ShipAttributeLayer.POTION).get(CoreShipAttributes.HIT),
                "index setter visible through dynamic potion query");
        assertFloatEquals(1.4F, attrs.shipAttributes(ShipAttributeLayer.FORMATION).get(CoreShipAttributes.DEF),
                "index setter visible through dynamic formation query");

        ShipAttributeLayout layout = ShipAttributeLayout.current();
        ShipAttributeValues equipment = ShipAttributeValues.defaults(layout, ShipAttributeLayer.EQUIPMENT)
                .toBuilder()
                .set(CoreShipAttributes.HP, 11F)
                .set(CoreShipAttributes.MOV, -0.08F)
                .build();
        attrs.setShipAttributes(ShipAttributeLayer.EQUIPMENT, equipment);
        assertFloatEquals(11F, attrs.getAttrsEquip(ID.Attrs.HP), "dynamic setter mirrored to HP array slot");
        assertFloatEquals(-0.08F, attrs.getAttrsEquip(ID.Attrs.MOV), "dynamic setter mirrored to MOV array slot");

        AttrsAdv copy = AttrsAdv.copyAttrsAdv(attrs);
        attrs.setAttrsEquip(ID.Attrs.HP, 99F);
        assertFloatEquals(11F, copy.shipAttributes(ShipAttributeLayer.EQUIPMENT).get(CoreShipAttributes.HP),
                "copied dynamic layer remains independent");

        attrs.resetAttrsEquip();
        assertFloatEquals(0F, attrs.shipAttributes(ShipAttributeLayer.EQUIPMENT).get(CoreShipAttributes.HP),
                "legacy reset visible through dynamic query");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void dynamicLayerValidationRejectsPartialAndNonFiniteValues(GameTestHelper helper) {
        ResourceLocation firstId = id("test_addon", "first");
        ResourceLocation secondId = id("test_addon", "second");
        ShipAttributeType firstType = ShipAttributeType.builder().build();
        ShipAttributeType secondType = ShipAttributeType.builder().build();
        ShipAttributeLayout layout = ShipAttributeLayout.detached(Map.of(firstId, firstType, secondId, secondType));
        ShipAttributeLayerState state = new ShipAttributeLayerState(layout);
        ShipAttributeLayout partialLayout = ShipAttributeLayout.detached(Map.of(firstId, firstType));

        assertThrows(IllegalArgumentException.class,
                () -> state.set(ShipAttributeLayer.RAW, ShipAttributeValues.zero(partialLayout)),
                "partial layout rejected");
        assertThrows(IllegalArgumentException.class,
                () -> ShipAttributeValues.builder(layout).set(firstId, Float.POSITIVE_INFINITY),
                "non-finite layer value rejected");

        ShipAttributeValues maximum = values(layout, Map.of(firstId, Float.MAX_VALUE));
        ShipAttributeValues one = values(layout, Map.of(firstId, Float.MAX_VALUE));
        assertThrows(IllegalArgumentException.class,
                () -> ShipEquipmentAttributeMath.add(maximum, one),
                "equipment addition overflow rejected as a whole stack");
        helper.succeed();
    }

    private static ShipAttributeValues values(ShipAttributeLayout layout,
                                               Map<ResourceLocation, Float> entries) {
        ShipAttributeValues.Builder builder = ShipAttributeValues.builder(layout);
        entries.forEach(builder::set);
        return builder.build();
    }

    private static ResourceLocation id(String namespace, String path) {
        return ResourceLocation.fromNamespaceAndPath(namespace, path);
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
        Throwable thrown = null;
        try {
            action.run();
        } catch (Throwable error) {
            thrown = error;
        }
        if (thrown == null || !expected.isInstance(thrown)) {
            throw new AssertionError(message + ": expected " + expected.getSimpleName()
                    + " but got " + (thrown == null ? "nothing" : thrown), thrown);
        }
    }
}
