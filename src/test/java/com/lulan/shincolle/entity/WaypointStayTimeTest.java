package com.lulan.shincolle.entity;

import com.lulan.shincolle.utility.WaypointStayTime;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WaypointStayTimeTest {

    @Test
    void rawWaypointStayValuesUseLegacyPiecewiseDurations() {
        assertEquals(0, WaypointStayTime.toTicks(0));
        assertEquals(100, WaypointStayTime.toTicks(1));
        assertEquals(500, WaypointStayTime.toTicks(5));
        assertEquals(1200, WaypointStayTime.toTicks(6));
        assertEquals(6000, WaypointStayTime.toTicks(10));
        assertEquals(12000, WaypointStayTime.toTicks(11));
        assertEquals(72000, WaypointStayTime.toTicks(16));
    }

    @Test
    void outOfRangeWaypointStayValuesDoNotCreateWaits() {
        assertEquals(0, WaypointStayTime.toTicks(-1));
        assertEquals(0, WaypointStayTime.toTicks(17));
    }

    @Test
    void displayShowsSecondsBelowAMinuteAndMinutesFromAMinuteOn() {
        assertEquals(new WaypointStayTime.Display(0, false), WaypointStayTime.display(0));
        assertEquals(new WaypointStayTime.Display(5, false), WaypointStayTime.display(1));
        assertEquals(new WaypointStayTime.Display(25, false), WaypointStayTime.display(5));
        assertEquals(new WaypointStayTime.Display(1, true), WaypointStayTime.display(6));
        assertEquals(new WaypointStayTime.Display(5, true), WaypointStayTime.display(10));
        assertEquals(new WaypointStayTime.Display(10, true), WaypointStayTime.display(11));
        assertEquals(new WaypointStayTime.Display(60, true), WaypointStayTime.display(16));
    }

    @Test
    void displayNamesTheUnitByTranslationKey() {
        assertEquals("gui.shincolle_kai.time.seconds", WaypointStayTime.display(1).translationKey());
        assertEquals("gui.shincolle_kai.time.minutes", WaypointStayTime.display(6).translationKey());
    }
}
