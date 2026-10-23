package com.lulan.shincolle.handler;

/** Internal test support: defer foreign-thread reloads while a test server owns cached settings. */
public final class ConfigReloadTestBarrier {
    private final Object monitor;
    private Thread owner;

    public ConfigReloadTestBarrier(Object monitor) {
        this.monitor = monitor;
    }

    /** Start before any tests; use the same monitor as the synchronized config reader. */
    public void begin() {
        synchronized (this.monitor) {
            if (this.owner != null) {
                throw new IllegalStateException("Config test barrier already active");
            }
            this.owner = Thread.currentThread();
        }
    }

    /** Release on server shutdown, including failed test runs. */
    public void end() {
        synchronized (this.monitor) {
            this.owner = null;
            this.monitor.notifyAll();
        }
    }

    /** Explicit synchronization by the test thread remains available. */
    public void awaitReload() {
        synchronized (this.monitor) {
            boolean interrupted = false;
            while (this.owner != null && this.owner != Thread.currentThread()) {
                try {
                    this.monitor.wait();
                } catch (InterruptedException interruption) {
                    interrupted = true;
                }
            }
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
