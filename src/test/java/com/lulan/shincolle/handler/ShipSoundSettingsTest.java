package com.lulan.shincolle.handler;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ShipSoundSettingsTest {

    @Test
    void disabledSoundGroupIsSilent() {
        assertEquals(0.0F, ShipSoundSettings.effectiveVolume(false, 0.75D, 2.0F));
    }

    @Test
    void enabledSoundGroupScalesIncomingVolume() {
        assertEquals(1.5F, ShipSoundSettings.effectiveVolume(true, 0.75D, 2.0F));
    }

    @Test
    void percentConversionUsesTheClientRange() {
        assertEquals(75, ShipSoundSettings.toPercent(0.75D));
        assertEquals(0.75D, ShipSoundSettings.fromPercent(75));
    }
}
