package com.lulan.shincolle.gametest;

import net.minecraft.gametest.framework.GameTestAssertException;

/** Adapts a deferred fixture check to the GameTest sequence failure boundary. */
final class GameTestVerification {

    private GameTestVerification() {
    }

    static Runnable namedFailure(Runnable verification) {
        return () -> {
            try {
                verification.run();
            } catch (AssertionError error) {
                GameTestAssertException failure = new GameTestAssertException(error.getMessage());
                failure.initCause(error);
                throw failure;
            }
        };
    }
}
