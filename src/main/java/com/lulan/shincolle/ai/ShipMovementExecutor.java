package com.lulan.shincolle.ai;

import com.lulan.shincolle.ai.command.ShipCommandStateAdapter;
import com.lulan.shincolle.ai.domain.TargetHandle;
import com.lulan.shincolle.ai.domain.movement.CombatMove;
import com.lulan.shincolle.ai.domain.movement.FollowRecallRequest;
import com.lulan.shincolle.ai.domain.movement.LookRequest;
import com.lulan.shincolle.ai.domain.movement.MovementBody;
import com.lulan.shincolle.ai.domain.movement.MovementPlan;
import com.lulan.shincolle.ai.domain.movement.MovementPoint;
import com.lulan.shincolle.ai.domain.movement.MovementReason;
import com.lulan.shincolle.ai.domain.movement.MovementStep;
import com.lulan.shincolle.ai.domain.movement.MovementTarget;
import com.lulan.shincolle.ai.domain.movement.PathOutcome;
import com.lulan.shincolle.ai.domain.movement.StuckDetector;
import com.lulan.shincolle.ai.domain.movement.TeleportDenial;
import com.lulan.shincolle.ai.domain.movement.TeleportSafety;
import com.lulan.shincolle.entity.BasicEntityMount;
import com.lulan.shincolle.utility.LogHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;

import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;

/**
 * The one place the movement goals stop, request paths and teleport under NEW. A host has one; it
 * keeps the last request and why the last teleport was refused, for inspection, and when the host
 * last teleported, for the cooldown. Server only, never saved.
 */
public final class ShipMovementExecutor {
    /** What a movement goal's stop does: drop the host's path. */
    public static final MovementPlan GOAL_STOPPED = MovementPlan.of(
            new MovementStep.Stop(MovementBody.SELF, MovementReason.GOAL_STOPPED));
    /** A detour skips points this close to where the body already stands. */
    private static final double DETOUR_SKIP_SQ = 1.5D * 1.5D;

    /** A step carried out, with the combat move behind it if an attack goal asked for it. */
    public record Request(int tick, MovementStep step, Optional<CombatMove.Reason> combat, PathOutcome outcome) {
    }

    private Request last;
    private LookRequest lastLook;
    private Set<TeleportDenial> lastDenials = Set.of();
    private boolean teleported;
    private int lastTeleportAt;
    private FollowRecallRequest followRecall;
    Optional<com.lulan.shincolle.ai.domain.movement.VerticalSeparation.State> separation = Optional.empty();
    Optional<com.lulan.shincolle.ai.domain.movement.VoidRescue.State> voidRescue = Optional.empty();

    public void requestFollowRecall(FollowRecallRequest request) {
        this.followRecall = request;
    }

    void clearFollowRecall() {
        this.followRecall = null;
    }

    /** Reads a short wait only while that owner's original jump is still current. */
    public OptionalLong followRecallRemaining(TargetHandle owner, long now) {
        if (this.followRecall == null) return OptionalLong.empty();
        if (!com.lulan.shincolle.handler.ShipFollowRecallHandler.isCurrent(owner.uuid(), this.followRecall.detectedAt())) {
            this.followRecall = null;
            return OptionalLong.empty();
        }
        OptionalLong remaining = this.followRecall.remaining(owner, now);
        if (remaining.isEmpty()) this.followRecall = null;
        return remaining;
    }

    public Optional<Request> last() {
        return Optional.ofNullable(this.last);
    }

    /** The last look asked for; for inspection, not saved. */
    public Optional<LookRequest> lastLook() {
        return Optional.ofNullable(this.lastLook);
    }

    /**
     * Turns the host's head as {@code request} asks; an entity that is gone or in another dimension
     * is not looked at, and the head is left as it was.
     */
    public static void look(Mob host, LookRequest request) {
        look(host, MovementBody.SELF, request);
    }

    /** As {@link #look(Mob, LookRequest)}, with the head of {@code body}; nothing happens when the ship has no such body. */
    public static void look(Mob host, MovementBody body, LookRequest request) {
        Mob head = body(host, body);
        if (head == null) return;
        if (request.target() instanceof MovementTarget.Point point) {
            MovementPoint at = point.point();
            head.getLookControl().setLookAt(at.x(), at.y(), at.z(), request.yawSpeed(), request.pitchSpeed());
        } else {
            Entity target = resolve(host, ((MovementTarget.Entity) request.target()).handle());
            if (target == null) return;
            head.getLookControl().setLookAt(target, request.yawSpeed(), request.pitchSpeed());
        }
        of(host).lastLook = request;
    }

