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
}
