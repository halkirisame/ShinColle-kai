package com.lulan.shincolle.ai;

import com.lulan.shincolle.ai.domain.action.ActionKind;
import com.lulan.shincolle.ai.domain.movement.FollowRange;
import com.lulan.shincolle.ai.domain.movement.LookPlanner;
import com.lulan.shincolle.ai.domain.movement.LookReason;
import com.lulan.shincolle.ai.domain.movement.LookRequest;
import com.lulan.shincolle.ai.domain.movement.MovementTarget;
import com.lulan.shincolle.ai.domain.movement.MovementBody;
import com.lulan.shincolle.ai.domain.movement.MovementPlan;
import com.lulan.shincolle.ai.domain.movement.MovementReason;
import com.lulan.shincolle.ai.domain.movement.MovementStep;
import com.lulan.shincolle.ai.domain.movement.GuardMovePlanner;
import com.lulan.shincolle.ai.domain.movement.MovementActivity;
import com.lulan.shincolle.ai.domain.movement.MovementIntent;
import com.lulan.shincolle.ai.domain.movement.MovementPoint;
import com.lulan.shincolle.ai.domain.movement.MovementSettings;
import com.lulan.shincolle.ai.domain.movement.MovementState;
import com.lulan.shincolle.ai.domain.movement.PlannedMove;
import com.lulan.shincolle.ai.domain.movement.StuckDetector;
import com.lulan.shincolle.ai.domain.movement.StuckState;
import com.lulan.shincolle.ai.command.ShipCommandStateAdapter;
import com.lulan.shincolle.ai.domain.command.CommandIssuer;
import com.lulan.shincolle.ai.domain.command.CommandPos;
import com.lulan.shincolle.ai.domain.command.CommandStateOp;
import com.lulan.shincolle.ai.domain.command.MovementOrder;
import com.lulan.shincolle.ai.domain.command.ShipTransitionReason;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.entity.BasicEntityMount;
import com.lulan.shincolle.entity.IShipCannonAttack;
import com.lulan.shincolle.entity.IShipGuardian;
import com.lulan.shincolle.utility.EntityHelper;
import com.lulan.shincolle.utility.FormationHelper;
import com.lulan.shincolle.utility.LogHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.pathfinder.Path;

import java.util.EnumSet;
import java.util.Locale;

/**
 * Ship guarding AI - guard a position or entity.
 * Ported from EntityAIShipGuarding (setMutexBits: 7)
 * <p>
 * Guard types:
 * 0: guard a block, force moving only
 * 1: guard a block, attack while moving
 * 2: guard an entity
 * <p>
 * Attack-while-moving:
 * While GuardType > 0 the fire control goal fires at the target authority's target as the ship
 * moves; this goal only walks and turns the head.
 */
public class ShipGuardingGoal extends Goal {

    private final IShipGuardian host;
    private final Mob hostEntity;
    private final PathNavigation shipNavigator;
    private Entity guarded;
    private double maxDistSq, minDistSq;
    private double distSq;                     // distance to guard target squared
    private IShipCannonAttack ship;            // host can use cannon
    private double diagPrevX;
    private double diagPrevZ;
    /** The timers, places and moving flag planned by the guard authority. */
    private MovementState.Guard move = GuardMovePlanner.initial();
    /** NEW: whether the ship is getting anywhere on its way. */
    private StuckState stuck = StuckState.NONE;
    /** NEW: the tick an arrival stopped the paths; the selector's stop later in that tick adds none. */
    private int arrivalStopTick = -1;
    /** NEW: the guarded point is a waypoint the route goes on from, so the distances are the pass-through ones. */
    private boolean passThrough;

