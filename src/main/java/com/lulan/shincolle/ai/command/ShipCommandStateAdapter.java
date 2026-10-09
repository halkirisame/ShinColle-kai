package com.lulan.shincolle.ai.command;

import com.lulan.shincolle.ai.ShipMovementExecutor;
import com.lulan.shincolle.ai.domain.DimensionKey;
import com.lulan.shincolle.ai.domain.TargetHandle;
import com.lulan.shincolle.ai.domain.command.CommandIssuer;
import com.lulan.shincolle.ai.domain.command.CommandStateChange;
import com.lulan.shincolle.ai.domain.command.CommandStateOp;
import com.lulan.shincolle.ai.domain.command.CommandStateReducer;
import com.lulan.shincolle.ai.domain.command.LegacyCommandCodec;
import com.lulan.shincolle.ai.domain.command.LegacyCommandFields;
import com.lulan.shincolle.ai.domain.command.MovementOrder;
import com.lulan.shincolle.ai.domain.command.ShipCommand;
import com.lulan.shincolle.ai.domain.command.ShipCommandState;
import com.lulan.shincolle.ai.domain.command.ShipTransitionReason;
import com.lulan.shincolle.ai.domain.movement.LookReason;
import com.lulan.shincolle.ai.domain.movement.LookRequest;
import com.lulan.shincolle.ai.domain.movement.MovementBody;
import com.lulan.shincolle.ai.domain.movement.MovementPlan;
import com.lulan.shincolle.ai.domain.movement.MovementPoint;
import com.lulan.shincolle.ai.domain.movement.MovementReason;
import com.lulan.shincolle.ai.domain.movement.MovementStep;
import com.lulan.shincolle.ai.domain.movement.MovementTarget;
import com.lulan.shincolle.ai.observation.MinecraftTargetResolver;
import com.lulan.shincolle.entity.BasicEntityMount;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.handler.ConfigHandler;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.utility.FormationHelper;
import com.lulan.shincolle.utility.LogHelper;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

import java.util.Objects;
import java.util.Optional;

/** Server-only command authority; the existing ship fields remain the save and Goal projection. */
public final class ShipCommandStateAdapter {
    private final BasicEntityShip ship;
    private boolean checkProjection;
    private ShipCommandState state;
    private CommandStateChange lastChange;
    private ConfigHandler.ShipAiTargetAuthority previousAuthority;
    private long sequence;

    public ShipCommandStateAdapter(BasicEntityShip ship) {
        this.ship = ship;
    }

    public static boolean isNew() {
        return ConfigHandler.shipAiTargetAuthority() == ConfigHandler.ShipAiTargetAuthority.NEW;
    }

    public void enableProjectionCheckForTest() {
        checkProjection = true;
    }

    public void onLoad() {
        state = null;
        lastChange = null;
        previousAuthority = null;
        sequence = 0;
    }

    public void observeAuthority() {
        if (!(ship.level() instanceof ServerLevel)) return;
        ConfigHandler.ShipAiTargetAuthority current = ConfigHandler.shipAiTargetAuthority();
        if (current != previousAuthority) {
            ConfigHandler.ShipAiTargetAuthority previous = previousAuthority;
            previousAuthority = current;
            if (current == ConfigHandler.ShipAiTargetAuthority.NEW) {
                boolean transition = previous == ConfigHandler.ShipAiTargetAuthority.LEGACY;
                LegacyCommandFields before = fields();
                state = LegacyCommandCodec.decode(before, dimension(ship.level().dimension()));
                if (transition && ship.getManualTarget() != null && ship.getManualTarget().isAlive()
                        && !ship.getManualTarget().isRemoved() && ship.getManualTarget().level() == ship.level()) {
                    state = new ShipCommandState(state.movement(), state.sitting(),
                            Optional.of(handle(ship.getManualTarget())));
                }
                endKnownLostGuard();
                project(null, true);
                LegacyCommandFields after = LegacyCommandCodec.encode(state);
                if (before.canFollow() != after.canFollow() || before.guardType() != after.guardType()
                        || before.guardX() != after.guardX() || before.guardY() != after.guardY()
                        || before.guardZ() != after.guardZ()) {
                    ship.sendSyncPacketGuard();
                }
            } else {
                state = null;
                lastChange = null;
            }
        }
    }

    public ShipCommandState state() {
        observeAuthority();
        return state;
    }

    public CommandStateChange lastChange() {
        observeAuthority();
        return lastChange;
    }

    public void apply(CommandIssuer issuer, CommandStateOp op) {
        apply(issuer, op, -1);
    }

