package com.lulan.shincolle.visualdata;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShipVisualPresentationTest {
    @Test
    void everyFaceAndMouthIdSelectsOneBoneAndResetsReflection() {
        for (String slot : new String[]{"face", "mouth"}) {
            int count = ShipVisualPresentation.SLOT_SIZES.get(slot);
            for (int value = 0; value < count * 2; value++) {
                var expression = new ShipVisualPresentation.Expression(value, value, false);
                int visible = 0;
                for (int index = 0; index < count; index++) {
                    var state = ShipVisualPresentation.visibility(slot, index, expression, true);
                    assertEquals(index == value % count, state.visible());
                    assertEquals(value >= count, state.flipped());
                    if (state.visible()) visible++;
                }
                assertEquals(1, visible);
            }
        }
    }

    @Test
    void semanticEmotionIdsAreNotMistakenForFaceIndicesAndNeverAdvanceTime() {
        int[] faces = {0, 1, 6, 2, 5, 4, 1, 0, 3, 9};
        int[] mouths = {0, 0, 5, 1, 0, 2, 0, 3, 0, 0};
        for (int id = 0; id < faces.length; id++) {
            var state = ShipVisualPresentation.emotion(id, false);
            assertEquals(faces[id], state.face());
            assertEquals(mouths[id], state.mouth());
            assertEquals(id == 7 || id == 8, state.flush());
            assertEquals(state, ShipVisualPresentation.emotion(id, false));
            assertTrue(ShipVisualPresentation.emotion(id, true).flush());
        }
        for (int invalid : new int[]{-1, 10, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
            assertEquals(new ShipVisualPresentation.Expression(0, 0, false), ShipVisualPresentation.emotion(invalid, false));
        }
    }

    @Test
    void missingSlotsAndInvalidValuesShowNormalFaceWithoutBlush() {
        for (String slot : new String[]{"face", "mouth"}) {
            for (int invalid : new int[]{-1, 100, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
                var state = new ShipVisualPresentation.Expression(invalid, invalid, true);
                for (int index = 0; index < ShipVisualPresentation.SLOT_SIZES.get(slot); index++) {
                    assertEquals(index == 0, ShipVisualPresentation.visibility(slot, index, state, true).visible());
                    assertEquals(index == 0, ShipVisualPresentation.visibility(slot, index, state, false).visible());
                }
            }
        }
        var blush = new ShipVisualPresentation.Expression(9, 5, true);
        for (int index = 0; index < 2; index++) {
            assertTrue(ShipVisualPresentation.visibility("flush", index, blush, true).visible());
            assertFalse(ShipVisualPresentation.visibility("flush", index, blush, false).visible());
            assertFalse(ShipVisualPresentation.visibility("flush", index,
                    new ShipVisualPresentation.Expression(0, 0, false), true).visible());
        }
        assertFalse(ShipVisualPresentation.visibility("unknown", 0, blush, true).visible());
        assertFalse(ShipVisualPresentation.visibility("face", -1, blush, true).visible());
        assertFalse(ShipVisualPresentation.visibility("face", 5, blush, true).visible());
        assertTrue(ShipVisualPresentation.visibility("face", 0, blush, true, 1).visible());
        assertFalse(ShipVisualPresentation.visibility("face", 0, blush, true, 1).flipped());
    }

    @Test
    void equipmentUsesOnlyTheCannonBitAndSittingLiftScalesExactlyOnce() {
        for (int state = 0; state < 65536; state++) {
            assertEquals((state & 1) == 1, ShipVisualPresentation.equipmentVisible(state));
        }
        assertEquals(0.23F, ShipVisualPresentation.postureLift(true, -0.5F, 0.46F));
        assertEquals(0.5F, ShipVisualPresentation.postureLift(true, -0.5F, 1));
        assertEquals(0, ShipVisualPresentation.postureLift(false, -0.5F, 0.46F));
        assertEquals(0, ShipVisualPresentation.postureLift(true, 0.5F, 0.46F));
        assertEquals(0, ShipVisualPresentation.postureLift(true, Float.POSITIVE_INFINITY, 0.46F));
        assertEquals(0, ShipVisualPresentation.postureLift(true, Float.NaN, 0.46F));
    }
}
