package com.lulan.shincolle.shipdata;

import java.util.List;
import java.util.Map;

/** Explicit order of the six base columns followed by their six growth coefficients. */
public final class ShipDefinitionAttributes {
    private static final List<String> COLUMNS = List.of("hp", "atk", "def", "spd", "mov", "hit");

    private ShipDefinitionAttributes() {
    }

    public static float[] row(ShipDefinition definition) {
        float[] result = new float[12];
        float[] base = columns(definition.baseStats());
        float[] growth = columns(definition.growth());
        System.arraycopy(base, 0, result, 0, 6);
        System.arraycopy(growth, 0, result, 6, 6);
        return result;
    }

    public static float[] columns(Map<String, Double> values) {
        float[] result = new float[6];
        for (int i = 0; i < result.length; i++) {
            result[i] = values.get(COLUMNS.get(i)).floatValue();
        }
        return result;
    }
}
