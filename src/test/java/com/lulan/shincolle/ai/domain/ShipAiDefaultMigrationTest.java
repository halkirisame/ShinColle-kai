package com.lulan.shincolle.ai.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ShipAiDefaultMigrationTest {
    private enum Mode { LEGACY, NEW }

    @Test
    void anUnmarkedLegacyConfigSwitchesToNew() {
        assertEquals(new ShipAiDefaultMigration.Result<>(Mode.NEW, true, true),
                ShipAiDefaultMigration.apply(false, Mode.LEGACY, Mode.NEW));
    }

    @Test
    void anUnmarkedNewConfigReceivesTheMarker() {
        assertEquals(new ShipAiDefaultMigration.Result<>(Mode.NEW, true, true),
                ShipAiDefaultMigration.apply(false, Mode.NEW, Mode.NEW));
    }

    @Test
    void aMarkedLegacyConfigPreservesTheManualChoice() {
        assertEquals(new ShipAiDefaultMigration.Result<>(Mode.LEGACY, true, false),
                ShipAiDefaultMigration.apply(true, Mode.LEGACY, Mode.NEW));
    }

    @Test
    void aMarkedNewConfigDoesNotRepeatTheMigration() {
        assertEquals(new ShipAiDefaultMigration.Result<>(Mode.NEW, true, false),
                ShipAiDefaultMigration.apply(true, Mode.NEW, Mode.NEW));
    }
}
