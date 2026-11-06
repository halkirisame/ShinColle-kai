package com.lulan.shincolle.handler;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** Public entity-load queries, with a scoped completion barrier for registration-delay tests. */
public final class RecallLoadObservation {
    private record Location(ServerLevel level, ChunkPos chunk) { }
    private static final Set<Location> HELD = new HashSet<>();
    private static final Map<Location, CompletionProbe> PROBES = new HashMap<>();

    private RecallLoadObservation() { }

    public static boolean entitiesLoaded(ServerLevel level, ChunkPos chunk) {
        Location location = new Location(level, chunk);
        boolean completed = !HELD.contains(location) && level.areEntitiesLoaded(chunk.toLong())
                && level.isPositionEntityTicking(chunk.getMiddleBlockPosition(level.getMinBuildHeight()));
        CompletionProbe probe = PROBES.get(location);
        if (probe != null) probe.completed |= completed;
        return completed;
    }

    /** A scoped test probe remembers a public completion query before ticket release can unload it. */
    public static CompletionProbe probeCompletionForTest(ServerLevel level, ChunkPos chunk) {
        Location location = new Location(level, chunk);
        CompletionProbe probe = new CompletionProbe(location);
        if (PROBES.putIfAbsent(location, probe) != null) throw new IllegalStateException("Completion already probed");
        return probe;
    }

    public static final class CompletionProbe implements AutoCloseable {
        private final Location location;
        private boolean completed;
        private CompletionProbe(Location location) { this.location = location; }
        public boolean completed() { return completed; }
        @Override
        public void close() { PROBES.remove(location, this); }
    }

    /** Only the named test chunk is masked; closing never changes vanilla loading or saved data. */
    public static AutoCloseable holdCompletionForTest(ServerLevel level, ChunkPos chunk) {
        Location location = new Location(level, chunk);
        if (!HELD.add(location)) throw new IllegalStateException("Completion already held");
        return () -> HELD.remove(location);
    }
}