    public ShipGuardingGoal(IShipGuardian host) {
        this.host = host;
        this.hostEntity = (Mob) host;
        this.shipNavigator = hostEntity.getNavigation();
        this.distSq = 1D;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK, Goal.Flag.JUMP));

        // check if host supports cannon/aircraft attack
        if (host instanceof IShipCannonAttack) {
            this.ship = (IShipCannonAttack) host;
        }

    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public boolean canUse() {
        if (ShipActionGate.blocked(this.hostEntity, ActionKind.MOVEMENT)) return false;
        if (isGuardBlocked()) {
            return false;
        }

        // check if guard target exists
        if (releaseCompletedMove()) {
            return false;
        }
        return !host.getIsRiding() && checkGuardTarget();
    }

    @Override
    public boolean canContinueToUse() {
        if (ShipActionGate.blocked(this.hostEntity, ActionKind.MOVEMENT)) return false;
        if (isGuardBlocked()) {
            this.stop();
            return false;
        }

        // still outside min range, keep going
        if (releaseCompletedMove()) {
            return false;
        }
        if (host.getIsRiding()) {
            this.stop();
            return false;
        }
        if (this.distSq > this.minDistSq) {
            return true;
        }

        // other cases: still has path or can re-evaluate
        return (shipNavigator != null && !shipNavigator.isDone()) || canUse();
    }

    @Override
    public void start() {
        int now = this.hostEntity.tickCount;
        this.move = GuardMovePlanner.start(this.move, now);
        this.stuck = StuckState.NONE;
    }

    @Override
    public void stop() {
        this.guarded = null;
        this.move = GuardMovePlanner.stopped(this.move);
        // the paths are already dropped, and the executor's last request stays the arrival
        if (this.arrivalStopTick == this.hostEntity.tickCount) return;
        ShipMovementExecutor.run(this.hostEntity, ShipMovementExecutor.GOAL_STOPPED);
    }

    @Override
    public void tick() {
        if (ShipActionGate.blocked(this.hostEntity, ActionKind.MOVEMENT)) return;
        if (isGuardBlocked()) {
            this.stop();
            return;
        }
        if (releaseCompletedMove()) {
            return;
        }
        if (host.getIsRiding()) {
            this.stop();
            return;
        }
        // ===== Update guarding movement =====
        if (host == null)
            return;
        this.tickMoveNew();
    }

    /**
     * NEW: the moves planned by the guard planner and carried out by the executor; a stuck ship
     * recovers, and only a stuck or far one teleports.
     */
    private void tickMoveNew() {
        int now = this.hostEntity.tickCount;
        this.stuck = StuckDetector.observe(this.stuck, GuardMovePlanner.travelling(this.distSq,
                new FollowRange(this.minDistSq, this.maxDistSq), isTemporaryBlockMove()),
                ShipMovementGate.point(this.hostEntity), now);
        this.move = GuardMovePlanner.count(this.move, this.stuck.stuck());

        // update guard position every 8 ticks
        if (GuardMovePlanner.anchorResolveDue(this.move, now)) {
            this.move = GuardMovePlanner.anchorResolved(this.move, now);
            if (!checkGuardTarget())
                return;
        }

        PlannedMove<MovementState.Guard> planned = GuardMovePlanner.move(this.move, new GuardMovePlanner.Facts(
                this.distSq, new FollowRange(this.minDistSq, this.maxDistSq), isTemporaryBlockMove(),
                // a guarded point is in the ship's dimension; checkGuardTarget ends the guard otherwise
                ShipCommandStateAdapter.handle(this.guarded != null ? this.guarded : this.hostEntity).dimension(),
                ShipMovementGate.teleportRule(), this.stuck));
        this.move = GuardMovePlanner.pathed(planned.state(), ShipMovementExecutor.run(this.hostEntity, planned.plan()));

        if (LogHelper.diagEnabled()) {
            logMoveProgress();
        }

        // look at what it is engaged with, else toward the guard position
        ShipMovementExecutor.look(this.hostEntity, LookPlanner.choose(ShipCombatGate.engagedTarget(this.hostEntity),
                new LookRequest(new MovementTarget.Point(this.move.destination()), 30F,
                        (float) this.hostEntity.getMaxHeadXRot(), LookReason.GUARD)));
    }

    /** Whether the ship is on its way to the guarded point; NEW keeps it in the guard state. */
    private boolean moving() {
        return this.move.moving();
    }

    /**
     * Guard-move movement diagnostic. Reports whether the path advances, how far the
     * ship is from the node it is steering at, and how far it actually moved.
     */
    private void logMoveProgress() {
        double step = Math.hypot(this.hostEntity.getX() - this.diagPrevX,
                this.hostEntity.getZ() - this.diagPrevZ);
        this.diagPrevX = this.hostEntity.getX();
        this.diagPrevZ = this.hostEntity.getZ();

        MovementPoint goal = this.move.destination();
        String node = "none";
        Path path = this.shipNavigator == null ? null : this.shipNavigator.getPath();
        if (path != null) {
            // getNextNodePos() indexes the node list directly, so it throws once the path
            // is done and the index has run past the last node. Report the counts either way.
            boolean done = path.isDone();
            node = path.getNextNodeIndex() + "/" + path.getNodeCount() + " done=" + done;
            if (!done) {
                Vec3i next = path.getNextNodePos();
                node += "@" + next.getX() + "," + next.getY() + "," + next.getZ()
                        + " dx=" + fmt(Math.abs(this.hostEntity.getX() - (next.getX() + 0.5D)))
                        + " dy=" + fmt(Math.abs(this.hostEntity.getY() - next.getY()))
                        + " dz=" + fmt(Math.abs(this.hostEntity.getZ() - (next.getZ() + 0.5D)));
            }
        }

        LogHelper.diag("DIAG: guard move ship=" + this.hostEntity
                + " pos=" + fmt(this.hostEntity.getX()) + "," + fmt(this.hostEntity.getY())
                + "," + fmt(this.hostEntity.getZ())
                + " goal=" + fmt(goal.x()) + "," + fmt(goal.y()) + "," + fmt(goal.z())
                + " distSq=" + fmt(this.distSq) + " maxDistSq=" + fmt(this.maxDistSq)
                + " passThrough=" + this.passThrough
                + " step=" + fmt(step)
                + " yaw=" + fmt(this.hostEntity.getYRot())
                + " speed=" + fmt(this.hostEntity.getSpeed())
                + " dm=" + fmt(this.hostEntity.getDeltaMovement().x)
                + "," + fmt(this.hostEntity.getDeltaMovement().z)
                + " node=[" + node + "]"
                + " liquid=" + EntityHelper.checkEntityIsInLiquid(this.hostEntity)
                + " ground=" + this.hostEntity.onGround()
                + " moving=" + moving() + " find=" + this.move.repathIn()
                + " tpT=" + this.move.stillTimer()
                + " tpD=" + this.move.farTimer());
    }

    private static String fmt(double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }

    private boolean isTemporaryBlockMove() {
        BasicEntityShip ship = commandedShip();
        return ship != null && ship.shouldReleaseGuardOnArrival();
    }

    /** The ship this goal moves for: the host itself, or the ship a mount carries. */
    private BasicEntityShip commandedShip() {
        if (this.host instanceof BasicEntityShip ship) return ship;
        return this.host instanceof BasicEntityMount mount ? mount.getHost() : null;
    }

    /** The mount's guard goal completes the move while the ship itself is riding. */
    private boolean isGuardBlocked() {
        MovementIntent intent = ShipMovementGate.intent(this.hostEntity);
        return intent == null || !intent.isGuard()
                || !ShipMovementGate.allows(this.hostEntity, MovementActivity.COMMANDED_MOVE);
    }

    /** The standing order of the ship this goal moves for; null for a mount carrying no ship. */
    private MovementOrder commandedOrder() {
        BasicEntityShip commandShip = commandedShip();
        return commandShip == null ? null : commandShip.getCommandState().movement();
    }

    /** Complete only the current reachable path, never a missing or abandoned path. */
    private boolean releaseCompletedMove() {
        if (!isTemporaryBlockMove() || !this.host.isGuardedInCurrentDimension()
                || this.shipNavigator == null) {
            return false;
        }
        // FormationHelper sends mounted move orders to this vehicle, not to the ship.
        PathNavigation moveNavigator = this.shipNavigator;
        if (this.host.getIsRiding()) {
            if (!(this.hostEntity.getVehicle() instanceof BasicEntityMount mount)) {
                return false;
            }
            moveNavigator = mount.getNavigation();
        }
        Path path = moveNavigator.getPath();
        MovementOrder order = commandedOrder();
        if (order == null || order.destination().isEmpty()) return false;
        CommandPos place = order.destination().orElseThrow();
        BlockPos destination = new BlockPos(place.x(), place.y(), place.z());
        if (path == null || !path.isDone() || !path.canReach() || !path.getTarget().equals(destination)) {
            return false;
        }
        BasicEntityShip commandShip = commandedShip();
        if (commandShip == null) return false;
        commandShip.applyCommandState(new CommandIssuer.Ship(ShipTransitionReason.ARRIVED),
                new CommandStateOp.EndMovement());
        // the goal's own stop first, so the executor's last request names the arrival
        this.stop();
        ShipMovementExecutor.run(this.hostEntity, MovementPlan.of(new MovementStep.Stop(
                this.host.getIsRiding() ? MovementBody.VEHICLE : MovementBody.SELF, MovementReason.MOVE_ARRIVED)));
        // a running goal is stopped once more by its selector on the way out
        this.arrivalStopTick = this.hostEntity.tickCount;
        BasicEntityShip basicShip = commandedShip();
        basicShip.sendSyncPacketFlags();
        basicShip.sendSyncPacketGuard();
        return true;
    }

    /**
     * Check and update guard target position.
     * Handles guarded entity (with formation support) and guarded block position.
     *
     * @return true if ship needs to move toward guard position
     */
    private boolean checkGuardTarget() {
        return checkGuardTargetNew();
    }

    /** NEW: {@link #checkGuardTarget()} with the destination and the guarded entity's last place in the guard state. */
    private boolean checkGuardTargetNew() {
        BasicEntityShip commandShip = commandedShip();
        if (commandShip == null) return false;
        this.guarded = host.getGuardedEntity();
        // the host answers for the distances and the pickup; the formation ranges apply to a host with cannons
        MovementSettings hostSettings = ShipMovementGate.settings(host);
        MovementSettings cannonSettings = ShipMovementGate.settings(this.ship);
        MovementOrder order = commandedOrder();
        this.passThrough = false;

        if (this.guarded != null) {
            // guarded entity is dead or invalid
            if (!this.guarded.isAlive() || this.guarded.level() != this.hostEntity.level()) {
                commandShip.applyCommandState(new CommandIssuer.Ship(ShipTransitionReason.GUARD_TARGET_LOST),
                        new CommandStateOp.EndMovement());
                this.stop();
                return false;
            }

            MovementPoint anchor = ShipMovementGate.point(this.guarded);
            if (hostSettings.formation()) {
                // if guarded entity moved significantly, recalculate position
                if (GuardMovePlanner.formationStale(this.move, anchor)) {
                    double[] place = FormationHelper.getFormationGuardingPos(host, guarded,
                            this.move.anchorMemory().x(), this.move.anchorMemory().z());
                    this.move = GuardMovePlanner.formationPlace(this.move,
                            new MovementPoint(place[0], place[1], place[2]), anchor);
                }
            } else {
                this.move = GuardMovePlanner.toEntity(this.move, anchor);
            }
        } else {
            // An entity guard can be temporarily unresolved while its chunk is unloaded.
            if (order instanceof MovementOrder.GuardEntity) {
                return false;
            }
            // A follow order neither starts nor keeps this goal, so there is no place to walk to; nothing is written.
            if (order instanceof MovementOrder.Follow) {
                return false;
            }
            if (!host.isGuardedInCurrentDimension()) {
                commandShip.applyCommandState(new CommandIssuer.Ship(ShipTransitionReason.GUARD_OTHER_DIMENSION),
                        new CommandStateOp.EndMovement());
                this.stop();
                return false;
            }
            CommandPos place = order.destination().orElseThrow();
            this.move = GuardMovePlanner.toPoint(this.move,
                    new MovementPoint(place.x() + 0.5D, place.y() + 0.5D, place.z() + 0.5D));
            // the traversal check marks the waypoint the ship passes through; a mark of another point is stale
            BasicEntityShip marked = commandedShip();
            this.passThrough = marked != null && marked.passThroughWaypoint().filter(place::equals).isPresent();
        }

        FollowRange range = GuardMovePlanner.range(cannonSettings.formation(),
                this.ship != null && order instanceof MovementOrder.GuardEntity, hostSettings.pickItem(),
                hostSettings.followMin(), hostSettings.followMax(), hostEntity.getBbWidth(), this.passThrough,
                this.passThrough && ShipCombatGate.active(this.hostEntity)
                        && ShipCombatGate.engagement(this.hostEntity).engaged(),
                this.passThrough && commandedShip().isPickingItem());
        this.minDistSq = range.minSq();
        this.maxDistSq = range.maxSq();
        this.distSq = this.move.destination().distanceSq(ShipMovementGate.point(this.hostEntity));

        // needs to move if outside max range
        return isTemporaryBlockMove() || this.distSq > this.maxDistSq;
    }
}
