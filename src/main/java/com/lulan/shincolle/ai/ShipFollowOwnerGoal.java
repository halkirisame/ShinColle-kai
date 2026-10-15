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
import com.lulan.shincolle.ai.domain.movement.MovementSettings;
import com.lulan.shincolle.ai.domain.movement.MovementState;
import com.lulan.shincolle.ai.domain.movement.PlannedMove;
import com.lulan.shincolle.ai.domain.movement.StuckDetector;
import com.lulan.shincolle.ai.domain.movement.StuckState;
import com.lulan.shincolle.ai.domain.movement.TeleportRule;
import com.lulan.shincolle.entity.IShipAttackBase;
import com.lulan.shincolle.handler.ConfigHandler;
import com.lulan.shincolle.server.ServerDataManager;
import com.lulan.shincolle.utility.DebugProfiler;
import com.lulan.shincolle.utility.FormationHelper;
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
    private final IShipAttackBase host;
    private final Mob hostEntity;
    private final PathNavigation shipNavigator;
    private LivingEntity owner;
    private double maxDistSq;
    private double minDistSq;
    private double distSq;
    private int nextParticleTick;
    /** The timers and places planned by the follow authority. */
    private MovementState.Follow move;
    /** NEW: whether the ship is getting anywhere on its way. */
    private StuckState stuck = StuckState.NONE;

    public ShipFollowOwnerGoal(IShipAttackBase entity) {
        this.host = entity;
        this.hostEntity = (Mob) entity;
        this.shipNavigator = this.hostEntity.getNavigation();
        this.distSq = 1D;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK, Goal.Flag.JUMP));

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
        this.move = FollowMovePlanner.start(this.move, this.hostEntity.tickCount);
        this.stuck = StuckState.NONE;
        this.nextParticleTick = this.hostEntity.tickCount;
    }

    @Override
    public void stop() {
        this.owner = null;
        ShipMovementExecutor.run(this.hostEntity, ShipMovementExecutor.GOAL_STOPPED);
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
            this.tickNew(profiler);
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
                flagshipPlaceDistanceSq(teleportRule), ShipMovementGate.ownerAirborne(this.owner)));
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
     * Update follow distances and target position.
     * Formation mode: fixed tight distances, formation-adjusted position.
     * Non-formation: configurable FollowMin/FollowMax with entity width.
     */
    private void updateDistance() {
        this.updateDistanceNew();
    }

    /** NEW: {@link #updateDistance()} with the destination and the owner's last place in the follow state. */
    private void updateDistanceNew() {
        var active = ShipFormationStateAdapter.active(host);
        boolean formation = active.isPresent();
        MovementSettings settings = ShipMovementGate.settings(host);
        FollowRange range = FollowMovePlanner.range(formation, settings.pickItem(),
                settings.followMin(), settings.followMax(), hostEntity.getBbWidth());
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

    /** Intent and permission checks shared by canUse/canContinueToUse. */
    private boolean isFollowBlockedState() {
        return !(ShipMovementGate.intent(this.hostEntity) instanceof MovementIntent.FollowOwner)
                || !ShipMovementGate.allows(this.hostEntity, MovementActivity.COMMANDED_MOVE);
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
