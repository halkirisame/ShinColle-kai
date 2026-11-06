package com.lulan.shincolle.ai.path;

import com.lulan.shincolle.ai.diag.TickStallProbe;
import com.lulan.shincolle.entity.IShipNavigator;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.navigation.AmphibiousPathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Amphibious path generation with the original ships' waypoint lookahead. */
public final class ShipPathNavigation extends AmphibiousPathNavigation {
    /** What vanilla adds to the follow range for the region a search for a block reads. */
    private static final int BLOCK_SEARCH_REGION_OFFSET = 8;
    /** Diagnostics only, off unless a test sets it: every stop and path request, with its tick. */
    private @Nullable List<String> callLog;
    /** Diagnostics only, off unless a test sets it: every path search, with how it ended. */
    private @Nullable List<String> searchLog;

    public ShipPathNavigation(Mob mob, Level level) {
        super(mob, level);
    }

    @Override
    protected PathFinder createPathFinder(int maxVisitedNodes) {
        this.nodeEvaluator = new ShipAmphibiousNodeEvaluator();
        this.nodeEvaluator.setCanPassDoors(true);
        return new PathFinder(this.nodeEvaluator, maxVisitedNodes);
    }

    /** Starts appending "tick:call" entries to {@code log}, or stops when it is null. */
    public void recordCalls(@Nullable List<String> log) {
        this.callLog = log;
    }

    /**
     * Starts appending "tick:search(targets)=reached|partial|null" entries to {@code log}, one per path search
     * however it was asked for, or stops when it is null.
     */
    public void recordSearches(@Nullable List<String> log) {
        this.searchLog = log;
    }

    @Override
    public boolean moveTo(double x, double y, double z, double speed) {
        return this.logMoveTo(x, y, z, speed, super.moveTo(x, y, z, speed));
    }

    /**
     * As {@link #moveTo(double, double, double, double)}, with the path searched by {@link #createPathAfresh}.
     */
    public boolean moveToAfresh(double x, double y, double z, double speed) {
        return this.logMoveTo(x, y, z, speed,
                super.moveTo(this.createPathAfresh(BlockPos.containing(x, y, z), 1), speed));
    }

    private boolean logMoveTo(double x, double y, double z, double speed, boolean issued) {
        if (this.callLog != null) {
            this.callLog.add(this.mob.tickCount + ":moveTo(" + fmt(x) + "," + fmt(y) + "," + fmt(z) + ")x"
                    + speed + "=" + issued);
        }
        return issued;
    }

    /**
     * A search for {@code target} as if no path were being followed: vanilla answers a search for where the
     * unfinished path goes with that path, and this one is never answered so. Otherwise vanilla's own search.
     */
    public @Nullable Path createPathAfresh(BlockPos target, int reach) {
        Path following = this.path;
        this.path = null;
        try {
            return this.createPath(target, reach);
        } finally {
            this.path = following;
        }
    }

    /**
     * Which of {@code targets} a search from where the mob stands now cannot reach with reach 0, known without
     * searching. A search reaches a target only by taking the node at its position from the open set, and while
     * the start node has no negative malus it enters only nodes with none. The node evaluator gives such a node
     * to no position it types open, which it falls through to the ground below, or with a negative malus, which
     * is walled up. So those positions are proven, but never the start's own, and nothing when the start has a
     * negative malus. The types are read as the search would read them, in the same region.
     */
    public boolean[] provenUnreachable(List<BlockPos> targets) {
        boolean[] proven = new boolean[targets.size()];
        int range = (int) ((float) this.mob.getAttributeValue(Attributes.FOLLOW_RANGE) + BLOCK_SEARCH_REGION_OFFSET);
        BlockPos at = this.mob.blockPosition();
        PathNavigationRegion region = new PathNavigationRegion(this.level, at.offset(-range, -range, -range),
                at.offset(range, range, range));
        this.nodeEvaluator.prepare(region, this.mob);
        try {
            Node start = this.nodeEvaluator.getStart();
            if (start == null || start.costMalus < 0.0F) return proven;
            for (int i = 0; i < proven.length; i++) {
                BlockPos target = targets.get(i);
                if (target.equals(start.asBlockPos())) continue;
                BlockPathTypes type = this.nodeEvaluator.getBlockPathType(region, target.getX(), target.getY(),
                        target.getZ(), this.mob);
                proven[i] = type == BlockPathTypes.OPEN || this.mob.getPathfindingMalus(type) < 0.0F;
            }
            return proven;
        } finally {
            this.nodeEvaluator.done();
        }
    }

