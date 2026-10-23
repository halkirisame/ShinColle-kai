package com.lulan.shincolle.visualdata;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/** Pure validation decisions shared by the loader and tests. */
public final class ShipVisualPolicy {
    public enum BoneBudget { RECOMMENDED, WARNING, REJECTED }

    private ShipVisualPolicy() { }

    public static BoneBudget boneBudget(int count) {
        if (count < 0) throw new IllegalArgumentException("Negative bone count");
        return count <= 64 ? BoneBudget.RECOMMENDED : count <= 128 ? BoneBudget.WARNING : BoneBudget.REJECTED;
    }

    public static Map<String, String> resolveRig(ShipVisualDefinition visual, Set<String> bones, Consumer<String> missing) {
        Map<String, String> result = new HashMap<>();
        for (String role : ShipVisualDefinition.ROLES) {
            String bone = visual.bone(role);
            if (bones.contains(bone)) result.put(role, bone);
            else missing.accept(role);
        }
        return Map.copyOf(result);
    }

    /** The built-in fallback intentionally has no off-hand locator; packs and add-ons still get the warning. */
    public static boolean warnsMissingRole(boolean builtinFallback, String role) {
        return !(builtinFallback && role.equals("held_item_left"));
    }

    public static <K, V> V select(Map<K, V> accepted, K id, V placeholder, Consumer<K> missing) {
        V visual = accepted.get(id);
        if (visual != null) return visual;
        missing.accept(id);
        return placeholder;
    }

    /** One missing root disables only this optional feature, not the whole visual. */
    public static Set<String> boneRoots(java.util.List<String> requested, Set<String> bones, Consumer<String> missing) {
        boolean complete = true;
        for (String root : requested) {
            if (!bones.contains(root)) {
                missing.accept(root);
                complete = false;
            }
        }
        return complete ? Set.copyOf(requested) : Set.of();
    }
}
