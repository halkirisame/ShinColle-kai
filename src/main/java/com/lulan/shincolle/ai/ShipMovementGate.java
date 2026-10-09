package com.lulan.shincolle.ai;

import com.lulan.shincolle.ai.domain.movement.MovementInhibitReason;
import com.lulan.shincolle.ai.domain.movement.MovementPermission;
import com.lulan.shincolle.ai.domain.task.TaskMovePermissions;
import com.lulan.shincolle.ai.domain.task.TaskMoveRequest;
import java.util.EnumSet;
import com.lulan.shincolle.ai.command.ShipCommandStateAdapter;
import com.lulan.shincolle.ai.domain.command.ShipCommandState;
import com.lulan.shincolle.ai.domain.movement.CombatMove;
import com.lulan.shincolle.ai.domain.movement.MovementActivity;
import com.lulan.shincolle.ai.domain.movement.MovementComposition;
import com.lulan.shincolle.ai.domain.movement.MovementConstraint;
import com.lulan.shincolle.ai.domain.movement.MovementConstraints;
import com.lulan.shincolle.ai.domain.movement.MovementDecision;
import com.lulan.shincolle.ai.domain.movement.MovementFacts;
import com.lulan.shincolle.ai.domain.movement.MovementIntent;
import com.lulan.shincolle.ai.domain.movement.MovementIntentResolver;
import com.lulan.shincolle.ai.domain.movement.MovementPoint;
import com.lulan.shincolle.ai.domain.movement.MovementSettings;
import com.lulan.shincolle.ai.domain.movement.TeleportRule;
import com.lulan.shincolle.ai.domain.action.ActionKind;
import com.lulan.shincolle.entity.BasicEntityMount;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.entity.IShipAttackBase;
import com.lulan.shincolle.entity.IShipFlags;
import com.lulan.shincolle.entity.IShipGuardian;
import com.lulan.shincolle.handler.ConfigHandler;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.server.ServerDataManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.Optional;
import java.util.OptionalLong;

/**
 * Single entry point through which friendly-ship goals read the movement intent. It gathers
 * the facts from the host (a ship, or a mount acting for the ship it carries) and resolves
 * them on every call, so a command applied earlier in the same tick is already seen. Only the
 * NEW authority uses it.
 */
public final class ShipMovementGate {
    private ShipMovementGate() { }

    public static boolean active() {
        return ShipCommandStateAdapter.isNew();
    }

    /** The same intent and permissions the follow goal uses, with ownership checked at the boundary. */
    public static boolean recallEligible(BasicEntityShip ship, ServerPlayer owner) {
        if (!active() || !ship.isAlive() || !owner.isAlive() || ship.level() != owner.level()
                || !owner.getUUID().equals(ship.getOwnerUUID()) || ShipActionGate.blocked(ship, ActionKind.MOVEMENT)) {
            return false;
        }
        MovementDecision decision = decision(ship);
        return decision != null && decision.intent() instanceof MovementIntent.FollowOwner
                && decision.allows(MovementActivity.COMMANDED_MOVE);
    }

    static OptionalLong followRecallRemaining(Entity host, LivingEntity owner) {
        if (!active() || !(host instanceof ShipMovementHost movementHost)) return OptionalLong.empty();
        BasicEntityShip ship = host instanceof BasicEntityShip s ? s
                : host instanceof BasicEntityMount mount ? mount.getHost() : null;
        ShipMovementExecutor executor = movementHost.shipMovementExecutor();
        if (ship == null || owner == null || !owner.getUUID().equals(ship.getOwnerUUID())) {
            executor.clearFollowRecall();
            return OptionalLong.empty();
        }
        if (!owner.isAlive() || owner.level() != host.level()) return OptionalLong.empty();
        return executor.followRecallRemaining(ShipCommandStateAdapter.handle(owner),
                host.getServer().getTickCount());
    }

    /** The decision for this host, or null when the host carries no ship. */
    public static MovementDecision decision(Entity host) {
        BasicEntityShip ship = host instanceof BasicEntityShip s ? s
                : host instanceof BasicEntityMount mount ? mount.getHost() : null;
        if (ship == null || !(host instanceof IShipAttackBase base)) return null;
        return MovementIntentResolver.resolve(facts(host, base, ship));
    }

    public static MovementIntent intent(Entity host) {
        MovementDecision decision = decision(host);
        return decision == null ? null : decision.intent();
    }

    public static boolean allows(Entity host, MovementActivity activity) {
        MovementDecision decision = decision(host);
        return decision != null && decision.allows(activity);
    }

    /**
     * How an attack goal may move toward its target this tick, within the region the intent
     * allows. A host that carries no ship, such as a hostile ship, has no intent and so no region.
     */
    public static CombatMove combatMove(Entity host, Entity target, boolean holdForFire) {
        CombatMove.Request request = new CombatMove.Request(point(host), point(target), holdForFire);
        BasicEntityShip ship = host instanceof BasicEntityShip s ? s
                : host instanceof BasicEntityMount mount ? mount.getHost() : null;
        if (ship == null || !(host instanceof IShipAttackBase base)) {
            return MovementComposition.compose(new MovementConstraint.Unconstrained(), request);
        }
        return MovementComposition.compose(region(host, base, ship), request);
    }

