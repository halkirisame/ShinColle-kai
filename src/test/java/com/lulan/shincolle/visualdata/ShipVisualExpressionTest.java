package com.lulan.shincolle.visualdata;

import org.junit.jupiter.api.Test;

import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShipVisualExpressionTest {
    private static ShipVisualPresentation.Expression face(int tick, int uid, int emotion, int age, ShipVisualEvent posture,
                                                         boolean attack) {
        return ShipVisualExpression.select(new ShipVisualExpression.Input(tick, uid, emotion, age, false, false, posture, attack, 0));
    }

    @Test
    void deterministicRandomHasIndependentUidCycleAndExpectedLargeSampleFrequencies() {
        int blinks = 0;
        int tilts = 0;
        int uidDifferences = 0;
        int cycleDifferences = 0;
        int tiltUidDifferences = 0;
        int tiltCycleDifferences = 0;
        for (int uid = 0; uid < 1000; uid++) {
            for (int cycle = 1; cycle <= 200; cycle++) {
                boolean blink = ShipVisualExpression.blinkCycle(uid, cycle);
                boolean tilt = ShipHeadTilt.tilted(uid, cycle);
                assertEquals(blink, ShipVisualExpression.blinkCycle(uid, cycle));
                assertEquals(tilt, ShipHeadTilt.tilted(uid, cycle));
                if (blink) blinks++;
                if (tilt) tilts++;
                if (blink != ShipVisualExpression.blinkCycle(uid + 1, cycle)) uidDifferences++;
                if (blink != ShipVisualExpression.blinkCycle(uid, cycle + 1)) cycleDifferences++;
                if (tilt != ShipHeadTilt.tilted(uid + 1, cycle)) tiltUidDifferences++;
                if (tilt != ShipHeadTilt.tilted(uid, cycle + 1)) tiltCycleDifferences++;
            }
        }
        System.out.println("Presentation random: blink=" + blinks + "/200000, tilt=" + tilts + "/200000");
        assertTrue(blinks > 39000 && blinks < 41000);
        assertTrue(tilts > 99000 && tilts < 101000);
        assertTrue(uidDifferences > 50000);
        assertTrue(cycleDifferences > 50000);
        assertTrue(tiltUidDifferences > 90000);
        assertTrue(tiltCycleDifferences > 90000);
    }

    @Test
    void normalBlinkUsesOneDecisionPer128TicksAndOriginalDoubleBlinkWindows() {
        int uid = 0;
        while (!ShipVisualExpression.blinkCycle(uid, 2)) uid++;
        for (int tick = 0; tick < 128; tick++) {
            int expected = tick <= 10 || tick > 20 && tick <= 25 ? 1 : 0;
            assertEquals(expected, face(256 + tick, uid, 0, tick, ShipVisualEvent.IDLE, false).face());
            assertEquals(expected, face(256 + tick, uid, 0, tick, ShipVisualEvent.IDLE, false).face());
        }
        while (ShipVisualExpression.blinkCycle(uid, 2)) uid++;
        for (int tick = 0; tick < 128; tick++) assertEquals(0, face(256 + tick, uid, 0, 0, ShipVisualEvent.IDLE, false).face());
        assertEquals(1, face(100, 0, 1, 0, ShipVisualEvent.IDLE, false).face());
        assertEquals(0, face(100, 0, 1, 26, ShipVisualEvent.IDLE, false).face());
    }

    @Test
    void presetsExpireStrictlyAfterTheirOwnDurationWithoutChangingTheInputEmotion() {
        int[] durations = {80, 45, 40, 80, 60};
        int[] emotions = {2, 3, 6, 7, 8};
        for (int i = 0; i < emotions.length; i++) {
            int emotion = emotions[i];
            assertEquals(durations[i], ShipVisualExpression.duration(emotion));
            assertEquals(ShipVisualPresentation.emotion(emotion, false), face(0, 0, emotion, 0, ShipVisualEvent.IDLE, false));
            assertEquals(face(0, 0, emotion, 0, ShipVisualEvent.IDLE, false),
                    face(0, 0, emotion, durations[i], ShipVisualEvent.IDLE, false));
            assertEquals(face(0, 0, 0, 0, ShipVisualEvent.IDLE, false),
                    face(0, 0, emotion, durations[i] + 1, ShipVisualEvent.IDLE, false));
        }
    }

    @Test
    void phaseMatchesIntShiftAndMaskIncludingOverflowAndNegativeUids() {
        for (int uid : new int[]{0, 1, 2, -1, 12345, Integer.MAX_VALUE, Integer.MIN_VALUE}) {
            for (int tick : new int[]{0, 63, 127, 255, 511, 2047, Integer.MAX_VALUE}) {
                for (int mask : new int[]{255, 511}) {
                    assertEquals((tick + (uid << 7)) & mask, ShipVisualExpression.phase(tick, uid, mask));
                }
            }
        }
        assertNotEquals(face(0, 0, 2, 0, ShipVisualEvent.IDLE, false), face(0, 1, 2, 0, ShipVisualEvent.IDLE, false));
    }

    @Test
    void phaseFacesMatchEveryOriginalBranchBoundaryAndHungryIsConstant() {
        for (int t = 0; t < 512; t++) {
            int c = t & 255;
            assertFace(c < 128 ? 6 : 7, c < 64 ? 5 : 2, face(t, 0, 2, 0, ShipVisualEvent.IDLE, false));
            assertFace(t < 128 ? 1 : t < 256 ? 2 : t < 384 ? 3 : 8,
                    t < 64 ? 0 : t < 128 ? 2 : t < 180 ? 0 : t < 256 ? 1 : t < 320 ? 0 : t < 384 ? 4 : t < 450 ? 0 : 1,
                    face(t, 0, 0, 0, ShipVisualEvent.IDLE, true));
            assertFace(t < 200 ? 6 : t < 400 ? 3 : 9,
                    t < 60 ? 5 : t < 200 ? 2 : t < 250 ? 0 : t < 400 ? 4 : t < 450 ? 0 : 1,
                    face(2048 + 1536 + t, 0, 3, 0, ShipVisualEvent.IDLE, false));
            assertFace(t < 170 ? 5 : t < 340 ? 8 : 0, t < 80 ? 0 : t < 170 ? 4 : 0,
                    face(t, 0, 4, 0, ShipVisualEvent.IDLE, false));
            assertFace(4, 2, face(t, 0, 5, 10000, ShipVisualEvent.IDLE, false));
            assertFace(c < 128 ? 1 : 2, c < 64 ? 0 : c < 170 ? 1 : 2, face(t, 0, 6, 0, ShipVisualEvent.IDLE, false));
            assertFace(c < 140 ? 0 : 8, c < 80 ? 3 : c < 140 ? 2 : 0, face(t, 0, 7, 0, ShipVisualEvent.IDLE, false));
            assertFace(c < 140 ? 3 : 8, c < 80 ? 0 : 4, face(t, 0, 8, 0, ShipVisualEvent.IDLE, false));
        }
    }

    @Test
    void selectedPostureAndActiveAttackHaveTheDocumentedPrecedence() {
        assertFace(4, 2, face(300, 0, 7, 0, ShipVisualEvent.NO_FUEL, true));
        assertFace(0, 3, face(0, 0, 0, 0, ShipVisualEvent.CARESS, true));
        assertFace(3, 0, face(0, 0, 4, 0, ShipVisualEvent.SIT, false));
        assertFace(5, 4, face(100, 0, 4, 0, ShipVisualEvent.SIT, false));
        assertFace(3, 5, face(0, 0, 2, 0, ShipVisualEvent.SPRINT, false));
        assertTrue(face(0, 0, 0, 0, ShipVisualEvent.CARESS, false).flush());
        assertFalse(face(0, 0, 0, 0, ShipVisualEvent.NO_FUEL, false).flush());
    }

    @Test
    void tiltScheduleIsBoundedFrameIndependentAndUses71To75TickIntervals() {
        var intervals = new HashSet<Integer>();
        for (int uid = 0; uid < 10; uid++) {
            long previousCycle = 0;
            int previousStart = 0;
            for (int tick = 0; tick < 4000; tick++) {
                var cycle = ShipHeadTilt.cycle(tick, uid);
                assertEquals(cycle, ShipHeadTilt.cycle(tick, uid));
                if (cycle.number() != previousCycle) {
                    int interval = tick - previousStart;
                    assertTrue(interval >= 71 && interval <= 75);
                    intervals.add(interval);
                    previousStart = tick;
                    assertEquals(previousCycle + 1, cycle.number());
                    previousCycle = cycle.number();
                    assertEquals(0, cycle.age());
                }
                float angle = ShipHeadTilt.angle(tick, uid, 0.5F);
                assertTrue(angle >= -0.27F && angle <= 0);
                assertEquals(angle, ShipHeadTilt.angle(tick, uid, 0.5F));
            }
        }
        assertEquals(java.util.Set.of(71, 72, 73, 74, 75), intervals);
        assertEquals(0, ShipHeadTilt.angle(0, 0, 0));
        assertEquals(-0.27F, ShipHeadTilt.ease(true, true, 0));
        assertEquals(0, ShipHeadTilt.ease(false, false, 100));
        assertEquals(-0.27F, ShipHeadTilt.ease(true, false, 10));
        assertEquals(0, ShipHeadTilt.ease(false, true, 4));
        assertEquals(0, ShipHeadTilt.ease(false, true, 70));
        float atOne = (float) Math.sin(1F * 0.1F * 1.5708F) * -0.27F;
        assertEquals(atOne, ShipHeadTilt.ease(true, false, 1));
        float atReturn = (1F - (float) Math.sin(1F * 0.2F * 1.5708F)) * -0.27F;
        assertEquals(atReturn, ShipHeadTilt.ease(false, true, 1));
    }

    private static void assertFace(int face, int mouth, ShipVisualPresentation.Expression expression) {
        assertEquals(face, expression.face());
        assertEquals(mouth, expression.mouth());
    }
}
