package com.lulan.shincolle.handler;

import com.lulan.shincolle.api.attribute.CoreShipAttributes;
import com.lulan.shincolle.api.attribute.ShipAttributeLayout;
import com.lulan.shincolle.api.attribute.ShipAttributeType;
import com.lulan.shincolle.api.attribute.ShipAttributeValues;
import com.lulan.shincolle.attribute.ShipAttributeLayerEngine;
import com.lulan.shincolle.reference.Reference;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Regression coverage for stable-ID ship-attribute limit configuration. */
@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ShipAttributeLimitGameTests {

    private static final ResourceLocation CUSTOM = id("addon_test", "sonar_precision");
    private static final ResourceLocation TYPE_BOUNDED = id("addon_test", "type_bounded");

    private ShipAttributeLimitGameTests() {
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void shortLegacyListRebuildsDefaultSuffix(GameTestHelper helper) {
        List<String> warnings = new ArrayList<>();
        ShipAttributeLimits limits = ShipAttributeLimits.legacyOnly(List.of(123D), warnings::add);

        assertDoubleEquals(123D, limits.maximum(CoreShipAttributes.HP), "legacy prefix override");
        assertDoubleEquals(0.8D, limits.maximum(CoreShipAttributes.DEF), "default DEF suffix");
        assertDoubleEquals(0.6D, limits.maximum(CoreShipAttributes.MOV), "default MOV suffix");
        assertDoubleEquals(1D, limits.maximum(CoreShipAttributes.KB), "default KB suffix");
        if (!warnings.isEmpty()) {
            throw new AssertionError("Valid short legacy list produced warnings: " + warnings);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void namedLimitsOverrideLegacyAndApplyBeforeTypeBounds(GameTestHelper helper) {
        ShipAttributeLayout layout = testLayout();
        ShipAttributeLimits limits = ShipAttributeLimits.resolve(
                List.of(80D),
                List.of("shincolle:hp=0.5", "shincolle:def=-1", CUSTOM + "=3"),
                layout, message -> {
                    throw new AssertionError("Valid named limits produced warning: " + message);
                });

        assertDoubleEquals(0.5D, limits.maximum(CoreShipAttributes.HP), "named core override");
        assertDoubleEquals(-1D, limits.maximum(CoreShipAttributes.DEF), "named -1 suppresses legacy cap");
        assertDoubleEquals(3D, limits.maximum(CUSTOM), "named custom override");
        assertDoubleEquals(-1D, limits.maximum(TYPE_BOUNDED), "unconfigured custom delegates to type");

        ShipAttributeValues raw = ShipAttributeValues.builder(layout)
                .set(CoreShipAttributes.HP, 50F)
                .set(CUSTOM, 30F)
                .set(TYPE_BOUNDED, 30F)
                .build();
        ShipAttributeValues zero = ShipAttributeValues.zero(layout);
        ShipAttributeValues combined = ShipAttributeLayerEngine.combine(raw, zero, zero, zero, zero,
                ignored -> 1F, limits::maximum,
                (id, error) -> {
                    throw new AssertionError("Limit combination failed for " + id, error);
                });

        assertFloatEquals(1F, combined.get(CoreShipAttributes.HP), "type minimum after named maximum");
        assertFloatEquals(5F, combined.get(CUSTOM), "custom type minimum after named maximum");
        assertFloatEquals(20F, combined.get(TYPE_BOUNDED), "type maximum without config override");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void invalidNamedEntriesAreIsolatedAndBounded(GameTestHelper helper) {
        ShipAttributeLayout layout = testLayout();
        List<String> warnings = new ArrayList<>();
        List<String> entries = new ArrayList<>(List.of(
                CUSTOM + "=4",
                CUSTOM + "=5",
                "missing:attribute=6",
                "missing_separator",
                TYPE_BOUNDED + "=NaN",
                TYPE_BOUNDED + "=-2",
                "x".repeat(ShipAttributeLimits.MAX_ENTRY_LENGTH + 1)
        ));
        entries.addAll(Collections.nCopies(ShipAttributeLimits.MAX_NAMED_ENTRIES, CUSTOM + "=7"));

        ShipAttributeLimits limits = ShipAttributeLimits.resolve(List.of(), entries, layout, warnings::add);

        assertDoubleEquals(4D, limits.maximum(CUSTOM), "first valid duplicate wins");
        assertDoubleEquals(-1D, limits.maximum(TYPE_BOUNDED), "invalid custom values ignored");
        if (warnings.stream().noneMatch(message -> message.contains("ignoring entries after"))) {
            throw new AssertionError("Oversized named limit list was not reported");
        }
        if (warnings.stream().noneMatch(message -> message.contains("unregistered attribute"))
                || warnings.stream().noneMatch(message -> message.contains("must use namespace:path=value"))
                || warnings.stream().noneMatch(message -> message.contains("finite non-negative"))
                || warnings.stream().noneMatch(message -> message.contains("exceeds"))
                || warnings.stream().noneMatch(message -> message.contains("duplicates"))) {
            throw new AssertionError("Invalid named entry classes were not all reported: " + warnings);
        }
        helper.succeed();
    }

    private static ShipAttributeLayout testLayout() {
        Map<ResourceLocation, ShipAttributeType> types = new LinkedHashMap<>();
        types.put(CoreShipAttributes.HP, ShipAttributeType.builder().minimum(1F).build());
        types.put(CoreShipAttributes.DEF, ShipAttributeType.builder().maximum(0.8F).build());
        types.put(CUSTOM, ShipAttributeType.builder().minimum(5F).maximum(40F).build());
        types.put(TYPE_BOUNDED, ShipAttributeType.builder().maximum(20F).build());
        return ShipAttributeLayout.detached(types);
    }

    private static ResourceLocation id(String namespace, String path) {
        return ResourceLocation.fromNamespaceAndPath(namespace, path);
    }

    private static void assertDoubleEquals(double expected, double actual, String message) {
        if (Double.compare(expected, actual) != 0) {
            throw new AssertionError(message + ": expected " + expected + " but was " + actual);
        }
    }

    private static void assertFloatEquals(float expected, float actual, String message) {
        if (Float.compare(expected, actual) != 0) {
            throw new AssertionError(message + ": expected " + expected + " but was " + actual);
        }
    }
}
