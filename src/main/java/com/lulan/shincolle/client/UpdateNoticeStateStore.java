package com.lulan.shincolle.client;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

/** Reads and atomically replaces the update-notification state file. */
final class UpdateNoticeStateStore {

    private static final Gson GSON = new Gson();

    private UpdateNoticeStateStore() {
    }

    /** Reads a valid state, returning empty for absent, malformed, or invalid files. */
    static Optional<UpdateNoticeState> read(Path path) {
        try (Reader reader = Files.newBufferedReader(path)) {
            JsonElement root = JsonParser.parseReader(reader);
            if (!root.isJsonObject()) {
                return Optional.empty();
            }
            JsonObject object = root.getAsJsonObject();
            String version = readString(object, "version");
            Integer launchCount = readNonNegativeInt(object, "launchCount");
            Integer lastNotifiedLaunch = readNonNegativeInt(object, "lastNotifiedLaunch");
            if (version == null || launchCount == null || lastNotifiedLaunch == null) {
                return Optional.empty();
            }
            return Optional.of(new UpdateNoticeState(version, launchCount, lastNotifiedLaunch));
        } catch (IOException | RuntimeException exception) {
            return Optional.empty();
        }
    }

    /** Writes through a sibling temporary file and never exposes failures to callers. */
    static boolean write(Path path, UpdateNoticeState state) {
        Path temporary = null;
        try {
            Path parent = path.toAbsolutePath().getParent();
            temporary = Files.createTempFile(parent, path.getFileName().toString(), ".tmp");
            JsonObject object = new JsonObject();
            object.addProperty("version", state.version());
            object.addProperty("launchCount", state.launchCount());
            object.addProperty("lastNotifiedLaunch", state.lastNotifiedLaunch());
            try (Writer writer = Files.newBufferedWriter(temporary)) {
                GSON.toJson(object, writer);
            }
            replace(temporary, path);
            temporary = null;
            return true;
        } catch (IOException | RuntimeException exception) {
            return false;
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException ignored) {
                    // A failed cleanup must not escape the client login event.
                }
            }
        }
    }

    private static void replace(Path temporary, Path destination) throws IOException {
        try {
            Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String readString(JsonObject object, String name) {
        JsonElement value = object.get(name);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            return null;
        }
        return value.getAsString();
    }

    private static Integer readNonNegativeInt(JsonObject object, String name) {
        JsonElement value = object.get(name);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            return null;
        }
        try {
            int number = value.getAsBigDecimal().intValueExact();
            return number >= 0 ? number : null;
        } catch (ArithmeticException exception) {
            return null;
        }
    }
}