    @Override
    public boolean moveTo(Entity entity, double speed) {
        boolean issued = super.moveTo(entity, speed);
        if (this.callLog != null) {
            this.callLog.add(this.mob.tickCount + ":moveTo(" + entity.getType().getDescriptionId() + "@"
                    + fmt(entity.getX()) + "," + fmt(entity.getY()) + "," + fmt(entity.getZ()) + ")x" + speed
                    + "=" + issued);
        }
        return issued;
    }

    @Override
    public void stop() {
        super.stop();
        if (this.callLog != null) {
            this.callLog.add(this.mob.tickCount + ":stop");
        }
    }

    /**
     * Every path request, however it was made, ends in this vanilla method. It is overridden only to time the
     * request for the tick diagnostics; the result is vanilla's, unchanged.
     */
    @Override
    protected Path createPath(Set<BlockPos> targets, int regionOffset, boolean offsetUpward, int distance,
            float followRange) {
        if (!TickStallProbe.isEnabled()) {
            return this.logSearch(targets, super.createPath(targets, regionOffset, offsetUpward, distance,
                    followRange));
        }
        TickStallProbe.Aggregator.PathOutcome early = TickStallProbe.classifyRequest(
                targets.isEmpty(),
                this.mob.getY() < this.level.getMinBuildHeight(),
                !this.canUpdatePath(),
                this.path != null && !this.path.isDone() && targets.contains(this.getTargetPos()));
        long start = TickStallProbe.pathRequestBegin();
        Path result = super.createPath(targets, regionOffset, offsetUpward, distance, followRange);
        TickStallProbe.pathRequestEnd(this.mob, start, early, result != null, result != null && result.canReach(),
                result == null ? -1 : result.getNodeCount());
        return this.logSearch(targets, result);
    }

    private @Nullable Path logSearch(Set<BlockPos> targets, @Nullable Path result) {
        if (this.searchLog != null) {
            this.searchLog.add(this.mob.tickCount + ":search(" + targets.size() + ")="
                    + (result == null ? "null" : result.canReach() ? "reached" : "partial"));
        }
        return result;
    }

    @Override
    public void tick() {
        if (!TickStallProbe.isEnabled()) {
            super.tick();
            return;
        }
        long start = TickStallProbe.followBegin();
        super.tick();
        TickStallProbe.followEnd(this.mob, start);
    }

    private static String fmt(double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }

    @Override
    protected void followThePath() {
        Vec3 mobPos = this.getTempMobPos();
        this.maxDistanceToWaypoint = Math.max(this.mob.getBbWidth() * 0.75F, 0.75F);

        int sameHeightEnd = this.path.getNodeCount();
        // Upstream compares against floor(feet Y + 0.5), not the amphibious body midpoint.
        double pathHeight = Math.floor(this.mob.getY() + 0.5D);
        for (int index = this.path.getNextNodeIndex(); index < this.path.getNodeCount(); index++) {
            if (this.path.getNode(index).y != pathHeight) {
                sameHeightEnd = index;
                break;
            }
        }

        BlockPos nextPos = this.path.getNextNodePos();
        if (Math.abs(this.mob.getX() - nextPos.getX() - 0.5D) < this.maxDistanceToWaypoint
                && Math.abs(this.mob.getZ() - nextPos.getZ() - 0.5D) < this.maxDistanceToWaypoint) {
            this.path.advance();
        }

        for (int index = sameHeightEnd - 1; index >= this.path.getNextNodeIndex(); index--) {
            // canMoveDirectly rejects dry land; use the shared collision check in both media.
            Vec3 target = this.path.getEntityPosAtNode(this.mob, index);
            if (isClearForMovementBetween(this.mob, mobPos, target, false)
                    && this.hasSupportBetween(target, (int) pathHeight)) {
                this.path.setNextNodeIndex(index);
                break;
            }
        }

        this.doStuckDetection(mobPos);
    }

    /** Visit every horizontal grid cell of the shortcut at waypoint feet height. */
    private boolean hasSupportBetween(Vec3 target, int feetY) {
        if (this.mob instanceof IShipNavigator ship && ship.canFly()) {
            return true;
        }
        // Preserve the non-air predicate, including water, at waypoint feet height.
        return HorizontalSupportScan.hasSupportBetween(this.mob.getX(), this.mob.getZ(), target.x, target.z,
                (x, z) -> this.isStableDestination(new BlockPos(x, feetY, z)));
    }
}
