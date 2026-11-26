package com.lulan.shincolle.handler;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RemovedPolymetalConfigTest {
    @TempDir
    Path directory;

    @Test
    void enabledRemovedOptionLoadsWithoutLosingOtherSettings() {
        checkOldConfig(true);
    }

    @Test
    void disabledRemovedOptionLoadsWithoutLosingOtherSettings() {
        checkOldConfig(false);
    }

    private void checkOldConfig(boolean oldValue) {
        Path path = directory.resolve("existing-" + oldValue + ".toml");
        var removedPath = List.of("general", "polymetalAsMn");
        try {
            try (CommentedFileConfig config = CommentedFileConfig.builder(path).sync().build()) {
                config.load();
                config.set(removedPath, oldValue);
                config.set(ConfigHandler.COMMON.debugMode.getPath(), true);
                config.set(ConfigHandler.COMMON.easyMode.getPath(), true);
                config.save();
            }
            try (CommentedFileConfig config = CommentedFileConfig.builder(path).sync().build()) {
                config.load();
                assertEquals(oldValue, config.get(removedPath));
                ConfigHandler.COMMON_SPEC.setConfig(config);
                assertTrue(ConfigHandler.COMMON.debugMode.get());
                assertTrue(ConfigHandler.COMMON.easyMode.get());
                assertFalse(config.contains(removedPath));
                config.save();
            }
            try (CommentedFileConfig config = CommentedFileConfig.builder(path).sync().build()) {
                config.load();
                ConfigHandler.COMMON_SPEC.setConfig(config);
                assertTrue(ConfigHandler.COMMON.debugMode.get());
                assertTrue(ConfigHandler.COMMON.easyMode.get());
                assertFalse(config.contains(removedPath));
            }
        } finally {
            ConfigHandler.COMMON_SPEC.setConfig(null);
        }
    }
}
