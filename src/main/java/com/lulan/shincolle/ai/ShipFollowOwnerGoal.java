package com.lulan.shincolle.ai;

import com.lulan.shincolle.entity.IShipAttackBase;
import com.lulan.shincolle.handler.ConfigHandler;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.server.ServerDataManager;
import com.lulan.shincolle.utility.DebugProfiler;
import com.lulan.shincolle.utility.FormationHelper;
import com.lulan.shincolle.utility.LogHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;

import java.util.EnumSet;

/**
 * Follow owner goal with formation support.
 * Ported from EntityAIShipFollowOwner (setMutexBits: 7)
 * <p>
 * Triggers when distance > maxDistSq.
 * Continues until distance < minDistSq.
 * Teleports if distance > configTP or stuck time > configTP.
 * Formation mode uses FormationHelper for position calculation.
 */
public class ShipFollowOwnerGoal extends Goal {
    private static final double OWNER_TELEPORT_Y_OFFSET = 0.75D;

    private final IShipAttackBase host;
    private final Mob hostEntity;
    private final PathNavigation shipNavigator;
    private final double[] ownerPosOld; // last recorded owner position
    private LivingEntity owner;
    private int checkTP_T, checkTP_D; // teleport cooldown counters
    private int findCooldown; // path navigation cooldown
    private double maxDistSq;
    private double minDistSq;
    private double distSq;
    private double[] pos; // target position
    /** next entity tick at which a follow-blocked reason may be logged */
    private int nextBlockLogTick;

