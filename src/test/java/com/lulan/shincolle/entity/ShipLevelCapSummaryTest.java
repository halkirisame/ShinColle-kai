package com.lulan.shincolle.entity;

import com.lulan.shincolle.client.ClientShipLevelCaps;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShipLevelCapSummaryTest {

    @AfterEach
    void clearClientCache() {
        ClientShipLevelCaps.clear();
    }

    @Test
    void defaultCapsComeFromShipLevelRules() {
        assertEquals(ShipLevelRules.DEFAULT_UNMARRIED_CAP, ShipLevelCapSummary.DEFAULT.unmarriedCap());
        assertEquals(ShipLevelRules.DEFAULT_ABSOLUTE_CAP, ShipLevelCapSummary.DEFAULT.absoluteCap());
    }

    @Test
    void configuredCapsPreserveValuesAndAbsoluteThreshold() {
        ShipLevelCapSummary summary = new ShipLevelCapSummary(600, 1000);

        assertEquals(600, summary.unmarriedCap());
        assertEquals(1000, summary.absoluteCap());
        assertEquals(600, summary.effectiveUnmarriedCap());
        assertFalse(summary.isAbsoluteCapReached(999));
        assertTrue(summary.isAbsoluteCapReached(1000));
        assertTrue(summary.isAbsoluteCapReached(1001));
    }

    @Test
    void unmarriedCapMayExceedAbsoluteCap() {
        ShipLevelCapSummary summary = new ShipLevelCapSummary(1200, 1000);

        assertEquals(1200, summary.unmarriedCap());
        assertEquals(1000, summary.absoluteCap());
        assertEquals(1000, summary.effectiveUnmarriedCap());
    }

    @Test
    void nonPositiveCapsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new ShipLevelCapSummary(0, 1000));
        assertThrows(IllegalArgumentException.class, () -> new ShipLevelCapSummary(-1, 1000));
        assertThrows(IllegalArgumentException.class, () -> new ShipLevelCapSummary(600, 0));
        assertThrows(IllegalArgumentException.class, () -> new ShipLevelCapSummary(600, -1));
    }

    @Test
    void clientCacheInstallsAndClearsAsOneSummary() {
        ShipLevelCapSummary configured = new ShipLevelCapSummary(600, 1000);
        ClientShipLevelCaps.install(configured);
        assertSame(configured, ClientShipLevelCaps.current());

        ClientShipLevelCaps.clear();
        assertSame(ShipLevelCapSummary.DEFAULT, ClientShipLevelCaps.current());
    }

    @Test
    void synchronizedAbsoluteCapControlsTheGoldThreshold() {
        ShipLevelCapSummary configured = new ShipLevelCapSummary(600, 1000);
        assertFalse(configured.isAbsoluteCapReached(150));
        assertTrue(configured.isAbsoluteCapReached(1000));
        assertTrue(ShipLevelCapSummary.DEFAULT.isAbsoluteCapReached(150));
    }
}
