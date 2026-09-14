package com.lulan.shincolle.entity;

/** Pure rules for the hourly timekeeping voice. */
public final class TimekeepingRules {

    /** Sentinel for "no announcement played yet". */
    public static final long NONE = Long.MIN_VALUE;

    private TimekeepingRules() {
    }

    /**
     * Hour (0-23) to announce at this day time, or -1 if nothing should play.
     * <p>
     * Upstream announced whenever {@code dayTime % 1000 == 0}, which fires every
     * tick while the day time stands still on an hour boundary (doDaylightCycle
     * off after {@code /time set day}, or a mod that slows the clock). Each
     * boundary is therefore announced at most once per ship.
     */
    public static int hourToAnnounce(long dayTime, long lastAnnouncedDayTime) {
        if (dayTime % 1000L != 0L || dayTime == lastAnnouncedDayTime) {
            return -1;
        }
        return (int) ((dayTime / 1000L) % 24L);
    }
}