    public void apply(CommandIssuer issuer, CommandStateOp op, long serverSequence) {
        observeAuthority();
        if (state == null) throw new IllegalStateException("No server command authority");
        ShipCommandState before = state;
        state = CommandStateReducer.apply(state, op);
        if (serverSequence < 0) serverSequence = sequence++;
        else sequence = Math.max(sequence, serverSequence + 1);
        lastChange = new CommandStateChange(issuer, ship.level().getGameTime(), serverSequence, before, state);
        boolean movementChanged = !Objects.equals(before.movement(), state.movement());
        if (movementChanged) endKnownLostGuard();
        project(op, movementChanged);
        if (movementChanged) ship.sendSyncPacketGuard();
        assertProjection();
    }

    private void endKnownLostGuard() {
        Entity guarded = ship.getGuardedEntity();
        if (!(state.movement() instanceof MovementOrder.GuardEntity order) || guarded == null
                || !order.target().uuid().equals(guarded.getUUID())
                || guarded.isAlive() && !guarded.isRemoved()) return;
        ShipCommandState before = state;
        state = CommandStateReducer.apply(state, new CommandStateOp.EndMovement());
        lastChange = new CommandStateChange(new CommandIssuer.Ship(ShipTransitionReason.GUARD_TARGET_LOST),
                ship.level().getGameTime(), sequence++, before, state);
    }

    private void project(CommandStateOp op, boolean projectMovement) {
        LegacyCommandFields encoded = LegacyCommandCodec.encode(state);
        MovementOrder movement = state.movement();
        boolean moved = op instanceof CommandStateOp.Apply commandOp
                && (commandOp.command() instanceof ShipCommand.Move
                || commandOp.command() instanceof ShipCommand.GuardPosition
                || commandOp.command() instanceof ShipCommand.GuardEntity);
        if (moved && movement instanceof MovementOrder.MoveTo move) {
            if (projectMovement) applyGuard(move.position().x(), move.position().y(), move.position().z(), 0);
            else reapplyPositionEffects(move.position().x(), move.position().y(), move.position().z());
        } else if (moved && movement instanceof MovementOrder.GuardPosition guard) {
            if (projectMovement) applyGuard(guard.position().x(), guard.position().y(), guard.position().z(), 1);
            else reapplyPositionEffects(guard.position().x(), guard.position().y(), guard.position().z());
        } else if (moved && projectMovement && movement instanceof MovementOrder.GuardEntity guard) {
            Entity target = resolve(guard.target());
            if (target != null) FormationHelper.applyShipGuardEntity(ship, target);
        }

        if (projectMovement && movement instanceof MovementOrder.GuardEntity guard) {
            ship.setGuardedPos(-1, -1, -1, levelKey(guard.target().dimension()), 2);
            Entity target = resolve(guard.target());
            ship.setGuardedEntity(target);
            if (target == null) ship.projectGuardIdentity(guard.target().uuid(),
                    levelKey(guard.target().dimension()));
        } else if (projectMovement && movement instanceof MovementOrder.Follow) {
            ship.setGuardedPos(-1, -1, -1, 0, 0);
            ship.setGuardedEntity(null);
        } else if (projectMovement) {
            ship.setGuardedEntity(null);
            ship.setGuardedPos(encoded.guardX(), encoded.guardY(), encoded.guardZ(),
                    levelKey(encoded.guardedDimension()), encoded.guardType());
        }
        if (projectMovement) {
            ship.setStateFlag(ID.F.CanFollow, encoded.canFollow());
            ship.setReleaseGuardOnArrival(encoded.releaseOnArrival());
        }
        if (ship.isOrderedToSit() != state.sitting()
                || op instanceof CommandStateOp.Apply commandOp
                && commandOp.command() instanceof ShipCommand.SetSitting) {
            ship.setEntitySit(state.sitting());
        }
        Entity manual = state.manualAttack().map(this::resolve).orElse(null);
        if (state.manualAttack().isPresent() && manual == null) {
            ShipCommandState before = state;
            state = CommandStateReducer.apply(state, new CommandStateOp.ClearManualAttack());
            lastChange = new CommandStateChange(new CommandIssuer.Ship(ShipTransitionReason.ATTACK_TARGET_INVALID),
                    ship.level().getGameTime(), sequence++, before, state);
        }
        ship.setManualTarget(manual);
        if (op instanceof CommandStateOp.Apply commandOp) {
            ShipCommand command = commandOp.command();
            if (command instanceof ShipCommand.Attack || command instanceof ShipCommand.CancelAttack) {
                ship.setEntityTarget(manual);
                ship.applyEmotesReaction(5);
            }
        }
    }

    /** A waypoint traversal only moves the guard point on: it leaves the fight, the mood and the gaze alone. */
    private boolean byTraversal() {
        return lastChange != null && lastChange.issuer() instanceof CommandIssuer.Waypoint;
    }

