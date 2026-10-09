package com.lulan.shincolle.ai.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AiDataVersionTest {

    @Test
    void currentVersionIsOne() {
        assertEquals(1, AiDataVersion.CURRENT);
        assertEquals("AiDataVersion", AiDataVersion.KEY);
    }

    @Test
    void missingMarkIsAnUnmarkedSave() {
        assertEquals(new AiDataVersion.Unmarked(), AiDataVersion.read(false, false, 0));
        // without a key the other inputs carry no meaning
        assertEquals(new AiDataVersion.Unmarked(), AiDataVersion.read(false, true, 7));
    }

    @Test
    void currentMarkIsKnown() {
        assertEquals(new AiDataVersion.Known(), AiDataVersion.read(true, true, 1));
    }

    @Test
    void zeroAndNegativeMarksAreInvalid() {
        assertEquals(new AiDataVersion.Invalid("0"), AiDataVersion.read(true, true, 0));
        assertEquals(new AiDataVersion.Invalid("-1"), AiDataVersion.read(true, true, -1));
        assertEquals(new AiDataVersion.Invalid(Integer.toString(Integer.MIN_VALUE)),
                AiDataVersion.read(true, true, Integer.MIN_VALUE));
    }

    @Test
    void newerMarksAreFutureVersions() {
        assertEquals(new AiDataVersion.Future(2), AiDataVersion.read(true, true, 2));
        assertEquals(new AiDataVersion.Future(Integer.MAX_VALUE),
                AiDataVersion.read(true, true, Integer.MAX_VALUE));
    }

    @Test
    void markOfAnotherTypeIsInvalid() {
        assertEquals(new AiDataVersion.Invalid("not an integer"), AiDataVersion.read(true, false, 0));
        // a non-integer never counts as a version, whatever number sits beside it
        assertEquals(new AiDataVersion.Invalid("not an integer"), AiDataVersion.read(true, false, 1));
    }
}
