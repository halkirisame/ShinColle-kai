package com.lulan.shincolle.equipdata;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Pins the JEI runtime updates needed when synchronized equipment availability changes. */
class EquipmentIngredientDiffTest {

    @Test
    void emptyToHiddenSchedulesRemoval() {
        EquipmentIngredientDiff diff = EquipmentIngredientDiff.diff(Set.of(), Set.of("A"));

        assertEquals(List.of(), diff.toAdd());
        assertEquals(List.of("A"), diff.toRemove());
    }

    @Test
    void matchingHiddenSetsProduceNoOperations() {
        EquipmentIngredientDiff diff = EquipmentIngredientDiff.diff(Set.of("A"), Set.of("A"));

        assertEquals(List.of(), diff.toAdd());
        assertEquals(List.of(), diff.toRemove());
    }

    @Test
    void hiddenVariantNoLongerTrackedSchedulesRestore() {
        EquipmentIngredientDiff diff = EquipmentIngredientDiff.diff(Set.of("A"), Set.of());

        assertEquals(List.of("A"), diff.toAdd());
        assertEquals(List.of(), diff.toRemove());
    }

    @Test
    void replacedHiddenVariantRestoresOldAndRemovesNew() {
        EquipmentIngredientDiff diff = EquipmentIngredientDiff.diff(Set.of("A"), Set.of("B"));

        assertEquals(List.of("A"), diff.toAdd());
        assertEquals(List.of("B"), diff.toRemove());
    }
}
