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
        assertEquals(1 << 25, mask.unknownBits());
        assertEquals(1 << 19 | 1 << 20 | 1 << 25 | 0x3F, mask.raw());
        assertEquals(0, new TaskSideMask(0x1FFFFF).unknownBits());
    }

    @Test
    void oreMatchingUsesOnlyBit19AndRetainsLegacyMasks() {
        assertTrue(new TaskSideMask(1 << 19).checkOreTag());
        assertFalse(new TaskSideMask(0x17FFFF).checkOreTag());
        assertFalse(new TaskSideMask(1 << 19).checkMetadata());
        assertFalse(new TaskSideMask(1 << 19).checkNbt());
        for (int raw : new int[] {0, 0x17FFFF, 0x1FFFFF, 1 << 19 | 1 << 25}) {
            TaskSideMask mask = new TaskSideMask(raw);
            assertEquals(raw, mask.raw());
            assertEquals((raw & (1 << 19)) != 0, mask.checkOreTag());
            assertEquals((raw & (1 << 18)) != 0, mask.checkMetadata());
            assertEquals((raw & (1 << 20)) != 0, mask.checkNbt());
            assertEquals(raw & ~0x1FFFFF, mask.unknownBits());
        }
    }
}