    /**
     * Whether a working ship may walk for its work now, and so may take the walk {@code request} asks for.
     * A host that carries no ship may not.
     */
    public static MovementPermission taskMove(Entity host, TaskMoveRequest request) {
        BasicEntityShip ship = host instanceof BasicEntityShip s ? s : null;
        if (ship == null) {
            return MovementPermission.inhibited(EnumSet.of(MovementInhibitReason.RIDING));
        }
        return TaskMovePermissions.of(facts(host, ship, ship), region(host, ship, ship), request);
    }

    /** The region the host's current intent lets it move in. */
    public static boolean skillDestination(Entity host, net.minecraft.world.phys.Vec3 destination) {
        BasicEntityShip ship = host instanceof BasicEntityShip s ? s : null;
        MovementConstraint constraint = ship == null ? new MovementConstraint.Unconstrained() : region(host, ship, ship);
        return com.lulan.shincolle.ai.domain.combat.skill.SkillMovementPermission.allows(constraint,
                new MovementPoint(destination.x, destination.y, destination.z));
    }

    /** The region the host's current intent lets it move in. */
    private static MovementConstraint region(Entity host, IShipAttackBase base, BasicEntityShip ship) {
        MovementIntent intent = MovementIntentResolver.resolve(facts(host, base, ship)).intent();
        LivingEntity owner = resolveOwner(ship);
        Optional<MovementPoint> ownerPosition = owner != null && owner.isAlive() && owner.level() == host.level()
                ? Optional.of(point(owner)) : Optional.empty();
        Entity guarded = host instanceof IShipGuardian guardian ? guardian.getGuardedEntity() : null;
        Optional<MovementPoint> guardedPosition = guarded != null && guarded.isAlive()
                && guarded.level() == host.level() ? Optional.of(point(guarded)) : Optional.empty();
        MovementSettings settings = settings(base);
        MovementConstraints.Facts facts = new MovementConstraints.Facts(
                settings.formation(), ownerPosition, guardedPosition,
                settings.followMax(), host.getBbWidth(), settings.pickItem());
        return MovementConstraints.of(intent, facts);
    }

    /**
     * The movement settings as {@code source} holds them now. This is the one place they are read for the
     * movement decisions: a mount answers for the ship it carries, and a missing source has none.
     */
    public static MovementSettings settings(IShipFlags source) {
        if (source == null) return MovementSettings.NONE;
        boolean formation = com.lulan.shincolle.ai.command.ShipCommandStateAdapter.isNew()
                ? ShipFormationStateAdapter.active(source).isPresent() : source.getStateMinor(ID.M.FormatType) > 0;
        return new MovementSettings(formation,
                source.getStateMinor(ID.M.FollowMin), source.getStateMinor(ID.M.FollowMax),
                source.getStateFlag(ID.F.PickItem));
    }

    /**
     * Whether the host is held at a crane. This is the one place the ship AI reads the crane state; a mount
     * answers for the ship it carries.
     */
    public static boolean craneBusy(IShipFlags host) {
        return craneState(host) > 0;
    }

    /** The crane state as stored, for diagnostics. */
    public static int craneState(IShipFlags host) {
        return host.getStateMinor(ID.M.CraneState);
    }

    /** The teleport settings following and guarding read on every goal tick. */
    static TeleportRule teleportRule() {
        return new TeleportRule(ConfigHandler.canTeleport(), ConfigHandler.shipTeleport[0],
                ConfigHandler.shipTeleport[1]);
    }

    static MovementPoint point(Entity entity) {
        return new MovementPoint(entity.getX(), entity.getY(), entity.getZ());
    }

    private static MovementFacts facts(Entity host, IShipAttackBase base, BasicEntityShip ship) {
        ShipCommandState command = ship.getCommandState();
        LivingEntity owner = resolveOwner(ship);
        boolean ownerPresent = owner != null && owner.isAlive() && owner.level() == ship.level();
        double ownerDistanceSq = ownerPresent ? ship.distanceToSqr(owner) : 0D;
        return new MovementFacts(
                command.movement(),
                ship.isOrderedToSit(),
                base.getIsSitting(),
                host instanceof BasicEntityMount,
                ship.getHealth() / ship.getMaxHealth(),
                ship.getStateMinor(ID.M.FleeHP) * 0.01F,
                ownerPresent,
                ownerDistanceSq,
                base.getStateMinor(ID.M.NumGrudge) > 0,
                base.getIsRiding(),
                host.getVehicle() instanceof BasicEntityShip,
                base.getIsLeashed(),
                craneBusy(base),
                ship.fishHook != null,
                settings(ship).pickItem(),
                engaged(host));
    }

    /**
     * Whether the host is engaged with a locked target. The combat gate reads only the target lock and
     * the action constraints, never this gate, so building the facts here cannot re-enter it.
     */
    private static boolean engaged(Entity host) {
        return ShipCombatGate.active(host) && ShipCombatGate.engagement(host).engaged();
    }

    /** The flee goal's owner lookup: the online player first, then the stored owner. */
    private static LivingEntity resolveOwner(BasicEntityShip ship) {
        int uid = ship.getPlayerUID();
        if (uid > 0 && !ship.level().isClientSide()) {
            ServerPlayer serverPlayer = ServerDataManager.getPlayerByUID(uid);
            if (serverPlayer != null) {
                return serverPlayer;
            }
        }
        return ship.getOwner();
    }
}
