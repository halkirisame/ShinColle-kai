package com.lulan.shincolle.gametest;

import com.lulan.shincolle.item.PointerItem;
import com.lulan.shincolle.reference.Reference;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Pins {@link PointerItem#cycleMode} and {@link PointerItem#baseMode}.
 * <p>
 * The wheel handler and the HUD carousel are client-only, so the stepping rule is the one
 * piece a GameTest can reach. They assert on a pure function and never touch the world, so
 * the {@code empty} template is enough here. It is also the piece with the two easy mistakes: wrapping the
 * wrong way at the ends, and dropping the caress band (3-5) back to the plain band.
 */
@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PointerModeCycleGameTests {

    private PointerModeCycleGameTests() {
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void modeStepsForwardAndWrapsAtTheEnd(GameTestHelper helper) {
        assertCycle(helper, PointerItem.MODE_SINGLE, 1, PointerItem.MODE_GROUP);
        assertCycle(helper, PointerItem.MODE_GROUP, 1, PointerItem.MODE_FORMATION);
        assertCycle(helper, PointerItem.MODE_FORMATION, 1, PointerItem.MODE_SINGLE);
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void modeStepsBackwardAndWrapsAtTheStart(GameTestHelper helper) {
        // Scrolling up walks this direction, so formation has to be reachable from single
        // in one step rather than two.
        assertCycle(helper, PointerItem.MODE_FORMATION, -1, PointerItem.MODE_GROUP);
        assertCycle(helper, PointerItem.MODE_GROUP, -1, PointerItem.MODE_SINGLE);
        assertCycle(helper, PointerItem.MODE_SINGLE, -1, PointerItem.MODE_FORMATION);
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void caressBandSurvivesStepping(GameTestHelper helper) {
        // 3-5 is the same three modes with caress toggled on; stepping must stay in it.
        for (int caress = 3; caress <= 5; caress++) {
            for (int direction = -1; direction <= 1; direction += 2) {
                int stepped = PointerItem.cycleMode(caress, direction);
                helper.assertTrue(stepped >= 3 && stepped <= 5,
                        "cycleMode(" + caress + ", " + direction + ") left the caress band: " + stepped);
            }
        }

        assertCycle(helper, 3, 1, 4);
        assertCycle(helper, 5, 1, 3);
        assertCycle(helper, 3, -1, 5);
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void outOfRangeModeFallsBackToSingle(GameTestHelper helper) {
        for (int mode : new int[]{-1, 6, 42, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
            assertCycle(helper, mode, 1, PointerItem.MODE_SINGLE);
            assertCycle(helper, mode, -1, PointerItem.MODE_SINGLE);
            helper.assertTrue(PointerItem.baseMode(mode) == PointerItem.MODE_SINGLE,
                    "baseMode(" + mode + ") should fall back to single but was "
                            + PointerItem.baseMode(mode));
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void baseModeStripsTheCaressBand(GameTestHelper helper) {
        // The overlay looks translation keys up by base mode, so 0 and 3 must name the
        // same row.
        for (int base = 0; base < PointerItem.MODE_COUNT; base++) {
            helper.assertTrue(PointerItem.baseMode(base) == base,
                    "baseMode(" + base + ") changed a plain mode");
            helper.assertTrue(PointerItem.baseMode(base + 3) == base,
                    "baseMode(" + (base + 3) + ") should map onto " + base
                            + " but was " + PointerItem.baseMode(base + 3));
        }
        helper.succeed();
    }

    private static void assertCycle(GameTestHelper helper, int from, int direction, int expected) {
        int actual = PointerItem.cycleMode(from, direction);
        helper.assertTrue(actual == expected,
                "cycleMode(" + from + ", " + direction + ") expected " + expected + " but was " + actual);
    }
}
