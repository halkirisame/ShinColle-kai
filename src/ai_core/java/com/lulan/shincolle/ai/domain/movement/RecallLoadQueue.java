package com.lulan.shincolle.ai.domain.movement;

import com.lulan.shincolle.ai.domain.DimensionKey;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Bounded, shared chunk leases. It knows neither Minecraft loading nor an I/O future. */
public final class RecallLoadQueue {
    public static final Budget DEFAULT = new Budget(1_200L, 120_000_000_000L, 4, 16, 64);
    public static final int TICKET_TIMEOUT = 100;
    public static final int TICKET_REFRESH = 20;

    public record Budget(long ticks, long nanos, int perOwner, int global, int queuedPerOwner) {
        public Budget {
            if (ticks <= 0 || nanos <= 0 || perOwner <= 0 || global <= 0 || queuedPerOwner <= 0) {
                throw new IllegalArgumentException("Positive recall budgets required");
            }
        }
    }

    public record Clock(long tick, long nanos) {
        public boolean elapsed(Clock start, Budget budget) {
            return tick - start.tick >= budget.ticks || nanos - start.nanos >= budget.nanos;
        }
    }

    public record Chunk(DimensionKey dimension, int x, int z) { }
    public record Lease(long generation, Chunk chunk, Clock started) { }
    public enum Observation { PENDING, REGISTERED, ENTITIES_LOADED_WITHOUT_SHIP }
    public enum Result { WAIT, REGISTERED, ABSENT_AFTER_LOAD, LOAD_BUDGET_EXCEEDED, QUEUE_BUDGET_EXCEEDED, ROSTER_EXPIRED, CANCELLED }
    public enum Phase { QUEUED, LOADING }
    public record View(Phase phase, Chunk chunk, Clock queued, Optional<Clock> firstLoad) { }

    private static final class Attempt {
        private final Clock queued;
        private Optional<Clock> firstLoad = Optional.empty();
        private Attempt(Clock queued) { this.queued = queued; }
    }

    private final Budget budget;
    private final Map<RecallRequestKey, Attempt> attempts = new HashMap<>();
    private final Map<RecallRequestKey, Chunk> waiting = new LinkedHashMap<>();
    private final Map<Chunk, Lease> leases = new LinkedHashMap<>();
    private final Map<RecallRequestKey, Lease> loading = new LinkedHashMap<>();
    private long generation;
    private int nextOwner;

    public RecallLoadQueue() { this(DEFAULT); }
    public RecallLoadQueue(Budget budget) { this.budget = budget; }

    public boolean enqueue(RecallRequestKey key, Chunk chunk, Clock now) {
        if (waiting.containsKey(key)) return true;
        long queued = waiting.keySet().stream().filter(k -> k.owner().equals(key.owner()) && !loading.containsKey(k)).count();
        if (queued >= budget.queuedPerOwner) return false;
        attempts.computeIfAbsent(key, ignored -> new Attempt(now));
        waiting.put(key, chunk);
        return true;
    }

    /** One FIFO request per owner per round; sharing a lease does not extend its original budget. */
    public void promote(Clock now) {
        List<UUID> owners = waiting.keySet().stream().filter(k -> !loading.containsKey(k))
                .map(RecallRequestKey::owner).distinct().toList();
        if (owners.isEmpty()) return;
        int start = Math.floorMod(nextOwner++, owners.size());
        boolean progress;
        do {
            progress = false;
            for (int i = 0; i < owners.size(); i++) {
                UUID owner = owners.get((start + i) % owners.size());
                RecallRequestKey key = waiting.keySet().stream()
                        .filter(k -> k.owner().equals(owner) && !loading.containsKey(k)).findFirst().orElse(null);
                if (key == null || deadline(key, now) != Result.WAIT) continue;
                Chunk chunk = waiting.get(key);
                Lease lease = leases.get(chunk);
                if (lease != null && now.elapsed(lease.started, budget)) continue;
                boolean alreadyParticipates = loading.entrySet().stream()
                        .anyMatch(e -> e.getKey().owner().equals(owner) && e.getValue().chunk.equals(chunk));
                if (!alreadyParticipates && ownerLeases(owner) >= budget.perOwner) continue;
                if (lease == null) {
                    if (leases.size() >= budget.global) continue;
                    lease = new Lease(++generation, chunk, now);
                    leases.put(chunk, lease);
                }
                loading.put(key, lease);
                Attempt attempt = attempts.get(key);
                if (attempt.firstLoad.isEmpty()) attempt.firstLoad = Optional.of(now);
                progress = true;
            }
        } while (progress);
    }

    public Result deadline(RecallRequestKey key, Clock now) {
        Attempt attempt = attempts.get(key);
        if (attempt == null) return Result.WAIT;
        Lease lease = loading.get(key);
        if (attempt.firstLoad.filter(start -> now.elapsed(start, budget)).isPresent()
                || lease != null && now.elapsed(lease.started, budget)) return Result.LOAD_BUDGET_EXCEEDED;
        return lease == null && now.elapsed(attempt.queued, budget) ? Result.QUEUE_BUDGET_EXCEEDED : Result.WAIT;
    }

    /** Expiry is tested before registration, including at the exact deadline. */
    public Result observe(RecallRequestKey key, Observation observed, Clock now) {
        if (!waiting.containsKey(key)) return Result.CANCELLED;
        Result result = deadline(key, now);
        if (result == Result.WAIT) {
            result = switch (observed) {
                case REGISTERED -> Result.REGISTERED;
                case ENTITIES_LOADED_WITHOUT_SHIP -> loading.containsKey(key) ? Result.ABSENT_AFTER_LOAD : Result.WAIT;
                case PENDING -> Result.WAIT;
            };
        }
        if (result != Result.WAIT) release(key);
        return result;
    }

    /** Keeps cumulative clocks for a still-live roster entry that has become indexed. */
    public void release(RecallRequestKey key) {
        waiting.remove(key);
        Lease lease = loading.remove(key);
        if (lease != null && !loading.containsValue(lease)) leases.remove(lease.chunk, lease);
    }

    public void forget(RecallRequestKey key) {
        release(key);
        attempts.remove(key);
    }

    public Optional<View> view(RecallRequestKey key) {
        Chunk chunk = waiting.get(key);
        if (chunk == null) return Optional.empty();
        Attempt attempt = attempts.get(key);
        return Optional.of(new View(loading.containsKey(key) ? Phase.LOADING : Phase.QUEUED,
                chunk, attempt.queued, attempt.firstLoad));
    }

    public List<RecallRequestKey> pending() { return List.copyOf(waiting.keySet()); }
    public List<Lease> leases() { return List.copyOf(leases.values()); }
    public int ownerLeases(UUID owner) {
        return (int) loading.entrySet().stream().filter(e -> e.getKey().owner().equals(owner))
                .map(Map.Entry::getValue).distinct().count();
    }
}
