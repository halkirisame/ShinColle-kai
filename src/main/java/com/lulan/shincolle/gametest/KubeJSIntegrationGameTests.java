package com.lulan.shincolle.gametest;

import com.lulan.shincolle.api.attribute.CoreShipAttributes;
import com.lulan.shincolle.api.attribute.ShipAttributeCombineContext;
import com.lulan.shincolle.api.attribute.ShipAttributeDisplayFormat;
import com.lulan.shincolle.api.attribute.ShipAttributeEnchantRule;
import com.lulan.shincolle.api.attribute.ShipAttributeLayer;
import com.lulan.shincolle.api.attribute.ShipAttributeLayout;
import com.lulan.shincolle.api.attribute.ShipAttributeScaleGroup;
import com.lulan.shincolle.api.attribute.ShipAttributeType;
import com.lulan.shincolle.init.ModShipAttributes;
import com.lulan.shincolle.reference.Reference;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.InvocationTargetException;

/**
 * Verifies the optional KubeJS startup-registry bridge without importing optional KubeJS classes.
 */
@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class KubeJSIntegrationGameTests {

    private static final String SMOKE_PROPERTY = "shincolle.kubejsSmokeTest";
    private static final ResourceLocation TEST_ATTRIBUTE =
            ResourceLocation.fromNamespaceAndPath("kubejs", "gametest_attribute");
    private static final float EPSILON = 0.00001F;

    private KubeJSIntegrationGameTests() {
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void kubeJsStartupDslRegistersCanonicalAttribute(GameTestHelper helper) {
        if (!Boolean.getBoolean(SMOKE_PROPERTY)) {
            helper.succeed();
            return;
        }
        if (!ModList.get().isLoaded("kubejs")) {
            helper.fail("KubeJS smoke run requires -PshincolleKubeJSRuntime=true");
            return;
        }

        ShipAttributeType type = ModShipAttributes.REGISTRY.get().getValue(TEST_ATTRIBUTE);
        if (type == null) {
            helper.fail("KubeJS startup script did not register " + TEST_ATTRIBUTE);
            return;
        }
        if (ShipAttributeLayout.current().indexOf(TEST_ATTRIBUTE) < 0) {
            helper.fail("KubeJS attribute was registered after the canonical layout was initialized");
            return;
        }

        assertFloat(2F, type.defaultValue(ShipAttributeLayer.RAW), "raw");
        assertFloat(3F, type.defaultValue(ShipAttributeLayer.EQUIPMENT), "equipment");
        assertFloat(4F, type.defaultValue(ShipAttributeLayer.MORALE), "morale");
        assertFloat(5F, type.defaultValue(ShipAttributeLayer.POTION), "potion");
        assertFloat(6F, type.defaultValue(ShipAttributeLayer.FORMATION), "formation");
        assertFloat(35F, type.combine(new ShipAttributeCombineContext(2F, 3F, 4F, 5F, 6F, 2F)),
                "scaled additive result");
        assertFloat(0F, type.minimum(), "minimum");
        assertFloat(100F, type.maximum(), "maximum");
        assertEquals(ShipAttributeScaleGroup.HIT, type.scaleGroup(), "scale group");
        assertEquals(ShipAttributeDisplayFormat.PERCENT, type.displayFormat(), "display format");
        assertEquals(ShipAttributeEnchantRule.WEAPON_ADDITIVE, type.enchantRule(), "enchant rule");
        assertEquals(CoreShipAttributes.HIT, type.enchantEffectSource(TEST_ATTRIBUTE), "enchant source");
        assertEquals("ship_attribute.kubejs.gametest_attribute", type.translationKey(TEST_ATTRIBUTE),
                "translation key");
        verifyDslBoundaryValidation();
        helper.succeed();
    }

    private static void verifyDslBoundaryValidation() {
        try {
            Class<?> builderClass = Class.forName(
                    "com.lulan.shincolle.integration.kubejs.ShipAttributeTypeKubeJSBuilder");
            Object normalized = newBuilder(builderClass, "normalized");
            invoke(builderClass, normalized, "scaleGroup", String.class, "SpD");
            invoke(builderClass, normalized, "displayFormat", String.class, "PerCent");
            invoke(builderClass, normalized, "enchantRule", String.class, "weapon additive");
            ShipAttributeType normalizedType = (ShipAttributeType) builderClass.getMethod("createObject")
                    .invoke(normalized);
            assertEquals(ShipAttributeScaleGroup.SPD, normalizedType.scaleGroup(), "normalized scale group");
            assertEquals(ShipAttributeDisplayFormat.PERCENT, normalizedType.displayFormat(),
                    "normalized display format");
            assertEquals(ShipAttributeEnchantRule.WEAPON_ADDITIVE, normalizedType.enchantRule(),
                    "normalized enchant rule");

            expectIllegalArgument("unknown enum", () -> invoke(builderClass,
                    newBuilder(builderClass, "unknown_enum"), "scaleGroup", String.class, "not-a-group"));
            expectIllegalArgument("invalid ResourceLocation", () -> invoke(builderClass,
                    newBuilder(builderClass, "invalid_rl"), "enchantEffectSource", String.class, "bad id"));
            expectIllegalArgument("non-finite layer default", () -> invoke(builderClass,
                    newBuilder(builderClass, "nan"), "raw", float.class, Float.NaN));
            expectIllegalArgument("blank translation key", () -> invoke(builderClass,
                    newBuilder(builderClass, "blank_translation"), "translationKey", String.class, " "));
            expectIllegalArgument("reversed bounds", () -> {
                Object reversed = newBuilder(builderClass, "reversed_bounds");
                invoke(builderClass, reversed, "minimum", float.class, 2F);
                invoke(builderClass, reversed, "maximum", float.class, 1F);
                builderClass.getMethod("createObject").invoke(reversed);
            });
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Could not inspect the optional KubeJS DSL", exception);
        }
    }

    private static Object newBuilder(Class<?> builderClass, String path) throws ReflectiveOperationException {
        return builderClass.getConstructor(ResourceLocation.class)
                .newInstance(ResourceLocation.fromNamespaceAndPath("kubejs_test", path));
    }

    private static Object invoke(Class<?> builderClass, Object builder, String method, Class<?> parameterType,
                                 Object value) throws ReflectiveOperationException {
        return builderClass.getMethod(method, parameterType).invoke(builder, value);
    }

    private static void expectIllegalArgument(String field, ThrowingAction action) {
        try {
            action.run();
        } catch (InvocationTargetException exception) {
            if (exception.getCause() instanceof IllegalArgumentException) {
                return;
            }
            throw new AssertionError(field + " threw the wrong exception", exception.getCause());
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(field + " could not be invoked", exception);
        }
        throw new AssertionError(field + " accepted invalid input");
    }

    private static void assertFloat(float expected, Float actual, String field) {
        if (actual == null || Math.abs(expected - actual) > EPSILON) {
            throw new AssertionError(field + " expected " + expected + " but was " + actual);
        }
    }

    private static void assertEquals(Object expected, Object actual, String field) {
        if (!java.util.Objects.equals(expected, actual)) {
            throw new AssertionError(field + " expected " + expected + " but was " + actual);
        }
    }

    @FunctionalInterface
    private interface ThrowingAction {
        void run() throws ReflectiveOperationException;
    }
}
