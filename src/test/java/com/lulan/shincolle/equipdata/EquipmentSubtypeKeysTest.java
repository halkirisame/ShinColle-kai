package com.lulan.shincolle.equipdata;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/** Pins JEI-independent subtype key behaviour for equipment variants. */
class EquipmentSubtypeKeysTest {

    @Test
    void ingredientKeysSeparateVariantsAndMatchEqualVariants() {
        assertNotEquals(EquipmentSubtypeKeys.subtypeKey(3, true), EquipmentSubtypeKeys.subtypeKey(4, true));
        assertEquals(EquipmentSubtypeKeys.subtypeKey(3, true), EquipmentSubtypeKeys.subtypeKey(3, true));
    }

    @Test
    void recipeKeysUseNoSubtypeValueForEveryVariant() {
        assertEquals("", EquipmentSubtypeKeys.subtypeKey(0, false));
        assertEquals("", EquipmentSubtypeKeys.subtypeKey(3, false));
    }

    @Test
    void variantZeroHasAnIngredientKey() {
        assertNotEquals("", EquipmentSubtypeKeys.subtypeKey(0, true));
    }

    @Test
    void probeVariantAlwaysDiffersFromInput() {
        for (int variant : new int[] {0, 1, 3, -1}) {
            assertNotEquals(variant, EquipmentSubtypeKeys.probeVariant(variant));
        }
    }
}
