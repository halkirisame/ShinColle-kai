package com.lulan.shincolle.entity;

import com.lulan.shincolle.reference.ID;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShipStateAggregateTest {

    @Test
    void createsIndependentStateWithLegacyDefaults() {
        ShipStateAggregate first = new ShipStateAggregate();
        ShipStateAggregate second = new ShipStateAggregate();

        assertArrayEquals(new int[]{
                1, 0, 0, 40, 0,
                0, 0, 0, 0, 3,
                3, 12, 35, 1, -1,
                -1, -1, 0, -1, 0,
                0, -1, -1, -1, 0,
                0, 0, 0, 0, 0,
                60, 0, 10, 0, 0,
                -1, 0, 0, 0, 0,
                -1, -1, -1, 0, 0
        }, first.copyMinor());
        assertArrayEquals(new int[ShipStateAggregate.TIMER_COUNT], first.copyTimer());
        assertArrayEquals(new int[ShipStateAggregate.EMOTION_COUNT], first.copyEmotion());
        assertArrayEquals(new boolean[]{
                false, false, false, false, true,
                true, true, true, false, true,
                true, false, true, true, true,
                true, true, true, true, false,
                false, false, true, true, false,
                true, false
        }, first.copyFlags());
        assertArrayEquals(new boolean[ShipStateAggregate.UPDATE_FLAG_COUNT], first.copyUpdateFlags());

        first.setMinor(0, 99);
        first.setFlag(4, false);
        assertEquals(1, second.getMinor(0));
        assertTrue(second.getFlag(4));
    }

    @Test
    void persistenceSnapshotsCannotMutateOwnedState() {
        ShipStateAggregate state = new ShipStateAggregate();

        int[] minor = state.copyMinor();
        int[] emotion = state.copyEmotion();
        boolean[] flags = state.copyFlags();
        minor[0] = 99;
        emotion[0] = 7;
        flags[4] = false;

        assertEquals(1, state.getMinor(0));
        assertEquals(0, state.getEmotion(0));
        assertTrue(state.getFlag(4));
    }

    @Test
    void shortLegacyPayloadKeepsDefaultsOutsidePayload() {
        ShipStateAggregate state = new ShipStateAggregate();

        state.loadMinor(new int[]{7, 8});
        state.loadEmotion(new int[]{3});
        state.loadFlags(new byte[]{1, 1, 1});

        assertArrayEquals(new int[]{7, 8}, new int[]{state.getMinor(0), state.getMinor(1)});
        assertEquals(40, state.getMinor(3));
        assertEquals(3, state.getEmotion(0));
        assertEquals(0, state.getEmotion(1));
        assertTrue(state.getFlag(0));
        assertTrue(state.getFlag(1));
        assertTrue(state.getFlag(2));
        assertFalse(state.getFlag(3));
        assertTrue(state.getFlag(4));
    }

    @Test
    void legacySnapshotsRouteTypedEmotionStateWithoutLeakingReferences() {
        ShipStateAggregate state = new ShipStateAggregate();
        state.setEmotion(ID.S.Emotion, ID.Emotion.SHY);
        state.setEmotion(ID.S.Emotion4, ID.Emotion.BORED);
        state.setTimer(ID.T.EmoteDelay, 25);

        int[] emotions = state.copyEmotion();
        int[] timers = state.copyTimer();
        assertEquals(ID.Emotion.SHY, emotions[ID.S.Emotion]);
        assertEquals(ID.Emotion.BORED, emotions[ID.S.Emotion4]);
        assertEquals(25, timers[ID.T.EmoteDelay]);

        emotions[ID.S.Emotion] = ID.Emotion.NORMAL;
        timers[ID.T.EmoteDelay] = 0;
        assertEquals(ID.Emotion.SHY, state.getEmotion(ID.S.Emotion));
        assertEquals(25, state.getTimer(ID.T.EmoteDelay));

        int[] loaded = new int[ShipStateAggregate.EMOTION_COUNT];
        loaded[ID.S.Emotion] = ID.Emotion.XD;
        loaded[ID.S.Emotion4] = ID.Emotion.ANGRY;
        state.loadEmotion(loaded);
        assertEquals(ID.Emotion.XD, state.emotion().primaryExpression());
        assertEquals(ID.Emotion.ANGRY, state.emotion().secondaryExpression());
    }
}