    /** Why the last teleport asked for was not carried out; empty when it was. */
    public Set<TeleportDenial> lastDenials() {
        return this.lastDenials;
    }

    /** Carries out {@code plan} for {@code host} and returns what became of its path request. */
    public static PathOutcome run(Mob host, MovementPlan plan) {
        return of(host).execute(host, plan, Optional.empty());
    }

    static PathOutcome run(Mob host, MovementPlan plan, CombatMove.Reason combat) {
        return of(host).execute(host, plan, Optional.of(combat));
    }

    private static ShipMovementExecutor of(Mob host) {
        return host instanceof ShipMovementHost movementHost ? movementHost.shipMovementExecutor()
                : new ShipMovementExecutor();
    }

    private PathOutcome execute(Mob host, MovementPlan plan, Optional<CombatMove.Reason> combat) {
        PathOutcome outcome = PathOutcome.NONE;
        for (MovementStep step : plan.steps()) {
            PathOutcome result = this.step(host, step);
            if (step instanceof MovementStep.PathTo) outcome = result;
            this.last = new Request(host.tickCount, step, combat, result);
        }
        return outcome;
    }

    private PathOutcome step(Mob host, MovementStep step) {
        Mob body = body(host, step.body());
        if (body == null) return PathOutcome.UNRESOLVED;
        if (step instanceof MovementStep.SkillMotion motion) {
            MovementPoint velocity = motion.velocity();
            net.minecraft.world.phys.Vec3 vector = new net.minecraft.world.phys.Vec3(velocity.x(), velocity.y(), velocity.z());
            net.minecraft.world.phys.Vec3 destination = body.position().add(vector);
            BlockPos destinationBlock = BlockPos.containing(destination);
            if (vector.lengthSqr() == 0D || ShipSkillAttackGate.running(host)
                    && ShipMovementGate.skillDestination(host, destination)
                    && body.level().hasChunkAt(destinationBlock)
                    && body.level().getWorldBorder().isWithinBounds(destinationBlock)) {
                body.getNavigation().stop();
                body.setDeltaMovement(vector);
                body.hurtMarked = true;
                com.lulan.shincolle.network.ModNetworking.sendToAllTracking(
                        com.lulan.shincolle.network.S2CEntitySyncPacket.syncMotion(body), body);
            } else {
                body.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            }
            return PathOutcome.NONE;
        }
        if (step instanceof MovementStep.Stop) {
            body.getNavigation().stop();
            return PathOutcome.NONE;
        }
        if (step instanceof MovementStep.Teleport teleport) {
            this.teleport(host, body, teleport);
            return PathOutcome.NONE;
        }
        MovementStep.PathTo path = (MovementStep.PathTo) step;
        boolean issued;
        if (path.target() instanceof MovementTarget.Point point) {
            MovementPoint at = point.point();
            issued = body.getNavigation().moveTo(at.x(), at.y(), at.z(), path.speed());
        } else if (path.target() instanceof MovementTarget.Around around) {
            issued = detour(body, around.center(), path.speed());
        } else {
            Entity target = resolve(host, ((MovementTarget.Entity) path.target()).handle());
            // never a path into another dimension or toward what is gone
            if (target == null) return PathOutcome.UNRESOLVED;
            issued = body.getNavigation().moveTo(target, path.speed());
        }
        if (issued) return PathOutcome.ISSUED;
        path.onFailure().ifPresent(jump -> this.teleport(host, body(host, jump.body()), jump));
        return PathOutcome.FAILED;
    }

    /**
     * To the first point beside {@code center}, in the detector's order, that the body can reach and
     * is not already standing at; to {@code center} itself when there is none.
     */
    private static boolean detour(Mob body, MovementPoint center, double speed) {
        for (MovementPoint point : StuckDetector.detourPoints(center)) {
            double dx = point.x() - body.getX();
            double dz = point.z() - body.getZ();
            if (dx * dx + dz * dz < DETOUR_SKIP_SQ) continue;
            BlockPos pos = BlockPos.containing(point.x(), point.y(), point.z());
            if (!body.level().hasChunkAt(pos)) continue;
            Path path = body.getNavigation().createPath(pos, 0);
            if (path != null && path.canReach()) {
                return body.getNavigation().moveTo(point.x(), point.y(), point.z(), speed);
            }
        }
        return body.getNavigation().moveTo(center.x(), center.y(), center.z(), speed);
    }

