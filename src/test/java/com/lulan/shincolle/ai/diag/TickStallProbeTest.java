package com.lulan.shincolle.ai.diag;

import com.lulan.shincolle.ai.diag.TickStallProbe.Aggregator;
import com.lulan.shincolle.ai.diag.TickStallProbe.Aggregator.PathOutcome;

import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TickStallProbeTest {
    private static final long MS = 1_000_000L;

    private static List<String> finish(Aggregator stats, long nanos) {
        return stats.endTick(1L, 1000L, nanos, id -> "goal=[] target=[]");
    }

    @Test
    void distributionBucketsUseHalfOpenMillisecondRanges() {
        Aggregator stats = new Aggregator(50);
        long[] ticksMs = {0, 9, 10, 24, 25, 49, 50, 99, 100, 500};
        for (long ms : ticksMs) {
            stats.beginTick();
            finish(stats, ms * MS);
        }
        stats.beginTick();
        finish(stats, 10 * MS - 1);
        assertEquals(10L + 1L, stats.ticks());
        assertEquals(3L, stats.bucket(0), "<10: 0, 9 and just under 10");
        assertEquals(2L, stats.bucket(1), "10-25: 10 and 24");
        assertEquals(2L, stats.bucket(2), "25-50: 25 and 49");
        assertEquals(2L, stats.bucket(3), "50-100: 50 and 99");
        assertEquals(2L, stats.bucket(4), ">=100: 100 and 500");
    }

    @Test
    void onlyTicksAtOrAboveTheThresholdProduceLogLines() {
        Aggregator stats = new Aggregator(50);
        stats.beginTick();
        stats.entityTick("a", "Ship", "ally", 3 * MS);
        assertTrue(finish(stats, 49 * MS).isEmpty());
        stats.beginTick();
        stats.entityTick("a", "Ship", "ally", 3 * MS);
        List<String> lines = finish(stats, 50 * MS);
        assertEquals(2, lines.size());
        assertTrue(lines.get(0).startsWith("[TickProbe] SLOW tick=1 epochMs=1000 ms=50.00 ships=1"), lines.get(0));
        assertEquals(1L, stats.slowTicks());
    }

    @Test
    void slowTickListsTheFiveLargestEntitiesByOwnTickTime() {
        Aggregator stats = new Aggregator(10);
        stats.beginTick();
        for (int i = 1; i <= 8; i++) {
            stats.entityTick("e" + i, "Ship", i % 2 == 0 ? "ally" : "enemy", i * MS);
        }
        List<String> lines = finish(stats, 60 * MS);
        assertEquals(6, lines.size());
        assertTrue(lines.get(0).contains("ships=8"), lines.get(0));
        String[] expectedOrder = {"e8", "e7", "e6", "e5", "e4"};
        for (int i = 0; i < expectedOrder.length; i++) {
            assertTrue(lines.get(i + 1).contains("uuid=" + expectedOrder[i] + " "), lines.get(i + 1));
        }
        assertTrue(lines.get(1).contains("side=ally m2=8.00ms"), lines.get(1));
        assertTrue(lines.get(1).contains("goal=[] target=[]"), lines.get(1));
    }

    @Test
    void entityLineBreaksPathRequestsDownByCaller() {
        Aggregator stats = new Aggregator(10);
        stats.beginTick();
        stats.entityTick("s", "Ship", "ally", 40 * MS);
        stats.pathRequest("s", "Ship", "ally", "Exec.step", PathOutcome.REACHED, 12, 5 * MS);
        stats.pathRequest("s", "Ship", "ally", "Exec.step", PathOutcome.NULL, -1, 2 * MS);
        stats.pathRequest("s", "Ship", "ally", "Exec.detour", PathOutcome.REUSED, 3, MS / 10);
        stats.followTick("s", "Ship", "ally", 3 * MS);
        stats.spatialQuery("s", 4 * MS, 17);
        List<String> lines = finish(stats, 60 * MS);
        assertTrue(lines.get(0).contains("m3=3/7.10ms m4=3.00ms m5=1/4.00ms"), lines.get(0));
        String entity = lines.get(1);
        assertTrue(entity.contains("m3=3/7.10ms"), entity);
        assertTrue(entity.contains("Exec.step n=2 ms=7.00 reused=0 skipped=0 null=1 unreached=0 reached=1 maxPathNodes=12"),
                entity);
        assertTrue(entity.contains("Exec.detour n=1"), entity);
        assertTrue(entity.contains("m5=1/4.00ms"), entity);
    }

    @Test
    void callerTotalsAccumulateAcrossTicksAndEntities() {
        Aggregator stats = new Aggregator(50);
        for (int tick = 0; tick < 3; tick++) {
            stats.beginTick();
            stats.entityTick("a", "Ship", "ally", MS);
            stats.entityTick("b", "Ship", "enemy", MS);
            stats.pathRequest("a", "Ship", "ally", "X.f", PathOutcome.UNREACHED, 4, 2 * MS);
            stats.pathRequest("b", "Ship", "enemy", "X.f", PathOutcome.SKIPPED, -1, MS);
            finish(stats, 5 * MS);
        }
        Aggregator.CallerTally tally = stats.caller("X.f");
        assertNotNull(tally);
        assertEquals(6L, tally.calls());
        assertEquals(9 * MS, tally.nanos());
        assertEquals(6L, stats.m3Calls());
        assertNull(stats.caller("X.g"));
        List<String> report = stats.reportLines(true);
        assertTrue(report.stream().anyMatch(line -> line.contains("X.f n=6 ms=9.00 reused=0 skipped=3 null=0 unreached=3")
                && line.contains("max=2.00ms")), String.join("\n", report));
        assertTrue(report.get(0).contains("ticks=3"), report.get(0));
    }

    @Test
    void topEntitiesAreRankedByCumulativeOwnTickTime() {
        Aggregator stats = new Aggregator(50);
        stats.beginTick();
        stats.entityTick("small", "Ship", "ally", 1 * MS);
        stats.entityTick("big", "Ship", "enemy", 9 * MS);
        finish(stats, 12 * MS);
        stats.beginTick();
        stats.entityTick("small", "Ship", "ally", 20 * MS);
        finish(stats, 25 * MS);
        assertEquals(List.of("small", "big"), stats.topEntityIds(10));
        assertEquals(List.of("small"), stats.topEntityIds(1));
        List<String> report = stats.reportLines(false);
        assertTrue(report.get(0).contains("probe=OFF"), report.get(0));
        assertTrue(report.stream().anyMatch(line -> line.contains("uuid=small type=Ship side=ally total=21.00ms ticks=2")),
                String.join("\n", report));
    }

    @Test
    void nestedEntityTicksAreSubtractedFromTheirParent() {
        Aggregator stats = new Aggregator(50);
        stats.beginTick();
        stats.pushEntity();
        stats.pushEntity();
        long inner = stats.popEntity(30 * MS);
        stats.pushEntity();
        long second = stats.popEntity(10 * MS);
        long outer = stats.popEntity(100 * MS);
        assertEquals(30 * MS, inner);
        assertEquals(10 * MS, second);
        assertEquals(60 * MS, outer);
        assertEquals(7 * MS, stats.popEntity(7 * MS), "an unmatched pop at the root returns the elapsed time");
    }

    @Test
    void resetClearsEverythingButKeepsTheThreshold() {
        Aggregator stats = new Aggregator(50);
        stats.setThresholdMs(20);
        stats.beginTick();
        stats.entityTick("a", "Ship", "ally", 5 * MS);
        stats.pathRequest("a", "Ship", "ally", "X.f", PathOutcome.REACHED, 2, MS);
        assertEquals(1, stats.pendingEntities());
        assertEquals(1L, stats.pendingPathRequests());
        finish(stats, 30 * MS);
        stats.entityTick("b", "Ship", "ally", MS);
        stats.reset();
        assertEquals(0L, stats.ticks());
        assertEquals(0L, stats.slowTicks());
        assertEquals(0, stats.pendingEntities());
        assertEquals(0L, stats.m3Calls());
        assertNull(stats.caller("X.f"));
        assertTrue(stats.topEntityIds(10).isEmpty());
        assertEquals(20, stats.thresholdMs());
    }

    @Test
    void serverStoppedDisablesProbeAndClearsAllSessionState() throws ReflectiveOperationException {
        Aggregator stats = (Aggregator) probeField("STATS").get(null);
        Map<?, ?> mobs = (Map<?, ?>) probeField("TICK_MOBS").get(null);
        try {
            TickStallProbe.reset();
            TickStallProbe.enable(30);
            stats.beginTick();
            stats.entityTick("previous", "Ship", "ally", 5 * MS);
            stats.pathRequest("previous", "Ship", "ally", "Exec.step", PathOutcome.REACHED, 2, MS);
            stats.followTick("previous", "Ship", "ally", MS);
            stats.spatialQuery("previous", MS, 3);
            finish(stats, 40 * MS);
            stats.beginTick();
            stats.entityTick("pending", "Ship", "ally", MS);
            stats.pathRequest("pending", "Ship", "ally", "Exec.step", PathOutcome.NULL, -1, MS);
            stats.pushEntity();
            stats.pushEntity();
            stats.popEntity(MS);
            probeField("tickOpen").setBoolean(null, true);
            for (String name : List.of("tickStartNanos", "tickEpochMs", "tickNumber", "followMark", "m3Running")) {
                probeField(name).setLong(null, 123L);
            }
            // A null value proves that the live-reference map is cleared without creating a Minecraft entity.
            mobs.put(null, null);
            assertTrue(TickStallProbe.isEnabled());
            assertEquals(30, TickStallProbe.thresholdMs());
            assertEquals(1L, TickStallProbe.cumulativeTicks());
            assertEquals(1, TickStallProbe.pendingEntities());
            assertEquals(1L, TickStallProbe.pendingPathRequests());
            assertFalse(mobs.isEmpty());

            // JUnit does not run Forge's event-class transformer; deliver only matching subscribed handlers.
            ServerStoppedEvent event = new ServerStoppedEvent(null);
            for (Method method : TickStallProbe.class.getDeclaredMethods()) {
                if (method.isAnnotationPresent(SubscribeEvent.class)
                        && Arrays.equals(method.getParameterTypes(), new Class<?>[] {ServerStoppedEvent.class})) {
                    method.invoke(null, event);
                }
            }

            assertFalse(TickStallProbe.isEnabled(), "server stop must disable the probe in the same JVM");
            assertEquals(TickStallProbe.DEFAULT_THRESHOLD_MS, TickStallProbe.thresholdMs());
            assertEquals(0L, TickStallProbe.cumulativeTicks());
            assertEquals(0, TickStallProbe.pendingEntities());
            assertEquals(0L, TickStallProbe.pendingPathRequests());
            assertEquals(new Aggregator(TickStallProbe.DEFAULT_THRESHOLD_MS).reportLines(false),
                    TickStallProbe.reportLines());
            assertTrue(mobs.isEmpty());
            assertFalse(probeField("tickOpen").getBoolean(null));
            for (String name : List.of("tickStartNanos", "tickEpochMs", "tickNumber", "followMark", "m3Running")) {
                assertEquals(0L, probeField(name).getLong(null), name);
            }
            Field depth = Aggregator.class.getDeclaredField("depth");
            depth.setAccessible(true);
            assertEquals(0, depth.getInt(stats));
            Field children = Aggregator.class.getDeclaredField("childNanos");
            children.setAccessible(true);
            assertTrue(Arrays.stream((long[]) children.get(stats)).allMatch(nanos -> nanos == 0L));

            // Enabling a second session must not resurrect the first session's measurements.
            TickStallProbe.enable(TickStallProbe.DEFAULT_THRESHOLD_MS);
            assertEquals(new Aggregator(TickStallProbe.DEFAULT_THRESHOLD_MS).reportLines(true),
                    TickStallProbe.reportLines());
        } finally {
            TickStallProbe.disable();
            TickStallProbe.reset();
            TickStallProbe.enable(TickStallProbe.DEFAULT_THRESHOLD_MS);
            TickStallProbe.disable();
        }
    }

    private static Field probeField(String name) throws NoSuchFieldException {
        Field field = TickStallProbe.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    @Test
    void requestsThatNeverSearchAreClassifiedLikeVanillaOrder() {
        assertEquals(PathOutcome.SKIPPED, TickStallProbe.classifyRequest(true, false, false, true));
        assertEquals(PathOutcome.SKIPPED, TickStallProbe.classifyRequest(false, true, false, true));
        assertEquals(PathOutcome.SKIPPED, TickStallProbe.classifyRequest(false, false, true, true));
        assertEquals(PathOutcome.REUSED, TickStallProbe.classifyRequest(false, false, false, true));
        assertNull(TickStallProbe.classifyRequest(false, false, false, false));
    }
}
