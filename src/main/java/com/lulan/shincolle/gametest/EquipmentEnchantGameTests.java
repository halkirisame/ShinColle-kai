package com.lulan.shincolle.gametest;

import com.lulan.shincolle.api.attribute.CoreShipAttributes;
import com.lulan.shincolle.api.attribute.ShipAttributeLayout;
import com.lulan.shincolle.api.attribute.ShipAttributeType;
import com.lulan.shincolle.api.attribute.ShipAttributeValues;
import com.lulan.shincolle.crafting.EquipCalc;
import com.lulan.shincolle.reference.Reference;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Regression coverage for the data-driven equipment enchant calculation.
 */
@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class EquipmentEnchantGameTests {

    private static final float EPSILON = 0.00001F;

    private EquipmentEnchantGameTests() {
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void equipmentEnchantRulesMatchLegacyForAllCoreTypes(GameTestHelper helper) {
        ShipAttributeLayout layout = ShipAttributeLayout.current();
        ShipAttributeValues raw = coreRawValues(layout);
        ShipAttributeValues effects = coreEffectValues(layout);

        for (int enchantType : List.of(0, 1, 2, 3)) {
            ShipAttributeValues result = EquipCalc.calcEquipStatWithEnchant(enchantType, raw, effects);
            for (ResourceLocation id : CoreShipAttributes.LEGACY_ORDER) {
                float expected = expectedLegacyValue(enchantType, id, raw.get(id), effects);
                assertFloatEquals(expected, result.get(id),
                        "enchant type " + enchantType + " result for " + id);
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void equipmentEnchantSignedRulesClampNegativeMovementAndDodge(GameTestHelper helper) {
        ShipAttributeLayout layout = ShipAttributeLayout.current();
        ShipAttributeValues raw = ShipAttributeValues.builder(layout)
                .set(CoreShipAttributes.MOV, -8F)
                .set(CoreShipAttributes.DODGE, -5F)
                .build();
        ShipAttributeValues effects = ShipAttributeValues.builder(layout)
                .set(CoreShipAttributes.MOV, 1.5F)
                .set(CoreShipAttributes.DODGE, 2F)
                .build();

        for (int enchantType : List.of(0, 1, 2, 3)) {
            ShipAttributeValues result = EquipCalc.calcEquipStatWithEnchant(enchantType, raw, effects);
            assertFloatEquals(0F, result.get(CoreShipAttributes.MOV),
                    "negative MOV clamps at enchant type " + enchantType);
            assertFloatEquals(0F, result.get(CoreShipAttributes.DODGE),
                    "negative DODGE clamps at enchant type " + enchantType);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void equipmentEnchantAttackVariantsShareLowAttackEffect(GameTestHelper helper) {
        ShipAttributeLayout layout = ShipAttributeLayout.current();
        ShipAttributeValues raw = ShipAttributeValues.builder(layout)
                .set(CoreShipAttributes.ATK_L, 10F)
                .set(CoreShipAttributes.ATK_H, 20F)
                .set(CoreShipAttributes.ATK_AL, 30F)
                .set(CoreShipAttributes.ATK_AH, 40F)
                .build();
        ShipAttributeValues effects = ShipAttributeValues.builder(layout)
                .set(CoreShipAttributes.ATK_L, 0.5F)
                .set(CoreShipAttributes.ATK_H, 0.1F)
                .set(CoreShipAttributes.ATK_AL, 0.2F)
                .set(CoreShipAttributes.ATK_AH, 0.3F)
                .build();

        ShipAttributeValues weapon = EquipCalc.calcEquipStatWithEnchant(1, raw, effects);
        assertFloatEquals(15F, weapon.get(CoreShipAttributes.ATK_L), "ATK_L uses its own effect");
        assertFloatEquals(30F, weapon.get(CoreShipAttributes.ATK_H), "ATK_H uses ATK_L effect");
        assertFloatEquals(45F, weapon.get(CoreShipAttributes.ATK_AL), "ATK_AL uses ATK_L effect");
        assertFloatEquals(60F, weapon.get(CoreShipAttributes.ATK_AH), "ATK_AH uses ATK_L effect");

        ShipAttributeValues armor = EquipCalc.calcEquipStatWithEnchant(2, raw, effects);
        for (ResourceLocation id : List.of(CoreShipAttributes.ATK_L, CoreShipAttributes.ATK_H,
                CoreShipAttributes.ATK_AL, CoreShipAttributes.ATK_AH)) {
            assertFloatEquals(raw.get(id), armor.get(id), "armor does not modify " + id);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void equipmentEnchantRetainsCustomNoneAcrossDifferentLayouts(GameTestHelper helper) {
        ShipAttributeLayout canonical = ShipAttributeLayout.current();
        ResourceLocation customId = ResourceLocation.fromNamespaceAndPath("test_addon", "sonar_precision");
        Map<ResourceLocation, ShipAttributeType> rawEntries = new HashMap<>();
        for (ResourceLocation id : canonical.ids()) {
            rawEntries.put(id, canonical.type(id));
        }
        rawEntries.put(customId, ShipAttributeType.builder().build());
        ShipAttributeLayout rawLayout = ShipAttributeLayout.detached(rawEntries);

        ShipAttributeValues raw = ShipAttributeValues.builder(rawLayout)
                .set(CoreShipAttributes.ATK_L, 10F)
                .set(customId, 11F)
                .build();
        ShipAttributeValues effects = ShipAttributeValues.builder(canonical)
                .set(CoreShipAttributes.ATK_L, 0.5F)
                .build();
        ShipAttributeValues result = EquipCalc.calcEquipStatWithEnchant(1, raw, effects);

        if (result.layout() != rawLayout) {
            throw new AssertionError("Enchant result did not retain the raw layout instance.");
        }
        assertFloatEquals(15F, result.get(CoreShipAttributes.ATK_L),
                "core effect resolves by ResourceLocation across layouts");
        assertFloatEquals(11F, result.get(customId),
                "custom NONE attribute ignores a missing effect-layout entry");

        ShipAttributeLayout customOnlyLayout = ShipAttributeLayout.detached(
                Map.of(customId, ShipAttributeType.builder().build()));
        ShipAttributeValues customOnlyEffects = ShipAttributeValues.builder(customOnlyLayout)
                .set(customId, 0.75F)
                .build();
        ShipAttributeValues missingCoreEffect = EquipCalc.calcEquipStatWithEnchant(1, raw, customOnlyEffects);
        assertFloatEquals(10F, missingCoreEffect.get(CoreShipAttributes.ATK_L),
                "missing cross-layout core effect defaults to zero");
        assertFloatEquals(11F, missingCoreEffect.get(customId),
                "custom NONE remains unchanged when its effect exists");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void equipmentEnchantRejectsFiniteInputsThatOverflow(GameTestHelper helper) {
        ShipAttributeLayout layout = ShipAttributeLayout.current();
        ShipAttributeValues multiplicativeRaw = ShipAttributeValues.builder(layout)
                .set(CoreShipAttributes.HP, Float.MAX_VALUE)
                .build();
        ShipAttributeValues multiplicativeEffect = ShipAttributeValues.builder(layout)
                .set(CoreShipAttributes.HP, 1F)
                .build();
        assertThrows(IllegalArgumentException.class,
                () -> EquipCalc.calcEquipStatWithEnchant(0, multiplicativeRaw, multiplicativeEffect),
                "multiplicative overflow");

        ShipAttributeValues additiveRaw = ShipAttributeValues.builder(layout)
                .set(CoreShipAttributes.XP, Float.MAX_VALUE)
                .build();
        ShipAttributeValues additiveEffect = ShipAttributeValues.builder(layout)
                .set(CoreShipAttributes.XP, Float.MAX_VALUE)
                .build();
        assertThrows(IllegalArgumentException.class,
                () -> EquipCalc.calcEquipStatWithEnchant(1, additiveRaw, additiveEffect),
                "additive overflow");
        helper.succeed();
    }

    private static ShipAttributeValues coreRawValues(ShipAttributeLayout layout) {
        ShipAttributeValues.Builder values = ShipAttributeValues.builder(layout);
        for (int index = 0; index < CoreShipAttributes.LEGACY_ORDER.size(); index++) {
            ResourceLocation id = CoreShipAttributes.LEGACY_ORDER.get(index);
            float raw = index + 1F;
            if (id.equals(CoreShipAttributes.MOV)) {
                raw = -8F;
            } else if (id.equals(CoreShipAttributes.DODGE)) {
                raw = -5F;
            }
            values.set(id, raw);
        }
        return values.build();
    }

    private static ShipAttributeValues coreEffectValues(ShipAttributeLayout layout) {
        ShipAttributeValues.Builder values = ShipAttributeValues.builder(layout);
        for (int index = 0; index < CoreShipAttributes.LEGACY_ORDER.size(); index++) {
            values.set(CoreShipAttributes.LEGACY_ORDER.get(index), (index + 1F) * 0.05F);
        }
        return values.build();
    }

    private static float expectedLegacyValue(int enchantType, ResourceLocation id, float raw,
                                             ShipAttributeValues effects) {
        float effect = effects.get(id);
        if (id.equals(CoreShipAttributes.ATK_H) || id.equals(CoreShipAttributes.ATK_AL)
                || id.equals(CoreShipAttributes.ATK_AH)) {
            effect = effects.get(CoreShipAttributes.ATK_L);
        }
        if (id.equals(CoreShipAttributes.ATK_L) || id.equals(CoreShipAttributes.ATK_H)
                || id.equals(CoreShipAttributes.ATK_AL) || id.equals(CoreShipAttributes.ATK_AH)) {
            return enchantType == 1 ? raw * (1F + effect) : raw;
        }
        if (id.equals(CoreShipAttributes.DEF)) {
            return enchantType == 2 ? raw * (1F + effect) : raw;
        }
        if (id.equals(CoreShipAttributes.MOV) || id.equals(CoreShipAttributes.DODGE)) {
            return raw < 0F ? raw * Math.max(0F, 1F - effect) : raw * (1F + effect);
        }
        if (id.equals(CoreShipAttributes.XP) || id.equals(CoreShipAttributes.AMMO)) {
            return enchantType == 1 ? raw + effect : raw;
        }
        if (id.equals(CoreShipAttributes.GRUDGE) || id.equals(CoreShipAttributes.HPRES)
                || id.equals(CoreShipAttributes.KB)) {
            return enchantType == 1 ? raw : raw + effect;
        }
        return raw * (1F + effect);
    }

    private static void assertFloatEquals(float expected, float actual, String message) {
        if (Math.abs(expected - actual) > EPSILON) {
            throw new AssertionError(message + ": expected " + expected + " but was " + actual);
        }
    }

    private static void assertThrows(Class<? extends Throwable> expected, Runnable action, String message) {
        try {
            action.run();
        } catch (Throwable error) {
            if (expected.isInstance(error)) {
                return;
            }
            throw new AssertionError(message + ": expected " + expected.getSimpleName()
                    + " but got " + error, error);
        }
        throw new AssertionError(message + ": expected " + expected.getSimpleName() + " but got nothing");
    }
}
