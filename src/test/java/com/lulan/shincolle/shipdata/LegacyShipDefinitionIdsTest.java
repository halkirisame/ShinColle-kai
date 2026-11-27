package com.lulan.shincolle.shipdata;

import com.lulan.shincolle.reference.Values;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LegacyShipDefinitionIdsTest {
    @Test
    void tableIsImmutableAndUnknownClassesHaveNoId() {
        assertThrows(UnsupportedOperationException.class, () -> LegacyShipDefinitionIds.all().clear());
        assertNull(LegacyShipDefinitionIds.get(9999, LegacyShipDefinitionIds.Registration.FRIENDLY));
    }

    @Test
    void eachPublishedLimitIsTwiceTheMeasuredMaximumOfItsBuiltInColumn() {
        double[] maxima = new double[12];
        var builtIn = LegacyShipDefinitionIds.all().keySet().stream()
                .filter(key -> key.registration() == LegacyShipDefinitionIds.Registration.FRIENDLY)
                .map(LegacyShipDefinitionIds.Key::shipClass).toList();
        assertFalse(builtIn.isEmpty());
        for (int shipClass : builtIn) {
            float[] columns = Values.ShipAttrMap.get(shipClass);
            assertEquals(12, columns.length, "ship class " + shipClass);
            for (int i = 0; i < 12; i++) {
                maxima[i] = Math.max(maxima[i], columns[i]);
            }
        }
        String[] keys = {"hp", "atk", "def", "spd", "mov", "hit"};
        for (int i = 0; i < keys.length; i++) {
            assertEquals(2 * maxima[i], ShipDefinitionFormat.BASE_MAX.get(keys[i]), 0.0000001, keys[i]);
            assertEquals(2 * maxima[i + 6], ShipDefinitionFormat.GROWTH_MAX.get(keys[i]), 0.0000001, keys[i]);
        }
    }
}
