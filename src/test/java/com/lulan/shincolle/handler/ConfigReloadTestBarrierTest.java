package com.lulan.shincolle.handler;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigReloadTestBarrierTest {
    @Test
    void foreignReloadWaitsUntilCachedOverridesHaveBeenRestored() throws InterruptedException {
        Object monitor = new Object();
        ConfigReloadTestBarrier barrier = new ConfigReloadTestBarrier(monitor);
        CountDownLatch entered = new CountDownLatch(1);
        AtomicBoolean synchronizedConfig = new AtomicBoolean();
        Thread reload = new Thread(() -> {
            synchronized (monitor) {
                entered.countDown();
                barrier.awaitReload();
                synchronizedConfig.set(true);
            }
        }, "config-reload-test");
        barrier.begin();
        try {
            reload.start();
            assertTrue(entered.await(5, TimeUnit.SECONDS), "reload did not enter its synchronized method");
            synchronized (monitor) {
                // Acquiring this monitor proves the reload has reached wait(), not merely been scheduled late.
                assertFalse(synchronizedConfig.get(), "reload overwrote an active config fixture");
            }
            barrier.awaitReload();
            assertFalse(synchronizedConfig.get(), "an explicit test-thread sync must not release the watcher");
        } finally {
            barrier.end();
            reload.join(5_000);
        }
        assertFalse(reload.isAlive(), "reload did not finish after the test barrier ended");
        assertTrue(synchronizedConfig.get());
    }

    @Test
    void ordinaryReloadsDoNotNeedATestOwner() throws InterruptedException {
        ConfigReloadTestBarrier barrier = new ConfigReloadTestBarrier(new Object());
        AtomicBoolean synchronizedConfig = new AtomicBoolean();
        Thread reload = new Thread(() -> {
            barrier.awaitReload();
            synchronizedConfig.set(true);
        }, "ordinary-config-reload-test");
        try {
            reload.start();
            reload.join(5_000);
        } finally {
            barrier.end();
            reload.join(5_000);
        }
        assertFalse(reload.isAlive());
        assertTrue(synchronizedConfig.get());
    }
}
