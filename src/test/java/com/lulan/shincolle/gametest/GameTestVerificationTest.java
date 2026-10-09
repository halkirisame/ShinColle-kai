package com.lulan.shincolle.gametest;

import net.minecraft.gametest.framework.GameTestAssertException;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GameTestVerificationTest {

    @Test
    void successfulVerificationRunsOnce() {
        AtomicInteger calls = new AtomicInteger();
        GameTestVerification.namedFailure(calls::incrementAndGet).run();
        assertEquals(1, calls.get());
    }

    @Test
    void assertionFailureKeepsItsMessageAndCauseAfterCleanup() {
        AssertionError original = new AssertionError("Fixture target was not acquired");
        AtomicInteger cleanups = new AtomicInteger();
        Runnable deferred = GameTestVerification.namedFailure(() -> {
            try {
                throw original;
            } finally {
                cleanups.incrementAndGet();
            }
        });
        GameTestAssertException failure = assertThrows(GameTestAssertException.class, deferred::run);
        assertEquals(original.getMessage(), failure.getMessage());
        assertSame(original, failure.getCause());
        assertEquals(1, cleanups.get());
    }

    @Test
    void existingNamedFailureIsNotReplaced() {
        GameTestAssertException original = new GameTestAssertException("Already a named failure");
        GameTestAssertException failure = assertThrows(GameTestAssertException.class,
                GameTestVerification.namedFailure(() -> { throw original; })::run);
        assertSame(original, failure);
    }

    @Test
    void runtimeFailureIsNotReplaced() {
        IllegalStateException original = new IllegalStateException("Broken fixture lifecycle");
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                GameTestVerification.namedFailure(() -> { throw original; })::run);
        assertSame(original, failure);
    }

    @Test
    void unrelatedErrorIsNotTurnedIntoAnAssertion() {
        LinkageError original = new LinkageError("Missing runtime class");
        LinkageError failure = assertThrows(LinkageError.class,
                GameTestVerification.namedFailure(() -> { throw original; })::run);
        assertSame(original, failure);
    }
}