    /** What the guard fields and the first steps toward the point are, as the command-applying helper set them. */
    private void applyGuard(int x, int y, int z, int guardType) {
        if (!byTraversal()) {
            ship.setTarget(null);
            ship.setEntityTarget(null);
            // A new order ends the stay counted so far; coming back to the point counts from 0.
            ship.setWpStayAt(null);
        }
        ship.setEntitySit(false);
        ship.setGuardedEntity(null);
        ship.setGuardedPos(x, y, z, ship.level().dimension(), guardType);
        ship.setStateFlag(ID.F.CanFollow, false);
        walkToward(x, y, z);
    }

    private void reapplyPositionEffects(int x, int y, int z) {
        if (!byTraversal()) {
            ship.setTarget(null);
            ship.setEntityTarget(null);
        }
        walkToward(x, y, z);
    }

    /** The path and the look toward a point just set, from the body the ship moves with; nothing without fuel. */
    private void walkToward(int x, int y, int z) {
        if (ship.getStateFlag(ID.F.NoFuel)) return;
        boolean waypoint = byTraversal();
        MovementBody body = ship.getVehicle() instanceof BasicEntityMount ? MovementBody.VEHICLE : MovementBody.SELF;
        if (waypoint) {
            // A route ends at the middle of the block, as the arrival check measures it.
            ShipMovementExecutor.run(ship, MovementPlan.of(new MovementStep.PathTo(body,
                    new MovementTarget.Point(new MovementPoint(x + 0.5D, y, z + 0.5D)), 1D,
                    MovementReason.WAYPOINT_ADVANCED)));
            return;
        }
        ship.applyEmotesReaction(5);
        MovementPoint point = new MovementPoint(x, y, z);
        ShipMovementExecutor.run(ship, MovementPlan.of(new MovementStep.PathTo(body, new MovementTarget.Point(point),
                1D, MovementReason.COMMAND_APPLIED)));
        ShipMovementExecutor.look(ship, body, new LookRequest(new MovementTarget.Point(point), 30F, 40F,
                LookReason.COMMAND_APPLIED));
    }

    public void assertProjection() {
        if (!checkProjection || state == null || !isNew()) return;
        LegacyCommandFields expected = LegacyCommandCodec.encode(state);
        LegacyCommandFields actual = fields();
        boolean equal = expected.guardX() == actual.guardX() && expected.guardY() == actual.guardY()
                && expected.guardZ() == actual.guardZ() && expected.guardDim() == actual.guardDim()
                && expected.guardType() == actual.guardType()
                && Objects.equals(expected.guardedDimension(), actual.guardedDimension())
                && Objects.equals(expected.guardedEntityUuid(), actual.guardedEntityUuid())
                && expected.canFollow() == actual.canFollow()
                && expected.releaseOnArrival() == actual.releaseOnArrival()
                && expected.orderedToSit() == actual.orderedToSit()
                && Objects.equals(state.manualAttack(), Optional.ofNullable(ship.getManualTarget())
                        .map(ShipCommandStateAdapter::handle));
        if (!equal) {
            LogHelper.diag("DIAG: command projection mismatch ship=" + ship + " expected=" + expected
                    + " actual=" + actual + " manual=" + ship.getManualTarget() + " last=" + lastChange);
            throw new AssertionError("Ship command projection mismatch: " + ship);
        }
    }

    private LegacyCommandFields fields() {
        ResourceKey<Level> guarded = ship.getGuardedDimension();
        return new LegacyCommandFields(ship.getGuardedPos(0), ship.getGuardedPos(1),
                ship.getGuardedPos(2), ship.getGuardedPos(3), ship.getGuardedPos(4),
                guarded == null ? null : dimension(guarded), ship.getGuardedEntityUuid(),
                ship.getStateFlag(ID.F.CanFollow), ship.shouldReleaseGuardOnArrival(),
                ship.isOrderedToSit(), ship.getStateMinor(ID.M.GuardID));
    }

    private Entity resolve(TargetHandle target) {
        return ship.level() instanceof ServerLevel server ? new MinecraftTargetResolver(server).resolve(target).orElse(null)
                : null;
    }

    public static TargetHandle handle(Entity target) {
        return new TargetHandle(target.getUUID(), dimension(target.level().dimension()));
    }

    private static DimensionKey dimension(ResourceKey<Level> key) {
        return dimension(key.location());
    }

    private static DimensionKey dimension(ResourceLocation id) {
        return new DimensionKey(id.getNamespace(), id.getPath());
    }

    private static ResourceKey<Level> levelKey(DimensionKey key) {
        return ResourceKey.create(Registries.DIMENSION, new ResourceLocation(key.namespace(), key.path()));
    }
}
