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
    void everyStoredValueAndTestOverrideRunsTheNewAi() {
        Path path = directory.resolve("compat.toml");
        try {
            try (CommentedFileConfig config = CommentedFileConfig.builder(path).sync().build()) {
                config.load();
                ConfigHandler.COMMON_SPEC.setConfig(config);
                for (ConfigHandler.ShipAiTargetAuthority stored : ConfigHandler.ShipAiTargetAuthority.values()) {
                    ConfigHandler.COMMON.shipAiTargetAuthority.set(stored);
                    assertEquals(stored, ConfigHandler.COMMON.shipAiTargetAuthority.get());
                    assertEquals(ConfigHandler.ShipAiTargetAuthority.NEW, ConfigHandler.shipAiTargetAuthority(),
                            "stored " + stored);
                    for (ConfigHandler.ShipAiTargetAuthority override : ConfigHandler.ShipAiTargetAuthority.values()) {
                        ConfigHandler.setShipAiTargetAuthorityForTest(override);
                        assertEquals(ConfigHandler.ShipAiTargetAuthority.NEW, ConfigHandler.shipAiTargetAuthority(),
                                "stored " + stored + ", override " + override);
                    }
                    ConfigHandler.setShipAiTargetAuthorityForTest(null);
                    assertEquals(ConfigHandler.ShipAiTargetAuthority.NEW, ConfigHandler.shipAiTargetAuthority(),
                            "stored " + stored + ", no override");
                }
            }
        } finally {
            ConfigHandler.setShipAiTargetAuthorityForTest(null);
            ConfigHandler.COMMON_SPEC.setConfig(null);
        }
    }

    @Test
    void storedSettingsAndMigrationMarkersLoadWithoutSelectingAnOldRuntime() {
        int fixture = 0;
        try {
            for (ConfigHandler.ShipAiTargetAuthority stored : ConfigHandler.ShipAiTargetAuthority.values()) {
                for (Boolean marker : new Boolean[]{null, false, true}) {
                    Path path = directory.resolve("loaded-compat-" + fixture++ + ".toml");
                    try (CommentedFileConfig config = CommentedFileConfig.builder(path).sync().build()) {
                        config.load();
                        config.set(ConfigHandler.COMMON.shipAiTargetAuthority.getPath(), stored.name());
                        if (marker != null) {
                            config.set(ConfigHandler.COMMON.shipAiNewDefaultApplied.getPath(), marker);
                        }
                        config.save();
                    }
                    try (CommentedFileConfig config = CommentedFileConfig.builder(path).sync().build()) {
                        config.load();
                        assertEquals(marker != null,
                                config.contains(ConfigHandler.COMMON.shipAiNewDefaultApplied.getPath()));
                        ConfigHandler.COMMON_SPEC.setConfig(config);
                        assertEquals(stored, ConfigHandler.COMMON.shipAiTargetAuthority.get());
                        assertEquals(Boolean.TRUE.equals(marker), ConfigHandler.COMMON.shipAiNewDefaultApplied.get());
                        for (ConfigHandler.ShipAiTargetAuthority override : ConfigHandler.ShipAiTargetAuthority.values()) {
                            ConfigHandler.setShipAiTargetAuthorityForTest(override);
                            assertEquals(ConfigHandler.ShipAiTargetAuthority.NEW, ConfigHandler.shipAiTargetAuthority());
                        }
                        ConfigHandler.setShipAiTargetAuthorityForTest(null);
                        ConfigHandler.applyShipAiNewDefault();
                        assertEquals(ConfigHandler.ShipAiTargetAuthority.NEW, ConfigHandler.shipAiTargetAuthority());
                    } finally {
                        ConfigHandler.COMMON_SPEC.setConfig(null);
                    }
                }
            }
        } finally {
            ConfigHandler.setShipAiTargetAuthorityForTest(null);
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
