package com.lulan.shincolle.equipdata;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Describes the JEI item-list changes required to reach a desired equipment set. */
public record EquipmentIngredientDiff(List<String> toAdd, List<String> toRemove) {

    public static EquipmentIngredientDiff diff(Set<String> tracked, Set<String> desired) {
        Objects.requireNonNull(tracked, "tracked");
        Objects.requireNonNull(desired, "desired");

        List<String> toAdd = tracked.stream()
                .filter(uid -> !desired.contains(uid))
                .sorted()
                .toList();
        List<String> toRemove = desired.stream()
                .filter(uid -> !tracked.contains(uid))
                .sorted()
                .toList();
        return new EquipmentIngredientDiff(toAdd, toRemove);
    }
}
