package com.lulan.shincolle.ai.diag;

import com.lulan.shincolle.entity.BasicEntityMount;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.entity.BasicEntityShipHostile;
import com.lulan.shincolle.entity.BasicEntitySummon;
import com.lulan.shincolle.reference.Reference;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Opt-in diagnostics that attribute slow server ticks to ship entities, path requests and target searches.
 *
 * <p>Everything lives here and in the few call sites that read {@link #isEnabled()}. While disabled, a call site costs
 * one static boolean read: nothing is allocated and no clock is read. It is switched by an operator command and is never
 * saved, so every server start begins disabled. All measurements are taken on the server thread only.
 *
 * <p>Measured quantities (all with {@link System#nanoTime()}):
 * <ul>
 * <li>M1: the whole server tick, from the Forge tick event START to END.</li>
 * <li>M2: one entity's own tick. The time of a nested entity tick is subtracted from its parent.</li>
 * <li>M3: one path computation request in the ship navigation, with its outcome and caller.</li>
 * <li>M4: the ship navigation's per-tick path following, without the path requests made inside it.</li>
 * <li>M5: one spatial query for target candidates.</li>
 * </ul>
 * M3, M4 and M5 normally run inside an M2 entity tick and are therefore part of it, not additional to it.
 */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TickStallProbe {
    public static final int DEFAULT_THRESHOLD_MS = 50;
    private static final Logger LOG = LoggerFactory.getLogger("ShinColle/TickProbe");
    private static final StackWalker WALKER = StackWalker.getInstance();
    private static final String MOD_PACKAGE = "com.lulan.shincolle";
    private static final String PATH_PACKAGE = "com.lulan.shincolle.ai.path";
    private static final String DIAG_PACKAGE = "com.lulan.shincolle.ai.diag";

    private static volatile boolean enabled;
    private static final Aggregator STATS = new Aggregator(DEFAULT_THRESHOLD_MS);
    /** Live entities of the current tick, kept only to name the running goals of a slow tick. */
    private static final Map<String, Mob> TICK_MOBS = new HashMap<>();
    private static boolean tickOpen;
    private static long tickStartNanos;
    private static long tickEpochMs;
    private static long tickNumber;
    private static long followMark;
    private static long m3Running;

    private TickStallProbe() {
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static int thresholdMs() {
        return STATS.thresholdMs();
    }

    /** Starts recording; the cumulative statistics are kept, use {@link #reset()} to clear them. */
    public static void enable(int thresholdMs) {
        STATS.setThresholdMs(thresholdMs);
        enabled = true;
    }

    public static void disable() {
        enabled = false;
        tickOpen = false;
        TICK_MOBS.clear();
    }

    public static void reset() {
        STATS.reset();
        TICK_MOBS.clear();
        tickOpen = false;
        followMark = 0L;
        m3Running = 0L;
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        disable();
        reset();
        STATS.setThresholdMs(DEFAULT_THRESHOLD_MS);
        tickStartNanos = 0L;
        tickEpochMs = 0L;
        tickNumber = 0L;
    }

    /** Number of entities and path requests recorded since the last tick started or the last reset. */
    public static int pendingEntities() {
        return STATS.pendingEntities();
    }

    public static long pendingPathRequests() {
        return STATS.pendingPathRequests();
    }

    public static long cumulativeTicks() {
        return STATS.ticks();
    }

    public static List<String> reportLines() {
        return STATS.reportLines(enabled);
    }

    /** Writes lines to the probe's logger, the same place the slow-tick lines go. */
    public static void log(List<String> lines) {
        for (String line : lines) {
            LOG.info(line);
        }
    }

    // ---- M1 ----

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onServerTickStart(TickEvent.ServerTickEvent event) {
        if (!enabled || event.phase != TickEvent.Phase.START) {
            return;
        }
        STATS.beginTick();
        TICK_MOBS.clear();
        tickNumber = event.getServer().getTickCount();
        tickEpochMs = System.currentTimeMillis();
        tickOpen = true;
        tickStartNanos = System.nanoTime();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onServerTickEnd(TickEvent.ServerTickEvent event) {
        if (!tickOpen || event.phase != TickEvent.Phase.END) {
            return;
        }
        long elapsed = System.nanoTime() - tickStartNanos;
        tickOpen = false;
        if (!enabled) {
            TICK_MOBS.clear();
            return;
        }
        for (String line : STATS.endTick(tickNumber, tickEpochMs, elapsed, TickStallProbe::runningGoals)) {
            LOG.info(line);
        }
        TICK_MOBS.clear();
    }

    // ---- M2 ----

    /** Call only when {@link #isEnabled()} was true and the entity is on the server side. */
    public static long entityTickBegin() {
        STATS.pushEntity();
        return System.nanoTime();
    }

    public static void entityTickEnd(Entity entity, long start) {
        long self = STATS.popEntity(System.nanoTime() - start);
        String id = entity.getUUID().toString();
        STATS.entityTick(id, typeOf(entity), sideOf(entity), self);
        if (entity instanceof Mob mob) {
            TICK_MOBS.put(id, mob);
        }
    }

    // ---- M3 ----

    /**
     * Classifies a request before vanilla runs it. Mirrors the vanilla checks that return without searching,
     * in vanilla's order: no target, below the world, cannot update, same target while the path is unfinished.
     */
    public static Aggregator.PathOutcome classifyRequest(boolean noTargets, boolean belowWorld, boolean cannotUpdate,
            boolean sameTargetAndUnfinished) {
        if (noTargets || belowWorld || cannotUpdate) {
            return Aggregator.PathOutcome.SKIPPED;
        }
        return sameTargetAndUnfinished ? Aggregator.PathOutcome.REUSED : null;
    }

    public static long pathRequestBegin() {
        return System.nanoTime();
    }

    /**
     * @param early the outcome {@link #classifyRequest} gave, or null when vanilla computed a path
     * @param nodes path node count, or -1 for no path
     */
    public static void pathRequestEnd(Mob mob, long start, Aggregator.PathOutcome early, boolean found, boolean reached,
            int nodes) {
        long nanos = System.nanoTime() - start;
        Aggregator.PathOutcome outcome = early != null ? early
                : !found ? Aggregator.PathOutcome.NULL
                : reached ? Aggregator.PathOutcome.REACHED : Aggregator.PathOutcome.UNREACHED;
        m3Running += nanos;
        STATS.pathRequest(mob.getUUID().toString(), typeOf(mob), sideOf(mob), caller(), outcome, nodes, nanos);
    }

    private static String caller() {
        String callers = WALKER.walk(frames -> frames
                .filter(frame -> {
                    String name = frame.getClassName();
                    return name.startsWith(MOD_PACKAGE) && !name.startsWith(PATH_PACKAGE)
                            && !name.startsWith(DIAG_PACKAGE);
                })
                .limit(2)
                .map(frame -> simpleName(frame.getClassName()) + "." + frame.getMethodName())
                .collect(Collectors.joining(" < ")));
        return callers.isEmpty() ? "(outside mod code)" : callers;
    }

    // ---- M4 ----

    public static long followBegin() {
        followMark = m3Running;
        return System.nanoTime();
    }

    /** Path requests made inside the following tick are counted by M3 and left out here. */
    public static void followEnd(Mob mob, long start) {
        long nanos = System.nanoTime() - start - (m3Running - followMark);
        STATS.followTick(mob.getUUID().toString(), typeOf(mob), sideOf(mob), Math.max(0L, nanos));
    }

    // ---- M5 ----

    public static long spatialQueryBegin() {
        return System.nanoTime();
    }

    public static void spatialQueryEnd(java.util.UUID source, long start, int returned) {
        STATS.spatialQuery(source.toString(), System.nanoTime() - start, returned);
    }

    // ---- identity ----

    private static String typeOf(Entity entity) {
        return entity.getClass().getSimpleName();
    }

    private static String sideOf(Entity entity) {
        if (entity instanceof BasicEntityShipHostile) {
            return "enemy";
        }
        if (entity instanceof BasicEntityShip || entity instanceof BasicEntityMount) {
            return "ally";
        }
        if (entity instanceof BasicEntitySummon summon) {
            Entity host = summon.getHostEntity();
            if (host instanceof BasicEntityShipHostile) {
                return "enemy";
            }
            if (host instanceof BasicEntityShip) {
                return "ally";
            }
        }
        return "other";
    }

    private static String runningGoals(String id) {
        Mob mob = TICK_MOBS.get(id);
        if (mob == null) {
            return "goals=?";
        }
        return "goal=" + names(mob.goalSelector) + " target=" + names(mob.targetSelector);
    }

    private static String names(GoalSelector selector) {
        return selector.getRunningGoals()
                .map(wrapped -> wrapped.getGoal().getClass().getSimpleName())
                .collect(Collectors.joining(",", "[", "]"));
    }

    private static String simpleName(String className) {
        return className.substring(className.lastIndexOf('.') + 1);
    }

    // ================= aggregation (no Minecraft types) =================

    /** Collects the measurements and renders the log and report lines. Single-threaded. */
    public static final class Aggregator {
        /** Result of one path request. */
        public enum PathOutcome { SKIPPED, REUSED, NULL, REACHED, UNREACHED }

        private static final int MAX_CUMULATIVE_ENTITIES = 20000;
        private static final int SLOW_TICK_ENTITIES = 5;
        private static final int REPORT_ENTITIES = 10;
        private static final int REPORT_CALLERS = 25;
        /** Upper bounds in ms of the first four distribution buckets; the fifth is open-ended. */
        private static final long[] BUCKET_LIMITS_MS = {10L, 25L, 50L, 100L};
        private static final String[] BUCKET_NAMES = {"<10", "10-25", "25-50", "50-100", ">=100"};
        private static final String PREFIX = "[TickProbe] ";

        /** Path request totals for one caller. */
        public static final class CallerTally {
            long calls;
            long nanos;
            long maxNanos;
            long skipped;
            long reused;
            long nulls;
            long reached;
            long unreached;
            int maxNodes;

            void add(PathOutcome outcome, int nodes, long elapsed) {
                this.calls++;
                this.nanos += elapsed;
                this.maxNanos = Math.max(this.maxNanos, elapsed);
                this.maxNodes = Math.max(this.maxNodes, nodes);
                switch (outcome) {
                    case SKIPPED -> this.skipped++;
                    case REUSED -> this.reused++;
                    case NULL -> this.nulls++;
                    case REACHED -> this.reached++;
                    case UNREACHED -> this.unreached++;
                    default -> { }
                }
            }

            void merge(CallerTally other) {
                this.calls += other.calls;
                this.nanos += other.nanos;
                this.maxNanos = Math.max(this.maxNanos, other.maxNanos);
                this.skipped += other.skipped;
                this.reused += other.reused;
                this.nulls += other.nulls;
                this.reached += other.reached;
                this.unreached += other.unreached;
                this.maxNodes = Math.max(this.maxNodes, other.maxNodes);
            }

            public long calls() {
                return this.calls;
            }

            public long nanos() {
                return this.nanos;
            }
        }

        private static final class EntityTally {
            final String id;
            String type = "?";
            String side = "?";
            long m2Nanos;
            long m2Calls;
            long m4Nanos;
            long m5Calls;
            long m5Nanos;
            long m5Returned;
            final Map<String, CallerTally> callers = new LinkedHashMap<>();

            EntityTally(String id) {
                this.id = id;
            }

            long m3Calls() {
                long sum = 0L;
                for (CallerTally caller : this.callers.values()) {
                    sum += caller.calls;
                }
                return sum;
            }

            long m3Nanos() {
                long sum = 0L;
                for (CallerTally caller : this.callers.values()) {
                    sum += caller.nanos;
                }
                return sum;
            }
        }

        private static final class CumulativeEntity {
            String type;
            String side;
            long nanos;
            long ticks;
        }

        private int thresholdMs;
        private long[] childNanos = new long[8];
        private int depth;

        private final Map<String, EntityTally> scratch = new LinkedHashMap<>();

        private long ticks;
        private long slowTicks;
        private long totalNanos;
        private long maxTickNanos;
        private final long[] buckets = new long[BUCKET_NAMES.length];
        private long m2Nanos;
        private long m2Calls;
        private long m3Calls;
        private long m3Nanos;
        private long m4Nanos;
        private long m5Calls;
        private long m5Nanos;
        private long m5Returned;
        private final Map<String, CallerTally> callers = new LinkedHashMap<>();
        private final Map<String, CumulativeEntity> entities = new HashMap<>();

        public Aggregator(int thresholdMs) {
            this.thresholdMs = Math.max(1, thresholdMs);
        }

        public int thresholdMs() {
            return this.thresholdMs;
        }

        public void setThresholdMs(int thresholdMs) {
            this.thresholdMs = Math.max(1, thresholdMs);
        }

        public void reset() {
            this.scratch.clear();
            this.depth = 0;
            java.util.Arrays.fill(this.childNanos, 0L);
            this.ticks = 0L;
            this.slowTicks = 0L;
            this.totalNanos = 0L;
            this.maxTickNanos = 0L;
            java.util.Arrays.fill(this.buckets, 0L);
            this.m2Nanos = 0L;
            this.m2Calls = 0L;
            this.m3Calls = 0L;
            this.m3Nanos = 0L;
            this.m4Nanos = 0L;
            this.m5Calls = 0L;
            this.m5Nanos = 0L;
            this.m5Returned = 0L;
            this.callers.clear();
            this.entities.clear();
        }

        public void beginTick() {
            this.scratch.clear();
            this.depth = 0;
            this.childNanos[0] = 0L;
        }

        // ---- entity nesting: an entity's own time excludes the entities ticked inside it ----

        public void pushEntity() {
            this.depth++;
            if (this.depth >= this.childNanos.length) {
                this.childNanos = java.util.Arrays.copyOf(this.childNanos, this.childNanos.length * 2);
            }
            this.childNanos[this.depth] = 0L;
        }

        /** @return the entity's own nanos: the elapsed time minus its nested entities' elapsed time */
        public long popEntity(long elapsedNanos) {
            if (this.depth <= 0) {
                return elapsedNanos;
            }
            long self = Math.max(0L, elapsedNanos - this.childNanos[this.depth]);
            this.depth--;
            this.childNanos[this.depth] += elapsedNanos;
            return self;
        }

        // ---- recording ----

        private EntityTally tally(String id, String type, String side) {
            EntityTally tally = this.scratch.computeIfAbsent(id, EntityTally::new);
            if (type != null && "?".equals(tally.type)) {
                tally.type = type;
            }
            if (side != null && "?".equals(tally.side)) {
                tally.side = side;
            }
            return tally;
        }

        public void entityTick(String id, String type, String side, long nanos) {
            EntityTally tally = tally(id, type, side);
            tally.m2Nanos += nanos;
            tally.m2Calls++;
        }

        public void pathRequest(String id, String type, String side, String caller, PathOutcome outcome, int nodes,
                long nanos) {
            this.tally(id, type, side).callers.computeIfAbsent(caller, key -> new CallerTally())
                    .add(outcome, nodes, nanos);
        }

        public void followTick(String id, String type, String side, long nanos) {
            this.tally(id, type, side).m4Nanos += nanos;
        }

        public void spatialQuery(String id, long nanos, int returned) {
            EntityTally tally = this.tally(id, null, null);
            tally.m5Calls++;
            tally.m5Nanos += nanos;
            tally.m5Returned += returned;
        }

        public int pendingEntities() {
            return this.scratch.size();
        }

        public long pendingPathRequests() {
            long sum = 0L;
            for (EntityTally tally : this.scratch.values()) {
                sum += tally.m3Calls();
            }
            return sum;
        }

        public long ticks() {
            return this.ticks;
        }

        public long slowTicks() {
            return this.slowTicks;
        }

        public long bucket(int index) {
            return this.buckets[index];
        }

        public long m3Calls() {
            return this.m3Calls;
        }

        public CallerTally caller(String key) {
            return this.callers.get(key);
        }

        /** Ids of the entities with the most own tick time since the last reset, longest first. */
        public List<String> topEntityIds(int limit) {
            return this.entities.entrySet().stream()
                    .sorted(Comparator.comparingLong((Map.Entry<String, CumulativeEntity> e) -> e.getValue().nanos)
                            .reversed().thenComparing(Map.Entry::getKey))
                    .limit(limit)
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toList());
        }

        // ---- end of a tick ----

        /**
         * Folds the finished tick into the cumulative statistics.
         *
         * @return the log lines of a slow tick (empty when the tick was below the threshold)
         */
        public List<String> endTick(long tickNumber, long epochMs, long nanos, Function<String, String> goalsOf) {
            this.ticks++;
            this.totalNanos += nanos;
            this.maxTickNanos = Math.max(this.maxTickNanos, nanos);
            this.buckets[bucketOf(nanos)]++;

            long tickM2 = 0L;
            long tickM2Calls = 0L;
            long tickM3Calls = 0L;
            long tickM3Nanos = 0L;
            long tickM4 = 0L;
            long tickM5Calls = 0L;
            long tickM5Nanos = 0L;
            int ships = 0;
            for (EntityTally tally : this.scratch.values()) {
                tickM2 += tally.m2Nanos;
                tickM2Calls += tally.m2Calls;
                if (tally.m2Calls > 0L) {
                    ships++;
                }
                tickM3Calls += tally.m3Calls();
                tickM3Nanos += tally.m3Nanos();
                tickM4 += tally.m4Nanos;
                tickM5Calls += tally.m5Calls;
                tickM5Nanos += tally.m5Nanos;
                for (Map.Entry<String, CallerTally> caller : tally.callers.entrySet()) {
                    this.callers.computeIfAbsent(caller.getKey(), key -> new CallerTally()).merge(caller.getValue());
                }
                if (tally.m2Calls > 0L) {
                    CumulativeEntity cumulative = this.entities.get(tally.id);
                    if (cumulative == null && this.entities.size() < MAX_CUMULATIVE_ENTITIES) {
                        cumulative = new CumulativeEntity();
                        cumulative.type = tally.type;
                        cumulative.side = tally.side;
                        this.entities.put(tally.id, cumulative);
                    }
                    if (cumulative != null) {
                        cumulative.nanos += tally.m2Nanos;
                        cumulative.ticks++;
                    }
                }
            }
            this.m2Nanos += tickM2;
            this.m2Calls += tickM2Calls;
            this.m3Calls += tickM3Calls;
            this.m3Nanos += tickM3Nanos;
            this.m4Nanos += tickM4;
            this.m5Calls += tickM5Calls;
            this.m5Nanos += tickM5Nanos;
            for (EntityTally tally : this.scratch.values()) {
                this.m5Returned += tally.m5Returned;
            }

            List<String> lines = new ArrayList<>();
            if (nanos >= this.thresholdMs * 1_000_000L) {
                this.slowTicks++;
                lines.add(PREFIX + String.format(Locale.ROOT,
                        "SLOW tick=%d epochMs=%d ms=%.2f ships=%d m2=%.2fms m3=%d/%.2fms m4=%.2fms m5=%d/%.2fms",
                        tickNumber, epochMs, ms(nanos), ships, ms(tickM2), tickM3Calls, ms(tickM3Nanos), ms(tickM4),
                        tickM5Calls, ms(tickM5Nanos)));
                this.scratch.values().stream()
                        .filter(tally -> tally.m2Calls > 0L)
                        .sorted(Comparator.comparingLong((EntityTally tally) -> tally.m2Nanos).reversed()
                                .thenComparing(tally -> tally.id))
                        .limit(SLOW_TICK_ENTITIES)
                        .forEach(tally -> lines.add(PREFIX + "  " + entityLine(tally, goalsOf)));
            }
            this.scratch.clear();
            return lines;
        }

        private static String entityLine(EntityTally tally, Function<String, String> goalsOf) {
            StringBuilder line = new StringBuilder(String.format(Locale.ROOT,
                    "uuid=%s type=%s side=%s m2=%.2fms m3=%d/%.2fms", tally.id, tally.type, tally.side,
                    ms(tally.m2Nanos), tally.m3Calls(), ms(tally.m3Nanos())));
            if (!tally.callers.isEmpty()) {
                line.append(" {");
                boolean first = true;
                for (Map.Entry<String, CallerTally> entry : tally.callers.entrySet()) {
                    if (!first) {
                        line.append("; ");
                    }
                    first = false;
                    line.append(callerText(entry.getKey(), entry.getValue()));
                }
                line.append('}');
            }
            line.append(String.format(Locale.ROOT, " m4=%.2fms m5=%d/%.2fms %s", ms(tally.m4Nanos), tally.m5Calls,
                    ms(tally.m5Nanos), goalsOf.apply(tally.id)));
            return line.toString();
        }

        private static String callerText(String caller, CallerTally tally) {
            return String.format(Locale.ROOT,
                    "%s n=%d ms=%.2f reused=%d skipped=%d null=%d unreached=%d reached=%d maxPathNodes=%d",
                    caller, tally.calls, ms(tally.nanos), tally.reused, tally.skipped, tally.nulls, tally.unreached,
                    tally.reached, tally.maxNodes);
        }

        // ---- report ----

        public List<String> reportLines(boolean on) {
            List<String> lines = new ArrayList<>();
            double perTick = this.ticks == 0L ? 0D : 1D / this.ticks;
            lines.add(PREFIX + String.format(Locale.ROOT, "report: probe=%s thresholdMs=%d ticks=%d slowTicks=%d",
                    on ? "ON" : "OFF", this.thresholdMs, this.ticks, this.slowTicks));
            lines.add(PREFIX + String.format(Locale.ROOT, "  tick ms: avg=%.3f max=%.2f", ms(this.totalNanos) * perTick,
                    ms(this.maxTickNanos)));
            StringBuilder distribution = new StringBuilder("  tick ms distribution:");
            for (int i = 0; i < BUCKET_NAMES.length; i++) {
                distribution.append(' ').append(BUCKET_NAMES[i]).append('=').append(this.buckets[i]);
            }
            lines.add(PREFIX + distribution);
            lines.add(PREFIX + String.format(Locale.ROOT,
                    "  M2 entity tick: total=%.2fms perTick=%.3fms entityTicks=%d",
                    ms(this.m2Nanos), ms(this.m2Nanos) * perTick, this.m2Calls));
            lines.add(PREFIX + String.format(Locale.ROOT, "  M3 path request: calls=%d total=%.2fms perTick=%.3fms",
                    this.m3Calls, ms(this.m3Nanos), ms(this.m3Nanos) * perTick));
            lines.add(PREFIX + String.format(Locale.ROOT, "  M4 path follow: total=%.2fms perTick=%.3fms",
                    ms(this.m4Nanos), ms(this.m4Nanos) * perTick));
            lines.add(PREFIX + String.format(Locale.ROOT,
                    "  M5 target search: calls=%d total=%.2fms perTick=%.3fms returned=%d", this.m5Calls,
                    ms(this.m5Nanos), ms(this.m5Nanos) * perTick, this.m5Returned));
            lines.add(PREFIX + "  M3 by caller (max=slowest single request):");
            this.callers.entrySet().stream()
                    .sorted(Comparator.comparingLong((Map.Entry<String, CallerTally> e) -> e.getValue().nanos)
                            .reversed().thenComparing(Map.Entry::getKey))
                    .limit(REPORT_CALLERS)
                    .forEach(entry -> lines.add(PREFIX + String.format(Locale.ROOT, "    %s max=%.2fms",
                            callerText(entry.getKey(), entry.getValue()), ms(entry.getValue().maxNanos))));
            lines.add(PREFIX + "  M2 top " + REPORT_ENTITIES + " entities by own tick time:");
            for (String id : topEntityIds(REPORT_ENTITIES)) {
                CumulativeEntity entity = this.entities.get(id);
                lines.add(PREFIX + String.format(Locale.ROOT, "    uuid=%s type=%s side=%s total=%.2fms ticks=%d avg=%.3fms",
                        id, entity.type, entity.side, ms(entity.nanos), entity.ticks,
                        entity.ticks == 0L ? 0D : ms(entity.nanos) / entity.ticks));
            }
            return lines;
        }

        static int bucketOf(long nanos) {
            for (int i = 0; i < BUCKET_LIMITS_MS.length; i++) {
                if (nanos < BUCKET_LIMITS_MS[i] * 1_000_000L) {
                    return i;
                }
            }
            return BUCKET_LIMITS_MS.length;
        }

        private static double ms(long nanos) {
            return nanos / 1_000_000D;
        }
    }
}
