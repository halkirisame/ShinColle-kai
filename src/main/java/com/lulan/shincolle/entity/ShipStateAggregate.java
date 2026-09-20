package com.lulan.shincolle.entity;

import com.lulan.shincolle.reference.ID;

/**
 * Owns the legacy indexed state storage shared by friendly and hostile ships.
 *
 * <p>This is the first migration boundary between Minecraft entities and
 * ShinColle's domain state. The indexed representation remains unchanged so
 * existing behavior, packets, and NBT stay compatible. Typed state slices will
 * replace these indexes incrementally.</p>
 */
final class ShipStateAggregate {

    static final int MINOR_COUNT = 45;
    static final int TIMER_COUNT = 21;
    static final int EMOTION_COUNT = 8;
    static final int FLAG_COUNT = 27;
    static final int UPDATE_FLAG_COUNT = 8;

    private static final int[] DEFAULT_MINOR = {
            1, 0, 0, 40, 0,
            0, 0, 0, 0, 3,
            3, 12, 35, 1, -1,
            -1, -1, 0, -1, 0,
            0, -1, -1, -1, 0,
            0, 0, 0, 0, 0,
            60, 0, 10, 0, 0,
            -1, 0, 0, 0, 0,
            -1, -1, -1, 0, 0
    };

    private static final boolean[] DEFAULT_FLAGS = {
            false, false, false, false, true,
            true, true, true, false, true,
            true, false, true, true, true,
            true, true, true, true, false,
            false, false, true, true, false,
            true, false
    };

    private final int[] minor = DEFAULT_MINOR.clone();
    private final int[] timer = new int[TIMER_COUNT];
    private final int[] legacyEmotion = new int[EMOTION_COUNT];
    private final boolean[] flags = DEFAULT_FLAGS.clone();
    private final boolean[] updateFlags = new boolean[UPDATE_FLAG_COUNT];
    private final ShipEmotionState emotion = new ShipEmotionState();

    int getMinor(int id) {
        return this.minor[id];
    }

    void setMinor(int id, int value) {
        this.minor[id] = value;
    }

    int getTimer(int id) {
        return id == ID.T.EmoteDelay ? this.emotion.reactionCooldown() : this.timer[id];
    }

    void setTimer(int id, int value) {
        if (id == ID.T.EmoteDelay) {
            this.emotion.setReactionCooldown(value);
        } else {
            this.timer[id] = value;
        }
    }

    int getEmotion(int id) {
        return switch (id) {
            case ID.S.Emotion -> this.emotion.primaryExpression();
            case ID.S.Emotion4 -> this.emotion.secondaryExpression();
            default -> this.legacyEmotion[id];
        };
    }

    void setEmotion(int id, int value) {
        switch (id) {
            case ID.S.Emotion -> this.emotion.setPrimaryExpression(value);
            case ID.S.Emotion4 -> this.emotion.setSecondaryExpression(value);
            default -> this.legacyEmotion[id] = value;
        }
    }

    boolean getFlag(int id) {
        return this.flags[id];
    }

    void setFlag(int id, boolean value) {
        this.flags[id] = value;
    }

    boolean getUpdateFlag(int id) {
        return this.updateFlags[id];
    }

    void setUpdateFlag(int id, boolean value) {
        this.updateFlags[id] = value;
    }

    int[] copyMinor() {
        return this.minor.clone();
    }

    int[] copyEmotion() {
        int[] snapshot = this.legacyEmotion.clone();
        snapshot[ID.S.Emotion] = this.emotion.primaryExpression();
        snapshot[ID.S.Emotion4] = this.emotion.secondaryExpression();
        return snapshot;
    }

    int[] copyTimer() {
        int[] snapshot = this.timer.clone();
        snapshot[ID.T.EmoteDelay] = this.emotion.reactionCooldown();
        return snapshot;
    }

    boolean[] copyFlags() {
        return this.flags.clone();
    }

    boolean[] copyUpdateFlags() {
        return this.updateFlags.clone();
    }

    void loadMinor(int[] values) {
        System.arraycopy(values, 0, this.minor, 0, Math.min(values.length, this.minor.length));
    }

    void loadEmotion(int[] values) {
        int length = Math.min(values.length, EMOTION_COUNT);
        for (int i = 0; i < length; i++) {
            this.setEmotion(i, values[i]);
        }
    }

    void loadFlags(byte[] values) {
        int length = Math.min(values.length, this.flags.length);
        for (int i = 0; i < length; i++) {
            this.flags[i] = values[i] != 0;
        }
    }

    /*
     * Transitional storage views for legacy ship subclasses and methods that
     * still use indexed array access. Do not expose these outside the entity
     * package; removing these views is a later migration gate.
     */
    int[] legacyMinorStorage() {
        return this.minor;
    }

    int[] legacyTimerStorage() {
        return this.timer;
    }

    boolean[] legacyFlagStorage() {
        return this.flags;
    }

    boolean[] legacyUpdateFlagStorage() {
        return this.updateFlags;
    }

    ShipEmotionState emotion() {
        return this.emotion;
    }
}
