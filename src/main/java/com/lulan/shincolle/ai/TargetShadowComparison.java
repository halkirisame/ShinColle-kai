package com.lulan.shincolle.ai;

import com.lulan.shincolle.reference.Reference;

import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Compatibility API for retired legacy/NEW comparisons. No observations or results are produced. */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public final class TargetShadowComparison {
    public enum Outcome {
        MATCH, DIFF_NON_LIVING, DIFF_TIE_BREAK, DRAW_NOT_TAKEN, UNKNOWN
    }

    private static final Metrics NO_COMPARISONS = new Metrics(0, 0, 0, 0, 0, 0, 0, 0);

    private TargetShadowComparison() {
    }

    /** Accepts old requests without enabling a retired execution path. */
    public static void setEnabled(boolean value) {
    }

    public static boolean isEnabled() {
        return false;
    }

    public static long count(Outcome outcome) {
        return 0L;
    }

    public static Metrics metrics() {
        return NO_COMPARISONS;
    }

    public static void resetCounters() {
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        resetCounters();
    }

    /** Retained for callers compiled against the old API; comparison is not performed. */
    public static void compare(Mob source, Entity legacyTarget, int range, int drawnIndex,
                               boolean legacyDrew, ProfilerFiller profiler) {
    }

    public record Metrics(
            long comparisons,
            long spatialQueries,
            long rawCandidates,
            long selections,
            long classifications,
            long relationLookups,
            long lineOfSightQueries,
            long errors) {
    }
}
