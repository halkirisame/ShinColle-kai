package com.lulan.shincolle.visualdata;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ShipVisualPostureTest {
    @Test
    void everyStateAndAvailabilityCombinationUsesTheHighestActiveAvailablePosture() {
        var order = List.of(ShipVisualEvent.NO_FUEL, ShipVisualEvent.CARESS, ShipVisualEvent.RIDE,
                ShipVisualEvent.SIT, ShipVisualEvent.SPRINT, ShipVisualEvent.MOVE, ShipVisualEvent.IDLE);
        for (int flags = 0; flags < 64; flags++) {
            var input = new ShipVisualPosture.Input(bit(flags, 0), bit(flags, 1), bit(flags, 2),
                    bit(flags, 3), bit(flags, 4), 0, bit(flags, 5));
            for (int clips = 0; clips < 128; clips++) {
                var available = EnumSet.noneOf(ShipVisualEvent.class);
                Optional<ShipVisualEvent> expected = Optional.empty();
                for (int index = 0; index < order.size(); index++) {
                    if (!bit(clips, index)) continue;
                    available.add(order.get(index));
                    if (expected.isEmpty() && (index == 6 || bit(flags, index))) expected = Optional.of(order.get(index));
                }
                available.add(ShipVisualEvent.LIGHT_ATTACK);
                available.add(ShipVisualEvent.HEAVY_ATTACK);
                assertEquals(expected, ShipVisualPosture.select(input, available), flags + "/" + clips);
            }
        }
    }

    @Test
    void sprintThresholdIsStrictAndExplicitSprintStillWorksWhenStationary() {
        var all = EnumSet.allOf(ShipVisualEvent.class);
        for (float amount : new float[]{-1, 0, Math.nextDown(0.95F), 0.95F, Float.NaN}) {
            assertEquals(Optional.of(ShipVisualEvent.MOVE), ShipVisualPosture.select(
                    new ShipVisualPosture.Input(false, false, false, false, false, amount, true), all));
        }
        assertEquals(Optional.of(ShipVisualEvent.SPRINT), ShipVisualPosture.select(
                new ShipVisualPosture.Input(false, false, false, false, false, Math.nextUp(0.95F), true), all));
        assertEquals(Optional.of(ShipVisualEvent.SPRINT), ShipVisualPosture.select(
                new ShipVisualPosture.Input(false, false, false, false, true, 0, false), all));
    }

    private static boolean bit(int value, int index) {
        return (value & (1 << index)) != 0;
    }
}