    public ShipFollowOwnerGoal(IShipAttackBase entity) {
        this.host = entity;
        this.hostEntity = (Mob) entity;
        this.shipNavigator = this.hostEntity.getNavigation();
        this.distSq = 1D;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK, Goal.Flag.JUMP));

        this.pos = new double[]{hostEntity.getX(), hostEntity.getY(), hostEntity.getZ()};
        this.ownerPosOld = new double[]{hostEntity.getX(), hostEntity.getY(), hostEntity.getZ()};
    }

    @Override
    public boolean canUse() {
        ProfilerFiller profiler = DebugProfiler.push(this.hostEntity.level(), "shincolle.ai.follow_owner.can_use");
        try {
            if (host == null) {
                DebugProfiler.count(profiler, "shincolle.ai.follow_owner.blocked.no_host");
                return false;
            }

            if (isFollowBlockedState()) {
                return false;
            }

            LivingEntity ownerEntity = resolveOwner();
            if (ownerEntity == null) {
                DebugProfiler.count(profiler, "shincolle.ai.follow_owner.blocked.no_owner");
                return false;
            }

            this.owner = ownerEntity;
            updateDistance();

            boolean canUse = distSq > this.maxDistSq;
            if (canUse) {
                DebugProfiler.count(profiler, "shincolle.ai.follow_owner.can_use.success");
            } else {
                DebugProfiler.count(profiler, "shincolle.ai.follow_owner.can_use.in_range");
            }
            return canUse;
        } finally {
            DebugProfiler.pop(profiler);
        }
    }

    @Override
    public boolean canContinueToUse() {
        ProfilerFiller profiler = DebugProfiler.push(this.hostEntity.level(), "shincolle.ai.follow_owner.continue");
        try {
            if (host == null || owner == null) {
                DebugProfiler.count(profiler, "shincolle.ai.follow_owner.continue.no_host_or_owner");
                return false;
            }

            // owner is non-null here - safe to dereference in updateDistance()
            updateDistance();

            if (isFollowBlockedState()) {
                this.stop();
                return false;
            }

            // still outside min range, keep going
            if (this.distSq > this.minDistSq) {
                DebugProfiler.count(profiler, "shincolle.ai.follow_owner.continue.keep_following");
                return true;
            }

            boolean cont = !shipNavigator.isDone() || canUse();
            if (!cont) {
                DebugProfiler.count(profiler, "shincolle.ai.follow_owner.continue.finished");
            }
            return cont;
        } finally {
            DebugProfiler.pop(profiler);
        }
    }

    @Override
    public void start() {
        this.findCooldown = 10;
        this.checkTP_T = 0;
        this.checkTP_D = 0;
    }

    @Override
    public void stop() {
        this.owner = null;
        this.shipNavigator.stop();
    }

    @Override
    public void tick() {
        ProfilerFiller profiler = DebugProfiler.push(this.hostEntity.level(), "shincolle.ai.follow_owner.tick");
        try {
            if (host == null) {
                DebugProfiler.count(profiler, "shincolle.ai.follow_owner.tick.no_host");
                return;
            }

            this.findCooldown--;
            this.checkTP_T++;

            // update follow range every 32 ticks
            if (hostEntity.tickCount % 32 == 0) {
                LivingEntity ownerEntity = resolveOwner();
                if (ownerEntity != null) {
                    this.owner = ownerEntity;
                    updateDistance();
                } else {
                    DebugProfiler.count(profiler, "shincolle.ai.follow_owner.tick.owner_lost");
                    this.stop();
                    return;
                }
            }

            // reached min distance, stop
            if (this.distSq <= this.minDistSq) {
                this.shipNavigator.stop();
            }

            // pathfind every cooldown cycle
            if (this.findCooldown <= 0) {
                this.findCooldown = 32;
                DebugProfiler.count(profiler, "shincolle.ai.follow_owner.tick.path_request");
                this.shipNavigator.moveTo(pos[0], pos[1], pos[2], 1D);
            }

            // look toward owner
            if (this.owner != null) {
                this.hostEntity.getLookControl().setLookAt(this.owner, 20F, 40F);
            }

            // ===== Teleport check =====
            if (!ConfigHandler.canTeleport())
                return;

            // distance-based teleport
            if (this.distSq > ConfigHandler.shipTeleport[1]) {
                this.checkTP_D++;

                if (this.checkTP_D > ConfigHandler.shipTeleport[0]) {
                    this.checkTP_D = 0;
                    DebugProfiler.count(profiler, "shincolle.ai.follow_owner.tick.teleport_by_distance");
                    LogHelper.debug("DEBUG: follow AI: distSQ > " + ConfigHandler.shipTeleport[1] +
                            " , teleport to target.");
                    applyTeleportToOwner();
                    return;
                }
            }

            // stuck-time-based teleport
            if (this.checkTP_T > ConfigHandler.shipTeleport[0]) {
                this.checkTP_T = 0;
                DebugProfiler.count(profiler, "shincolle.ai.follow_owner.tick.teleport_by_stuck_time");
                LogHelper.debug("DEBUG: follow AI: stuck time exceeded, teleport to target.");
                applyTeleportToOwner();
            }
        } finally {
            DebugProfiler.pop(profiler);
        }
    }

    /**
     * Teleport the ship next to its owner.
     * <p>
     * Mirrors upstream EntityHelper#applyTeleport: refuse to teleport into a
     * chunk that isn't loaded, and drop the current path first - otherwise the
     * ship arrives still holding a path back to where it came from and
     * immediately walks away again.
     */
    private void applyTeleportToOwner() {
        double tx = this.owner.getX();
        double ty = this.owner.getY() + OWNER_TELEPORT_Y_OFFSET;
        double tz = this.owner.getZ();

        if (!this.hostEntity.level().hasChunkAt(BlockPos.containing(tx, ty, tz))) {
            LogHelper.debug("DEBUG: follow AI: teleport skipped, destination chunk not loaded");
            return;
        }

        this.shipNavigator.stop();
        this.hostEntity.teleportTo(tx, ty, tz);
    }

    /**
     * Update follow distances and target position.
     * Formation mode: fixed tight distances, formation-adjusted position.
     * Non-formation: configurable FollowMin/FollowMax with entity width.
     */
    private void updateDistance() {
        // formation mode
        if (host.getStateMinor(ID.M.FormatType) > 0) {
            this.minDistSq = 4D;
            this.maxDistSq = 7D;

            // if owner moved significantly, recalculate formation position
            double dx = ownerPosOld[0] - owner.getX();
            double dy = ownerPosOld[1] - owner.getY();
            double dz = ownerPosOld[2] - owner.getZ();
            double dsq = dx * dx + dy * dy + dz * dz;

            if (dsq > 7) {
                pos = FormationHelper.getFormationGuardingPos(host, owner,
                        ownerPosOld[0], ownerPosOld[2]);

                ownerPosOld[0] = owner.getX();
                ownerPosOld[1] = owner.getY();
                ownerPosOld[2] = owner.getZ();

                // draw moving particle
                if (owner instanceof ServerPlayer player) {
                    boolean showPart = com.lulan.shincolle.handler.ConfigHandler.alwaysShowTeamCircle() ||
                            player.getMainHandItem().getItem() instanceof com.lulan.shincolle.item.PointerItem ||
                            player.getOffhandItem().getItem() instanceof com.lulan.shincolle.item.PointerItem;
                    if (showPart) {
                        com.lulan.shincolle.utility.ParticleHelper.spawnTeamCircleAtPlayer(player, pos[0], pos[1], pos[2], 4);
                    }
                }
            }

            if (this.hostEntity.tickCount % 16 == 0) {
                if (owner instanceof ServerPlayer player) {
                    boolean showPart = com.lulan.shincolle.handler.ConfigHandler.alwaysShowTeamCircle() ||
                            player.getMainHandItem().getItem() instanceof com.lulan.shincolle.item.PointerItem ||
                            player.getOffhandItem().getItem() instanceof com.lulan.shincolle.item.PointerItem;
                    if (showPart) {
                        com.lulan.shincolle.utility.ParticleHelper.spawnTeamCircleAtPlayer(player, pos[0], pos[1], pos[2], 6);
                    }
                }
            }

            // upstream widens the formation leash to 64 (not 16) while the ship
            // is allowed to wander off and pick up items
            if (host.getStateFlag(ID.F.PickItem))
                this.maxDistSq = 64D;
        }
        // no formation
        else {
            float fMin = host.getStateMinor(ID.M.FollowMin) + hostEntity.getBbWidth() * 0.75F;
            float fMax = host.getStateMinor(ID.M.FollowMax) + hostEntity.getBbWidth() * 0.75F;

            if (host.getStateFlag(ID.F.PickItem))
                fMax += 5F;

            this.minDistSq = fMin * fMin;
            this.maxDistSq = fMax * fMax;

            pos[0] = owner.getX();
            pos[1] = owner.getY();
            pos[2] = owner.getZ();
        }

        // calculate distance to target position
        double distX = pos[0] - this.hostEntity.getX();
        double distY = pos[1] - this.hostEntity.getY();
        double distZ = pos[2] - this.hostEntity.getZ();
        this.distSq = distX * distX + distY * distY + distZ * distZ;

    }

    /**
     * Guard checks shared by canUse/canContinueToUse.
     * Mirrors legacy follow-owner preconditions.
     */
    private boolean isFollowBlockedState() {
        String reason = null;

        // getIsLeashed() was missing here - a leashed ship kept trying to walk
        // to its owner and fought the leash tether instead of staying put.
        if (this.host.getIsSitting()) {
            reason = "sitting";
        } else if (this.host.getIsRiding()) {
            reason = "riding";
        } else if (this.host.getIsLeashed()) {
            reason = "leashed";
        } else if (!this.host.getStateFlag(ID.F.CanFollow)) {
            reason = "CanFollow flag off";
        } else if (this.host.getStateMinor(ID.M.CraneState) > 0) {
            reason = "crane state " + this.host.getStateMinor(ID.M.CraneState);
        } else if (this.host.getStateMinor(ID.M.NumGrudge) <= 0) {
            reason = "no grudge (fuel) - NumGrudge=" + this.host.getStateMinor(ID.M.NumGrudge);
        }

        // Report the reason (and any owner-resolution failure) about once a
        // second so "my ships won't follow" can be traced to the exact gate
        // that is blocking, without flooding the log every tick.
        // NOTE: deliberately a >= counter, not `tickCount % 20 == 0` - canUse()
        // is only invoked on every other tick (parity set by serverTick +
        // entityId), so an exact-modulo gate can never fire for some entities.
        if (this.hostEntity.tickCount >= this.nextBlockLogTick) {
            this.nextBlockLogTick = this.hostEntity.tickCount + 20;
            if (reason != null) {
                LogHelper.debug("DEBUG: follow AI: " + this.hostEntity + " blocked: " + reason);
            } else if (resolveOwner() == null) {
                LogHelper.debug("DEBUG: follow AI: " + this.hostEntity
                        + " not blocked, but owner unresolved (uid=" + this.host.getPlayerUID() + ")");
            }
        }

        return reason != null;
    }

    /**
     * Resolve owner from TamableAnimal.getOwner().
     * Original used EntityHelper.getEntityPlayerByUID(host.getPlayerUID()).
     * Both BasicEntityShip and BasicEntityMount extend TamableAnimal.
     */
    private LivingEntity resolveOwner() {
        LivingEntity found = null;

        int uid = this.host.getPlayerUID();
        if (uid > 0 && !this.hostEntity.level().isClientSide()) {
            found = ServerDataManager.getPlayerByUID(uid);
        }

        if (found == null && hostEntity instanceof TamableAnimal tamable) {
            found = tamable.getOwner();
        }

        // Upstream rejects an owner in another dimension (it compared
        // owner.dimension != host.dimension in both shouldExecute and
        // updateTask). Without this the ship would follow - and teleport
        // toward - raw coordinates from a different level.
        if (found != null && found.level() != this.hostEntity.level()) {
            return null;
        }
        return found;
    }
}
