package com.lulan.shincolle.handler;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/** Server-thread queue with weak, identity-based, at-most-once claims for each accounting lane. */
final class DeferredKillCreditQueue<K, V> {
    enum Lane { VANILLA, FTB }

    record Entry<K, V>(K key, V value) { }

    private final IdentityHashMap<K, V> pending = new IdentityHashMap<>();
    private final ReferenceQueue<K> collected = new ReferenceQueue<>();
    private final Map<IdentityReference<K>, EnumSet<Lane>> processed = new HashMap<>();

    void offer(K key, V value) {
        reap();
        if (!processed.containsKey(new IdentityReference<>(key, null))) {
            pending.putIfAbsent(key, value);
        }
    }

    List<Entry<K, V>> drain() {
        reap();
        List<Entry<K, V>> entries = new ArrayList<>(pending.size());
        pending.forEach((key, value) -> entries.add(new Entry<>(key, value)));
        pending.clear();
        return entries;
    }

    boolean claim(K key, Lane lane) {
        reap();
        return processed.computeIfAbsent(new IdentityReference<>(key, collected),
                ignored -> EnumSet.noneOf(Lane.class)).add(lane);
    }

    void discardPending(Predicate<V> predicate) {
        pending.values().removeIf(predicate);
    }

    void clear() {
        pending.clear();
        processed.clear();
        reap();
    }

    private void reap() {
        java.lang.ref.Reference<? extends K> reference;
        while ((reference = collected.poll()) != null) {
            processed.remove(reference);
        }
    }

    private static final class IdentityReference<T> extends WeakReference<T> {
        private final int hash;

        IdentityReference(T value, ReferenceQueue<T> queue) {
            super(value, queue);
            hash = System.identityHashCode(value);
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            return other instanceof IdentityReference<?> reference && get() != null && get() == reference.get();
        }
    }
}
