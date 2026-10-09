package com.lulan.shincolle.ai.domain.task;

import com.lulan.shincolle.ai.domain.LegacyDecodeResult;

/** The work a ship does by itself, as the persisted task id names it. */
public enum TaskKind {
    NONE(0),
    COOKING(1),
    FISHING(2),
    MINING(3),
    CRAFTING(4);

    private final int legacyId;

    TaskKind(int legacyId) {
        this.legacyId = legacyId;
    }

    public int legacyId() {
        return this.legacyId;
    }

    public static LegacyDecodeResult<TaskKind> fromLegacyId(int raw) {
        for (TaskKind kind : values()) {
            if (kind.legacyId == raw) return new LegacyDecodeResult.Known<>(kind);
        }
        return new LegacyDecodeResult.Unknown<>(raw);
    }
}
