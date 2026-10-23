package com.lulan.shincolle.visualdata;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;

/** Stateless semantic presentation rules, independent of Minecraft client state. */
public final class ShipVisualPresentation {
    public static final Map<String, Integer> SLOT_SIZES = Map.of("face", 5, "mouth", 3, "flush", 2);
    public record Expression(int face, int mouth, boolean flush) { }
    public record Visibility(boolean visible, boolean flipped) { }

    private ShipVisualPresentation() { }

    /** Static first frame of the existing emotion presets; no timers or random transitions. */
    public static Expression emotion(int emotion, boolean happyMorale) {
        return switch (emotion) {
            case 1 -> new Expression(1, 0, happyMorale);
            case 2 -> new Expression(6, 5, happyMorale);
            case 3 -> new Expression(2, 1, happyMorale);
            case 4 -> new Expression(5, 0, happyMorale);
            case 5 -> new Expression(4, 2, happyMorale);
            case 6 -> new Expression(1, 0, happyMorale);
            case 7 -> new Expression(0, 3, true);
            case 8 -> new Expression(3, 0, true);
            case 9 -> new Expression(9, 0, happyMorale);
            default -> new Expression(0, 0, happyMorale);
        };
    }

    public static Visibility visibility(String slot, int index, Expression expression, boolean specified) {
        return visibility(slot, index, expression, specified, SLOT_SIZES.getOrDefault(slot, 0));
    }

    public static Visibility visibility(String slot, int index, Expression expression, boolean specified, int available) {
        Integer count = SLOT_SIZES.get(slot);
        if (count == null || index < 0 || index >= count) return new Visibility(false, false);
        if (slot.equals("flush")) return new Visibility(specified && expression.flush(), false);
        int value = specified ? (slot.equals("face") ? expression.face() : expression.mouth()) : 0;
        if (value < 0 || value >= count * 2) value = 0;
        if (value % count >= available) value = 0;
        return new Visibility(index == value % count, value >= count);
    }

    /** Unspecified slots use their numbered rig family, but only its normal variant is shown. */
    public static List<String> expressionBones(ShipVisualDefinition visual, String slot, Set<String> bones) {
        List<String> explicit = visual.expressions().get(slot);
        if (explicit != null) {
            if (!bones.containsAll(explicit)) throw new IllegalArgumentException("Missing expression bones for " + slot);
            return explicit;
        }
        String anchor = visual.bone(slot);
        String prefix = anchor.endsWith("0") ? anchor.substring(0, anchor.length() - 1) : anchor;
        return IntStream.range(0, SLOT_SIZES.get(slot)).mapToObj(index -> bones.stream()
                .filter(name -> name.equalsIgnoreCase(prefix + index)).findFirst().orElse(index == 0 && bones.contains(anchor) ? anchor : ""))
                .toList();
    }

    public static boolean equipmentVisible(int modelState) {
        return (modelState & 1) != 0;
    }

    /** Minimum is in unscaled blocks above the entity's feet; output is in world blocks. */
    public static float postureLift(boolean active, float minimumY, float scale) {
        if (!active || !Float.isFinite(minimumY) || !Float.isFinite(scale) || scale <= 0) return 0;
        return Math.max(0, -minimumY) * scale;
    }
}
