package com.lulan.shincolle.ai.domain.movement;

import com.lulan.shincolle.ai.domain.DimensionKey;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;
import java.util.UUID;

/**
 * Followers an owner left behind in a fast departure, in memory only. It decides what becomes of
 * each one once the owner has settled; the server side does the loading and the recall request.
 */
public final class DepartureRoster {
    /** Ten minutes at twenty ticks a second. */
    public static final long LIFETIME_TICKS = 12_000L;
    public static final int MAX_ENTRIES = 64;
    /** How long an unloaded follower's chunk is waited on. */
    public static final long LOAD_WAIT_TICKS = 200L;

    private DepartureRoster() { }

    /**
     * @param loadStartedAt when its chunk began to be waited on, empty until it has
     * @param requestedAt   when it was last asked to come, empty until it has been
     */
    public record Entry(UUID ship, int shipUid, DimensionKey dimension, int chunkX, int chunkZ,
                        long recordedAt, OptionalLong loadStartedAt, OptionalLong requestedAt) {
        public static Entry departed(UUID ship, int shipUid, DimensionKey dimension, int chunkX, int chunkZ, long now) {
            return new Entry(ship, shipUid, dimension, chunkX, chunkZ, now, OptionalLong.empty(), OptionalLong.empty());
        }

        public Entry loadStarted(long now) {
            return new Entry(this.ship, this.shipUid, this.dimension, this.chunkX, this.chunkZ,
                    this.recordedAt, OptionalLong.of(now), this.requestedAt);
        }

        /** Where the follower was last seen loaded, so an unloaded one is looked for in the chunk it stopped in. */
        public Entry seenIn(int chunkX, int chunkZ) {
            return new Entry(this.ship, this.shipUid, this.dimension, chunkX, chunkZ,
                    this.recordedAt, this.loadStartedAt, this.requestedAt);
        }

        /** A loaded follower asked to come; its chunk is wherever it stands now. */
        public Entry requested(int chunkX, int chunkZ, long now) {
            return new Entry(this.ship, this.shipUid, this.dimension, chunkX, chunkZ,
                    this.recordedAt, OptionalLong.empty(), OptionalLong.of(now));
        }
    }

    /** What the server side found of one entry. */
    public enum Found {
        /** Not in the loaded world: unloaded, or gone. */
        NOT_LOADED,
        /** Loaded and alive, but it may not be recalled now, or it is another ship. */
        INELIGIBLE,
        /** Loaded and already within the owner's reach. */
        NEAR,
        /** Loaded, eligible, and out of reach. */
        FAR,
        /** As {@link #FAR}, with a recall request already waiting that a new one would only delay. */
        REQUESTED
    }

    public enum Step {
        DROP,
        /** Hold its chunk and wait for it to load. */
        START_LOAD,
        WAIT,
        /** Hold its chunk and ask it to come. */
        REQUEST
    }

    /** Appends an entry, keeping the first departure of a ship and dropping the oldest past the limit. */
    public static List<Entry> record(List<Entry> entries, Entry entry) {
        for (Entry existing : entries) {
            if (existing.ship().equals(entry.ship())) return entries;
        }
        List<Entry> next = new ArrayList<>(entries);
        next.add(entry);
        while (next.size() > MAX_ENTRIES) next.remove(0);
        return List.copyOf(next);
    }

    /** Another dimension's followers and entries past their lifetime are never recalled. */
    public static boolean stale(Entry entry, long now, DimensionKey ownerDimension) {
        return !entry.dimension().equals(ownerDimension) || now - entry.recordedAt() >= LIFETIME_TICKS;
    }

    public static Step decide(Entry entry, Found found, long now, DimensionKey ownerDimension) {
        if (stale(entry, now, ownerDimension)) return Step.DROP;
        return switch (found) {
            case INELIGIBLE, NEAR -> Step.DROP;
            case REQUESTED -> Step.WAIT;
            case FAR -> entry.requestedAt().isEmpty()
                    || now - entry.requestedAt().getAsLong() >= OwnerJumpDetector.HOLD_TICKS
                    ? Step.REQUEST : Step.WAIT;
            case NOT_LOADED -> entry.loadStartedAt().isEmpty() ? Step.START_LOAD
                    : now - entry.loadStartedAt().getAsLong() >= LOAD_WAIT_TICKS ? Step.DROP : Step.WAIT;
        };
    }
}
