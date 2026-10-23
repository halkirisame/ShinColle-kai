package com.lulan.shincolle.handler;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeferredKillCreditQueueTest {
    @Test
    void duplicateObservationKeepsFirstSnapshot() {
        var queue = new DeferredKillCreditQueue<Object, String>();
        Object victim = new Object();
        queue.offer(victim, "first");
        queue.offer(victim, "second");
        assertEquals("first", queue.drain().get(0).value());
        assertTrue(queue.drain().isEmpty());
    }

    @Test
    void identityDoesNotMergeEqualVictims() {
        var queue = new DeferredKillCreditQueue<String, String>();
        String first = new String("equal");
        String second = new String("equal");
        queue.offer(first, "one");
        queue.offer(second, "two");
        assertEquals(2, queue.drain().size());
        assertTrue(queue.claim(first, DeferredKillCreditQueue.Lane.VANILLA));
        assertTrue(queue.claim(second, DeferredKillCreditQueue.Lane.VANILLA));
    }

    @Test
    void lanesClaimIndependentlyAndOnlyOnce() {
        var queue = new DeferredKillCreditQueue<Object, String>();
        Object victim = new Object();
        assertTrue(queue.claim(victim, DeferredKillCreditQueue.Lane.VANILLA));
        assertFalse(queue.claim(victim, DeferredKillCreditQueue.Lane.VANILLA));
        assertTrue(queue.claim(victim, DeferredKillCreditQueue.Lane.FTB));
        assertFalse(queue.claim(victim, DeferredKillCreditQueue.Lane.FTB));
        queue.offer(victim, "late duplicate");
        assertTrue(queue.drain().isEmpty());
    }

    @Test
    void reentrantObservationSurvivesDetachedDrain() {
        var queue = new DeferredKillCreditQueue<Object, String>();
        queue.offer(new Object(), "first");
        var draining = queue.drain();
        queue.offer(new Object(), "during callback");
        assertEquals(1, draining.size());
        assertEquals("during callback", queue.drain().get(0).value());
    }

    @Test
    void unloadDiscardsOnlyMatchingPendingEntries() {
        var queue = new DeferredKillCreditQueue<Object, String>();
        queue.offer(new Object(), "unloaded");
        queue.offer(new Object(), "loaded");
        queue.discardPending("unloaded"::equals);
        assertEquals("loaded", queue.drain().get(0).value());
    }

    @Test
    void stopClearsPendingAndClaims() {
        var queue = new DeferredKillCreditQueue<Object, String>();
        Object victim = new Object();
        queue.claim(victim, DeferredKillCreditQueue.Lane.VANILLA);
        queue.offer(new Object(), "pending");
        queue.clear();
        assertTrue(queue.drain().isEmpty());
        assertTrue(queue.claim(victim, DeferredKillCreditQueue.Lane.VANILLA));
    }
}
