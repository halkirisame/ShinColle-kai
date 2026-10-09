package com.lulan.shincolle.ai;

import com.lulan.shincolle.ai.command.ShipCommandStateAdapter;
import com.lulan.shincolle.ai.domain.action.ActionKind;
import com.lulan.shincolle.ai.domain.TargetHandle;
import com.lulan.shincolle.ai.domain.movement.FollowMovePlanner;
import com.lulan.shincolle.ai.domain.movement.FollowRange;
import com.lulan.shincolle.ai.domain.movement.LookPlanner;
import com.lulan.shincolle.ai.domain.movement.LookReason;
import com.lulan.shincolle.ai.domain.movement.LookRequest;
import com.lulan.shincolle.ai.domain.movement.MovementTarget;
import com.lulan.shincolle.ai.domain.movement.MovementActivity;
import com.lulan.shincolle.ai.domain.movement.MovementIntent;
import com.lulan.shincolle.ai.domain.movement.MovementPoint;
import com.lulan.shincolle.ai.domain.movement.MovementState;
import com.lulan.shincolle.ai.domain.movement.PlannedMove;
import com.lulan.shincolle.ai.domain.movement.StuckDetector;
import com.lulan.shincolle.ai.domain.movement.StuckState;
import com.lulan.shincolle.ai.domain.movement.TeleportRule;
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
import java.util.Optional;

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
    private int nextOwnerResolveTick;
    private int nextParticleTick;
    /** NEW: the timers and places, in place of the fields above that LEGACY keeps. */
    private MovementState.Follow move;
    /** NEW: whether the ship is getting anywhere on its way. */
    private StuckState stuck = StuckState.NONE;

    public ShipFollowOwnerGoal(IShipAttackBase entity) {
        this.host = entity;
        this.hostEntity = (Mob) entity;
        this.shipNavigator = this.hostEntity.getNavigation();
        this.distSq = 1D;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK, Goal.Flag.JUMP));

        this.pos = new double[]{hostEntity.getX(), hostEntity.getY(), hostEntity.getZ()};
        this.ownerPosOld = new double[]{hostEntity.getX(), hostEntity.getY(), hostEntity.getZ()};
        this.move = FollowMovePlanner.initial(ShipMovementGate.point(this.hostEntity));
    }

    @Override
    public boolean canUse() {
        if (ShipActionGate.blocked(this.hostEntity, ActionKind.MOVEMENT)) return false;
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
        if (ShipActionGate.blocked(this.hostEntity, ActionKind.MOVEMENT)) return false;
        ProfilerFiller profiler = DebugProfiler.push(this.hostEntity.level(), "shincolle.ai.follow_owner.continue");
        try {
            if (host == null || owner == null) {
                DebugProfiler.count(profiler, "shincolle.ai.follow_owner.continue.no_host_or_owner");
                return false;
            }

            // owner is non-null here - safe to dereference in updateDistance()
            // NEW measures again only when it looks up its owner, every 32 ticks
            if (!ShipMovementGate.active()) {
                updateDistance();
            }

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
        if (ShipMovementGate.active()) {
            this.move = FollowMovePlanner.start(this.move, this.hostEntity.tickCount);
            this.stuck = StuckState.NONE;
            this.nextParticleTick = this.hostEntity.tickCount;
            return;
        }
        this.findCooldown = 10;
        this.checkTP_T = 0;
        this.checkTP_D = 0;
        int now = this.hostEntity.tickCount;
        this.nextOwnerResolveTick = now;
        this.nextParticleTick = now;
    }

    @Override
    public void stop() {
        this.owner = null;
        if (ShipMovementGate.active()) {
            ShipMovementExecutor.run(this.hostEntity, ShipMovementExecutor.GOAL_STOPPED);
            return;
        }
        this.shipNavigator.stop();
    }

    @Override
    public void tick() {
        if (ShipActionGate.blocked(this.hostEntity, ActionKind.MOVEMENT)) return;
        ProfilerFiller profiler = DebugProfiler.push(this.hostEntity.level(), "shincolle.ai.follow_owner.tick");
        try {
            if (host == null) {
                DebugProfiler.count(profiler, "shincolle.ai.follow_owner.tick.no_host");
                return;
            }
            if (ShipMovementGate.active()) {
                this.tickNew(profiler);
                return;
            }

            this.findCooldown--;
            this.checkTP_T++;

            // update follow range every 32 ticks
            int now = this.hostEntity.tickCount;
            if (now >= this.nextOwnerResolveTick) {
                this.nextOwnerResolveTick = now + 32;
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
     * NEW: the moves planned by the follow planner and carried out by the executor; a stuck ship
     * recovers, and only a stuck or far one teleports.
     */
    private void tickNew(ProfilerFiller profiler) {
        int now = this.hostEntity.tickCount;
        FollowRange range = new FollowRange(this.minDistSq, this.maxDistSq);
        this.stuck = StuckDetector.observe(this.stuck, FollowMovePlanner.travelling(this.distSq, range),
                ShipMovementGate.point(this.hostEntity), now);
        this.move = FollowMovePlanner.count(this.move, this.stuck.stuck());

        // update follow range every 32 ticks
        if (FollowMovePlanner.ownerResolveDue(this.move, now)) {
            this.move = FollowMovePlanner.ownerResolved(this.move, now);
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

        Optional<MovementPoint> ownerNow = this.owner == null ? Optional.empty()
                : Optional.of(ShipMovementGate.point(this.owner));
        TeleportRule teleportRule = ShipMovementGate.teleportRule();
        var recallRemaining = ShipMovementGate.followRecallRemaining(this.hostEntity, this.owner);
        if (recallRemaining.isPresent()) {
            this.move = FollowMovePlanner.afterOwnerJump(this.move, this.distSq,
                    ownerNow.map(point -> point.distanceSq(ShipMovementGate.point(this.hostEntity))).orElse(0D),
                    teleportRule, recallRemaining.getAsLong());
        }
        PlannedMove<MovementState.Follow> planned = FollowMovePlanner.move(this.move, new FollowMovePlanner.Facts(
                this.distSq, new FollowRange(this.minDistSq, this.maxDistSq), ownerNow,
                ShipCommandStateAdapter.handle(this.owner == null ? this.hostEntity : this.owner).dimension(),
                teleportRule, this.stuck,
                ownerNow.map(point -> point.distanceSq(ShipMovementGate.point(this.hostEntity))).orElse(this.distSq),
                flagshipPlaceDistanceSq(teleportRule)));
        this.move = planned.state();
        ShipMovementExecutor.run(this.hostEntity, planned.plan());

        // look at what it is engaged with, else toward the owner
        Optional<TargetHandle> engaged = ShipCombatGate.engagedTarget(this.hostEntity);
        if (this.owner != null) {
            ShipMovementExecutor.look(this.hostEntity, LookPlanner.choose(engaged, new LookRequest(
                    new MovementTarget.Entity(ShipCommandStateAdapter.handle(this.owner)), 20F, 40F,
                    LookReason.FOLLOW_OWNER)));
        } else {
            engaged.ifPresent(target -> ShipMovementExecutor.look(this.hostEntity, LookPlanner.choose(
                    engaged, new LookRequest(new MovementTarget.Entity(target), 20F, 40F,
                            LookReason.FOLLOW_OWNER))));
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
        if (ShipMovementGate.active()) {
            this.updateDistanceNew();
            return;
        }
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

            int now = this.hostEntity.tickCount;
            if (now >= this.nextParticleTick) {
                this.nextParticleTick = now + 16;
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

    /** NEW: {@link #updateDistance()} with the destination and the owner's last place in the follow state. */
    private void updateDistanceNew() {
        var active = ShipFormationStateAdapter.active(host);
        boolean formation = active.isPresent();
        FollowRange range = FollowMovePlanner.range(formation, host.getStateFlag(ID.F.PickItem),
                host.getStateMinor(ID.M.FollowMin), host.getStateMinor(ID.M.FollowMax), hostEntity.getBbWidth());
        MovementPoint ownerPoint = ShipMovementGate.point(owner);
        if (formation) {
            // if owner moved significantly, recalculate formation position
            if (FollowMovePlanner.formationStale(this.move, ownerPoint)) {
                double[] place = FormationHelper.getFormationGuardingPos(host, owner,
                        this.move.anchorMemory().x(), this.move.anchorMemory().z());
                this.move = FollowMovePlanner.formationPlace(this.move,
                        new MovementPoint(place[0], place[1], place[2]), ownerPoint,
                        active.orElseThrow().slot());
                showTeamCircle(this.move.destination(), 4);
            }

            int now = this.hostEntity.tickCount;
            if (now >= this.nextParticleTick) {
                this.nextParticleTick = now + 16;
                showTeamCircle(this.move.destination(), 6);
            }
        } else {
            this.move = FollowMovePlanner.toOwner(this.move, ownerPoint);
        }
        this.minDistSq = range.minSq();
        this.maxDistSq = range.maxSq();

        // calculate distance to target position
        this.distSq = this.move.destination().distanceSq(ShipMovementGate.point(this.hostEntity));
    }

    /**
     * NEW: the distance from the ship to the flagship slot worked out for the owner as it stands now, only on a
     * tick where the stuck teleport would be judged against a stored flagship slot; otherwise infinity, which
     * never counts as arrived. The slot is only read: {@link FormationHelper#getFormationGuardingPos} reads the
     * formation settings and the blocks around the owner and writes neither the owner nor the follow state.
     * That block read would load chunks, so it is only made for an owner in the ship's own level whose chunk is
     * already loaded; an owner held from before a dimension change, or one standing in unloaded ground, is
     * never looked up. Nor is it made while the ship's slot is no longer the flagship's, whose place is not
     * round the owner, or while any chunk the safe-spot search round the owner's block could reach is unloaded.
     */
    private double flagshipPlaceDistanceSq(TeleportRule teleportRule) {
        if (this.owner == null || !FollowMovePlanner.flagshipPlaceDistanceWanted(this.move, teleportRule)) {
            return Double.POSITIVE_INFINITY;
        }
        if (this.owner.level() != this.hostEntity.level()
                || !this.hostEntity.level().hasChunkAt(this.owner.blockPosition())) {
            return Double.POSITIVE_INFINITY;
        }
        var active = ShipFormationStateAdapter.active(host);
        if (active.isEmpty() || !FollowMovePlanner.flagshipSlot(active.get().slot())) {
            return Double.POSITIVE_INFINITY;
        }
        // the search reads up to 3 blocks round the owner's block; a chunk is 16 wide, so the chunks of the four
        // corners 4 out are every chunk it can touch
        BlockPos at = this.owner.blockPosition();
        for (int dx = -4; dx <= 4; dx += 8) {
            for (int dz = -4; dz <= 4; dz += 8) {
                if (!this.hostEntity.level().hasChunkAt(at.getX() + dx, at.getZ() + dz)) {
                    return Double.POSITIVE_INFINITY;
                }
            }
        }
        double[] place = FormationHelper.getFormationGuardingPos(host, owner,
                this.move.anchorMemory().x(), this.move.anchorMemory().z());
        return new MovementPoint(place[0], place[1], place[2]).distanceSq(ShipMovementGate.point(this.hostEntity));
    }

    /** The team circle at the formation place, while the owner holds a pointer or the config says always. */
    private void showTeamCircle(MovementPoint place, int type) {
        if (owner instanceof ServerPlayer player) {
            boolean showPart = ConfigHandler.alwaysShowTeamCircle()
                    || player.getMainHandItem().getItem() instanceof com.lulan.shincolle.item.PointerItem
                    || player.getOffhandItem().getItem() instanceof com.lulan.shincolle.item.PointerItem;
            if (showPart) {
                com.lulan.shincolle.utility.ParticleHelper.spawnTeamCircleAtPlayer(player, place.x(), place.y(),
                        place.z(), type);
            }
        }
    }

    /**
     * Guard checks shared by canUse/canContinueToUse.
     * Mirrors legacy follow-owner preconditions.
     */
    private boolean isFollowBlockedState() {
        if (ShipMovementGate.active()) {
            return !(ShipMovementGate.intent(this.hostEntity) instanceof MovementIntent.FollowOwner)
                    || !ShipMovementGate.allows(this.hostEntity, MovementActivity.COMMANDED_MOVE);
        }
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
        } else if (ShipMovementGate.craneBusy(this.host)) {
            reason = "crane state " + ShipMovementGate.craneState(this.host);
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