    /** Carries out a guarded teleport and reports success to the transient recall state. */
    public static boolean tryTeleport(Mob host, MovementStep.Teleport teleport) {
        ShipMovementExecutor executor = of(host);
        if (!ShipMovementGate.active()) return false;
        boolean success = executor.teleport(host, body(host, teleport.body()), teleport);
        executor.last = new Request(host.tickCount, teleport, Optional.empty(), PathOutcome.NONE);
        return success;
    }

    /**
     * Only when {@link TeleportSafety} allows it, onto the first spot with room for the body, and
     * without the path the body was on.
     */
    private boolean teleport(Mob host, Mob body, MovementStep.Teleport teleport) {
        if (body == null) return false;
        if (teleport.reason() == MovementReason.SKILL_ATTACK && !ShipSkillAttackGate.teleportAuthorized(host)) {
            this.lastDenials = Set.of(TeleportDenial.SKILL_NOT_AUTHORIZED);
            return false;
        }
        MovementPoint at = teleport.destination();
        BlockPos pos = BlockPos.containing(at.x(), at.y(), at.z());
        Level level = body.level();
        boolean sameDimension = ShipCommandStateAdapter.handle(body).dimension().equals(teleport.dimension());
        int sinceLast = this.teleported ? host.tickCount - this.lastTeleportAt : Integer.MAX_VALUE;
        boolean loaded = level.hasChunkAt(pos);
        boolean inside = level.getWorldBorder().isWithinBounds(pos);
        Set<TeleportDenial> denied = TeleportSafety.denials(
                new TeleportSafety.Facts(sameDimension, sinceLast, loaded, inside, true), teleport.reason());
        Optional<MovementPoint> landing = Optional.empty();
        if (denied.isEmpty()) {
            landing = landing(host, body, at, teleport.reason());
            denied = TeleportSafety.denials(
                    new TeleportSafety.Facts(true, sinceLast, true, true, landing.isPresent()), teleport.reason());
        }
        this.lastDenials = denied;
        if (!denied.isEmpty()) {
            LogHelper.debug("DEBUG: movement: " + body + " " + teleport.reason() + " teleport refused: " + denied);
            return false;
        }
        MovementPoint to = landing.get();
        LogHelper.debug("DEBUG: movement: " + body + " " + teleport.reason() + " teleport");
        body.getNavigation().stop();
        body.teleportTo(to.x(), to.y(), to.z());
        this.teleported = true;
        this.lastTeleportAt = host.tickCount;
        this.followRecall = null;
        return true;
    }

    /** The first of the safety rule's spots, in its order, in a loaded chunk and with room for the body. */
    private static Optional<MovementPoint> landing(Mob host, Mob body, MovementPoint destination, MovementReason reason) {
        Level level = body.level();
        AABB box = body.getBoundingBox();
        for (MovementPoint point : TeleportSafety.landings(destination)) {
            if (reason == MovementReason.SKILL_ATTACK && !ShipMovementGate.skillDestination(host,
                    new net.minecraft.world.phys.Vec3(point.x(), point.y(), point.z()))) continue;
            BlockPos pos = BlockPos.containing(point.x(), point.y(), point.z());
            if (!level.hasChunkAt(pos) || !level.getWorldBorder().isWithinBounds(pos)) continue;
            if (level.noCollision(body, box.move(point.x() - body.getX(), point.y() - body.getY(),
                    point.z() - body.getZ()))) {
                return Optional.of(point);
            }
        }
        return Optional.empty();
    }

    /** The host, or the mount it rides. */
    private static Mob body(Mob host, MovementBody body) {
        if (body == MovementBody.SELF) return host;
        return host.getVehicle() instanceof BasicEntityMount mount ? mount : null;
    }

    private static Entity resolve(Mob host, TargetHandle handle) {
        if (!(host.level() instanceof ServerLevel level)) return null;
        if (!ShipCommandStateAdapter.handle(host).dimension().equals(handle.dimension())) return null;
        return level.getEntity(handle.uuid());
    }

    /** An attack's instantaneous body/head turn, executed alongside its movement. */
    public static void skillFacing(Mob host, net.minecraft.world.phys.Vec3 direction) {
        if (!ShipSkillAttackGate.running(host)) return;
        float yaw = (float) Math.toDegrees(Math.atan2(-direction.x, direction.z));
        host.setYRot(yaw);
        host.yBodyRot = yaw;
        host.setYHeadRot(yaw);
        com.lulan.shincolle.network.ModNetworking.sendToAllTracking(
                com.lulan.shincolle.network.S2CEntitySyncPacket.syncRotation(host), host);
    }
}
