package com.lulan.shincolle.ai.domain.task;

import com.lulan.shincolle.ai.domain.LegacyDecodeResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskDecodeTest {
    @Test
    void taskIdsZeroToFourDecode() {
        TaskKind[] expected = {TaskKind.NONE, TaskKind.COOKING, TaskKind.FISHING, TaskKind.MINING,
                TaskKind.CRAFTING};
        for (int raw = 0; raw < expected.length; raw++) {
            assertEquals(new LegacyDecodeResult.Known<>(expected[raw]), TaskKind.fromLegacyId(raw));
            assertEquals(raw, expected[raw].legacyId());
        }
    }

    @Test
    void anUnknownTaskIdKeepsItsRawValue() {
        assertEquals(new LegacyDecodeResult.Unknown<TaskKind>(5), TaskKind.fromLegacyId(5));
        assertEquals(new LegacyDecodeResult.Unknown<TaskKind>(-1), TaskKind.fromLegacyId(-1));
    }

    @Test
    void faceBitsAreGroupedBySixInInputOutputFuelOrder() {
        TaskSideMask mask = new TaskSideMask(1 << 0 | 1 << 7 | 1 << 17);
        assertTrue(mask.face(0, 0));
        assertTrue(mask.face(1, 1));
        assertTrue(mask.face(2, 5));
        assertFalse(mask.face(0, 1));
        assertFalse(mask.face(1, 0));
        assertFalse(mask.face(2, 4));
        assertThrows(IllegalArgumentException.class, () -> mask.face(3, 0));
        assertThrows(IllegalArgumentException.class, () -> mask.face(0, 6));
    }

    @Test
    void metadataAndNbtChecksAreBits18And20() {
        assertTrue(new TaskSideMask(1 << 18).checkMetadata());
        assertFalse(new TaskSideMask(1 << 18).checkNbt());
        assertTrue(new TaskSideMask(1 << 20).checkNbt());
        assertFalse(new TaskSideMask(1 << 20).checkMetadata());
        assertFalse(new TaskSideMask(1 << 19).checkMetadata());
    }

    @Test
    void bitsOutsideTheKnownLayoutAreReportedAndNotLost() {
        TaskSideMask mask = new TaskSideMask(1 << 19 | 1 << 20 | 1 << 25 | 0x3F);
        assertEquals(1 << 19 | 1 << 25, mask.unknownBits());
        assertEquals(1 << 19 | 1 << 20 | 1 << 25 | 0x3F, mask.raw());
        assertEquals(0, new TaskSideMask(0x17FFFF).unknownBits());
    }
}
