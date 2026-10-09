package com.lulan.shincolle.handler;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShipAiDefaultConfigTest {
    @TempDir
    Path directory;

    @Test
    void absentConfigReceivesNewAndPersistsTheMarker() {
        Path path = directory.resolve("new.toml");
        try {
            try (CommentedFileConfig config = CommentedFileConfig.builder(path).sync().build()) {
                config.load();
                ConfigHandler.COMMON_SPEC.setConfig(config);
                assertEquals(ConfigHandler.ShipAiTargetAuthority.NEW, ConfigHandler.COMMON.shipAiTargetAuthority.get());
                assertTrue(ConfigHandler.applyShipAiNewDefault());
                config.save();
            }
            try (CommentedFileConfig config = CommentedFileConfig.builder(path).sync().build()) {
                config.load();
                ConfigHandler.COMMON_SPEC.setConfig(config);
                assertTrue(ConfigHandler.COMMON.shipAiNewDefaultApplied.get());
                assertFalse(ConfigHandler.applyShipAiNewDefault());
            }
        } finally {
            ConfigHandler.COMMON_SPEC.setConfig(null);
        }
    }

    @Test
    void legacyMigrationPreservesOtherSettingsAndAChoiceMadeAfterwards() {
        Path path = directory.resolve("existing.toml");
        try {
            try (CommentedFileConfig config = CommentedFileConfig.builder(path).sync().build()) {
                config.load();
                ConfigHandler.COMMON_SPEC.setConfig(config);
                ConfigHandler.COMMON.shipAiTargetAuthority.set(ConfigHandler.ShipAiTargetAuthority.LEGACY);
                ConfigHandler.COMMON.debugMode.set(true);
                assertTrue(ConfigHandler.applyShipAiNewDefault());
                assertEquals(ConfigHandler.ShipAiTargetAuthority.NEW, ConfigHandler.COMMON.shipAiTargetAuthority.get());
                assertTrue(ConfigHandler.COMMON.debugMode.get());
                ConfigHandler.COMMON.shipAiTargetAuthority.set(ConfigHandler.ShipAiTargetAuthority.LEGACY);
                config.save();
            }
            try (CommentedFileConfig config = CommentedFileConfig.builder(path).sync().build()) {
                config.load();
                ConfigHandler.COMMON_SPEC.setConfig(config);
                assertFalse(ConfigHandler.applyShipAiNewDefault());
                assertEquals(ConfigHandler.ShipAiTargetAuthority.LEGACY, ConfigHandler.COMMON.shipAiTargetAuthority.get());
                assertTrue(ConfigHandler.COMMON.debugMode.get());
            }
        } finally {
            ConfigHandler.COMMON_SPEC.setConfig(null);
        }
    }
}
