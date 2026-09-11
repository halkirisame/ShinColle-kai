package com.lulan.shincolle.utility;

import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Writes the TASK-106 model float measurements to their own file.
 * <p>
 * These lines are a measuring instrument, not diagnostics to read alongside everything else.
 * A session produces one line per ship per state, and mixing them into {@code latest.log}
 * buries them among thousands of unrelated entries and forces a grep that has to survive the
 * CP932 date mangling. They go to {@code logs/model-float.log} instead, appended across runs
 * with a header line per run so separate sessions stay distinguishable.
 * <p>
 * Offset fields describe configured translation only, not contact height or a correction
 * shortfall. TASK-106 probe-v2 also records geometry and pose inputs, pending client calibration.
 */
public final class ModelFloatLog {

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("HH:mm:ss");

    private static Path path;
    private static boolean unavailable;

    private ModelFloatLog() {
    }

    private static Path resolve() {
        if (path == null && !unavailable) {
            try {
                Path logs = FMLPaths.GAMEDIR.get().resolve("logs");
                Files.createDirectories(logs);
                path = logs.resolve("model-float.log");
                write("=== run started " + LocalDateTime.now().format(STAMP) + " ===");
            } catch (IOException | RuntimeException e) {
                // Never let a measuring instrument take the game down.
                unavailable = true;
            }
        }
        return path;
    }

    /** Appends one measurement. Silently does nothing if the file cannot be opened. */
    public static void log(String line) {
        try {
            if (!LogHelper.diagEnabled() || unavailable) {
                return;
            }
            Path target = resolve();
            if (target != null) {
                write(line);
            }
        } catch (Exception | LinkageError ignored) {
            unavailable = true;
            path = null;
        }
    }

    private static void write(String line) {
        try {
            Files.writeString(path, line + System.lineSeparator(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException | RuntimeException e) {
            unavailable = true;
            path = null;
        }
    }
}
