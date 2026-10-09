package com.lulan.shincolle.ai.domain.waypoint;

import com.lulan.shincolle.ai.domain.LegacyDecodeResult;

/** The 0..16 stay setting of a waypoint or a ship, and how long it is in ticks. */
public record WaypointStaySetting(int value) {
    public static final int MAX = 16;

    public WaypointStaySetting {
        if (value < 0 || value > MAX) {
            throw new IllegalArgumentException("Waypoint stay setting out of range: " + value);
        }
    }

    public static LegacyDecodeResult<WaypointStaySetting> fromLegacy(int raw) {
        return raw < 0 || raw > MAX ? new LegacyDecodeResult.Unknown<>(raw)
                : new LegacyDecodeResult.Known<>(new WaypointStaySetting(raw));
    }

    /** 1-5 are 100 ticks each, 6-10 a minute each, 11-16 ten minutes each; 0 waits for nothing. */
    public int ticks() {
        return switch (this.value) {
            case 1, 2, 3, 4, 5 -> this.value * 100;
            case 6, 7, 8, 9, 10 -> (this.value - 5) * 1200;
            case 11, 12, 13, 14, 15, 16 -> (this.value - 10) * 12000;
            default -> 0;
        };
    }
}
