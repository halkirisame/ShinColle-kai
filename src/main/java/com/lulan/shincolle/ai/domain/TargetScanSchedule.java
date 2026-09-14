package com.lulan.shincolle.ai.domain;

import java.util.Objects;

public record TargetScanSchedule(long nextScanTick) {
    public TargetScanSchedule {
        if (nextScanTick < 0) {
            throw new IllegalArgumentException("Next scan tick must not be negative");
        }
    }

    public static TargetScanSchedule initial(
            TargetHandle source, long currentTick, int intervalTicks) {
        Objects.requireNonNull(source, "source");
        requireTick(currentTick);
        int offset = phaseOffset(source, intervalTicks);
        return new TargetScanSchedule(Math.addExact(currentTick, offset));
    }

    public boolean isDue(long currentTick) {
        requireTick(currentTick);
        return currentTick >= this.nextScanTick;
    }

    public TargetScanSchedule advanceFrom(long currentTick, int intervalTicks) {
        requireTick(currentTick);
        requireInterval(intervalTicks);
        return new TargetScanSchedule(Math.addExact(currentTick, intervalTicks));
    }

    public static int phaseOffset(TargetHandle source, int intervalTicks) {
        Objects.requireNonNull(source, "source");
        requireInterval(intervalTicks);
        int dimensionHash = 31 * source.dimension().namespace().hashCode()
                + source.dimension().path().hashCode();
        int seed = 31 * dimensionHash + source.uuid().hashCode();
        return Math.floorMod(seed, intervalTicks);
    }

    private static void requireInterval(int intervalTicks) {
        if (intervalTicks <= 0) {
            throw new IllegalArgumentException("Scan interval must be positive");
        }
    }

    private static void requireTick(long tick) {
        if (tick < 0) {
            throw new IllegalArgumentException("Scan tick must not be negative");
        }
    }
}
