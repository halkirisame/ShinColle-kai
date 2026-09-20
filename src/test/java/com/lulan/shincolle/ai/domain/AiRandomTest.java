package com.lulan.shincolle.ai.domain;

import org.junit.jupiter.api.Test;

import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AiRandomTest {

    @Test
    void checkedDrawPreservesSeededProviderValuesAndConsumption() {
        Random expected = new Random(69L);
        Random actual = new Random(69L);
        AiRandom random = AiRandom.checked(actual::nextInt);
        for (int i = 0; i < 100; i++) {
            for (int bound : new int[]{1, 3, Integer.MAX_VALUE}) {
                assertEquals(expected.nextInt(bound), random.nextBoundedInt(bound));
            }
        }
    }

    @Test
    void invalidBoundsAreRejectedBeforeCallingProvider() {
        AtomicInteger calls = new AtomicInteger();
        AiRandom random = AiRandom.checked(bound -> calls.getAndIncrement());
        for (int bound : new int[]{0, -1, Integer.MIN_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> random.nextBoundedInt(bound));
        }
        assertEquals(0, calls.get());
    }

    @Test
    void invalidResultsAreRejectedWithoutRetryOrCorrection() {
        for (int bound : new int[]{1, 3}) {
            for (int invalid : new int[]{-1, bound, Integer.MAX_VALUE}) {
                AtomicInteger calls = new AtomicInteger();
                AiRandom random = AiRandom.checked(requestedBound -> {
                    assertEquals(bound, requestedBound);
                    calls.incrementAndGet();
                    return invalid;
                });
                assertThrows(IllegalArgumentException.class, () -> random.nextBoundedInt(bound));
                assertEquals(1, calls.get());
            }
        }
    }

    @Test
    void checkedSourceRejectsNull() {
        assertThrows(NullPointerException.class, () -> AiRandom.checked(null));
    }
}
