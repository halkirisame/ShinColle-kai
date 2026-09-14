package com.lulan.shincolle.client;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpdateNoticeStateStoreTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void missingFileIsTreatedAsNoState() {
        Path path = temporaryDirectory.resolve("missing.json");

        UpdateNotificationPolicy.Decision decision = UpdateNotificationPolicy.evaluate(true,
                List.of(1, 2, 4, 8, 16), UpdateNoticeStateStore.read(path).orElse(null),
                "1.20.1-0.9.1", true);

        assertTrue(decision.shouldNotify());
        assertEquals(new UpdateNoticeState("1.20.1-0.9.1", 1, 1), decision.state());
    }

    @Test
    void malformedWrongTypeAndNegativeStatesAreRejected() throws IOException {
        List<String> invalidDocuments = List.of(
                "not json",
                "[]",
                "{\"version\":1,\"launchCount\":1,\"lastNotifiedLaunch\":0}",
                "{\"version\":\"1.20.1-0.9.1\",\"launchCount\":\"1\",\"lastNotifiedLaunch\":0}",
                "{\"version\":\"1.20.1-0.9.1\",\"launchCount\":-1,\"lastNotifiedLaunch\":0}",
                "{\"version\":\"1.20.1-0.9.1\",\"launchCount\":1,\"lastNotifiedLaunch\":-1}");

        for (int index = 0; index < invalidDocuments.size(); index++) {
            Path path = temporaryDirectory.resolve("invalid-" + index + ".json");
            Files.writeString(path, invalidDocuments.get(index));
            assertTrue(UpdateNoticeStateStore.read(path).isEmpty(), invalidDocuments.get(index));
            UpdateNotificationPolicy.Decision decision = UpdateNotificationPolicy.evaluate(true,
                    List.of(1, 2, 4, 8, 16), UpdateNoticeStateStore.read(path).orElse(null),
                    "1.20.1-0.9.1", true);
            assertTrue(decision.shouldNotify(), invalidDocuments.get(index));
            assertEquals(1, decision.state().launchCount(), invalidDocuments.get(index));
        }
    }

    @Test
    void writtenStateCanBeReadBackAndReplaced() {
        Path path = temporaryDirectory.resolve("state.json");
        UpdateNoticeState first = new UpdateNoticeState("1.20.1-0.9.1", 3, 2);
        UpdateNoticeState second = new UpdateNoticeState("1.20.1-0.9.2", 1, 1);

        assertTrue(UpdateNoticeStateStore.write(path, first));
        assertEquals(first, UpdateNoticeStateStore.read(path).orElseThrow());
        assertTrue(UpdateNoticeStateStore.write(path, second));
        assertEquals(second, UpdateNoticeStateStore.read(path).orElseThrow());
    }

    @Test
    void writeFailureDoesNotEscape() {
        Path directoryAsDestination = temporaryDirectory.resolve("destination");
        assertTrue(directoryAsDestination.toFile().mkdir());

        assertFalse(UpdateNoticeStateStore.write(directoryAsDestination,
                new UpdateNoticeState("1.20.1-0.9.1", 1, 1)));
    }
}
