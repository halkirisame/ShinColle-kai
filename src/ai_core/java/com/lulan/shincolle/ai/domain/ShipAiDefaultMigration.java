package com.lulan.shincolle.ai.domain;

import java.util.Objects;

/** Applies the default authority once, then preserves explicit configuration choices. */
public final class ShipAiDefaultMigration {
    private ShipAiDefaultMigration() {
    }

    public static <T> Result<T> apply(boolean applied, T current, T newDefault) {
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(newDefault, "newDefault");
        return applied ? new Result<>(current, true, false)
                : new Result<>(newDefault, true, true);
    }

    public record Result<T>(T authority, boolean applied, boolean migrated) {
    }
}
