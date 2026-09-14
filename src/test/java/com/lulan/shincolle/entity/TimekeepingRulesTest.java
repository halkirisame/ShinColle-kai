package com.lulan.shincolle.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TimekeepingRulesTest {

    @Test
    void announcesOnlyOnHourBoundaries() {
        assertEquals(0, TimekeepingRules.hourToAnnounce(0L, TimekeepingRules.NONE));
        assertEquals(1, TimekeepingRules.hourToAnnounce(1000L, TimekeepingRules.NONE));
        assertEquals(23, TimekeepingRules.hourToAnnounce(23000L, TimekeepingRules.NONE));
        assertEquals(0, TimekeepingRules.hourToAnnounce(24000L, TimekeepingRules.NONE));
        assertEquals(-1, TimekeepingRules.hourToAnnounce(1001L, TimekeepingRules.NONE));
        assertEquals(-1, TimekeepingRules.hourToAnnounce(999L, TimekeepingRules.NONE));
    }

    @Test
    void frozenClockOnBoundaryAnnouncesOnce() {
        long last = TimekeepingRules.NONE;
        int played = 0;
        for (int tick = 0; tick < 100; tick++) {
            int hour = TimekeepingRules.hourToAnnounce(1000L, last);
            if (hour >= 0) {
                played++;
                last = 1000L;
            }
        }
        assertEquals(1, played);
    }

    @Test
    void runningClockStillAnnouncesEveryHourAndNextDay() {
        long last = TimekeepingRules.NONE;
        int played = 0;
        for (long time = 0L; time < 48000L; time++) {
            if (TimekeepingRules.hourToAnnounce(time, last) >= 0) {
                played++;
                last = time;
            }
        }
        assertEquals(48, played);
    }
}
