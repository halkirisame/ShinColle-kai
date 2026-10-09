package com.lulan.shincolle.ai;

import com.lulan.shincolle.ai.command.ShipCommandStateAdapter;
import com.lulan.shincolle.ai.domain.action.ActionKind;
import com.lulan.shincolle.ai.domain.command.CommandPos;
import com.lulan.shincolle.ai.domain.movement.MovementActivity;
import com.lulan.shincolle.ai.domain.movement.MovementBody;
import com.lulan.shincolle.ai.domain.movement.MovementPoint;
import com.lulan.shincolle.ai.domain.movement.MovementReason;
import com.lulan.shincolle.ai.domain.movement.MovementStep;
import com.lulan.shincolle.ai.domain.movement.VerticalSeparation;
import com.lulan.shincolle.ai.domain.movement.VoidRescue;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.handler.ConfigHandler;
import com.lulan.shincolle.reference.Reference;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Optional;

/** Server entity-tick boundary for transient separation observations, independent of goal scheduling. */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ShipSeparationGate {
    private ShipSeparationGate() { }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        if (event.getEntity() instanceof BasicEntityShip ship && !ship.level().isClientSide()) observe(ship);
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof BasicEntityShip ship) reset(ship);
    }

    @SubscribeEvent
    public static void onUnload(EntityLeaveLevelEvent event) {
        if (event.getEntity() instanceof BasicEntityShip ship) reset(ship);
    }

    private static void reset(BasicEntityShip ship) {
        ship.shipMovementExecutor().separation = Optional.empty();
        ship.shipMovementExecutor().voidRescue = Optional.empty();
    }

    private static ServerPlayer owner(BasicEntityShip ship) {
        if (ship.getServer() == null || ship.getOwnerUUID() == null) return null;
        ServerPlayer player = ship.level().getPlayerByUUID(ship.getOwnerUUID()) instanceof ServerPlayer online ? online : null;
        return player != null && player.isAlive() && player.level() == ship.level()
                && player.level().hasChunkAt(player.blockPosition()) ? player : null;
    }

    private static void observe(BasicEntityShip ship) {
        if (!ShipMovementGate.active() || !ship.isAlive()) {
            reset(ship);
            return;
        }
        ServerPlayer owner = owner(ship);
        if (owner == null) {
            reset(ship);
            return;
        }
        ShipMovementExecutor executor = ship.shipMovementExecutor();
        var identity = ShipCommandStateAdapter.handle(owner);
        var previous = executor.separation.orElseGet(VerticalSeparation.State::empty);
        if (!previous.owner().filter(identity::equals).isPresent()) executor.voidRescue = Optional.empty();
        long now = ship.getServer().getTickCount();
        var separation = VerticalSeparation.observe(previous, identity, ship.getY() - owner.getY(), now);
        executor.separation = Optional.of(separation.state());
        if (separation.notifyOwner()) notifyOwner(ship, owner);
        var falling = VoidRescue.observe(executor.voidRescue.orElseGet(VoidRescue.State::empty), new VoidRescue.Facts(
                ConfigHandler.COMMON.voidRescueDimensions.get().contains(ship.level().dimension().location().toString()),
                ship.getY(), ship.getDeltaMovement().y, ship.onGround(), ship.isInWater(), ship.onClimbable(),
                ship.isPassenger(), ship.isNoGravity() || ship.isFallFlying(), true, owner.onGround()), now);
        executor.voidRescue = Optional.of(falling.state());
        if (falling.rescue()) {
            MovementPoint at = new MovementPoint(owner.getX(), owner.getY() + 0.75D, owner.getZ());
            if (ShipMovementExecutor.tryTeleport(ship, new MovementStep.Teleport(MovementBody.SELF, at,
                    MovementReason.VOID_RESCUE, identity.dimension()))) {
                executor.voidRescue = Optional.empty();
                executor.separation = executor.separation.map(VerticalSeparation::consume);
                ship.setDeltaMovement(Vec3.ZERO);
                ship.fallDistance = 0F;
                notifyOwner(ship, owner);
            }
        }
    }

    /** Captured before a command can stand up a seated ship. Guarding itself does not inhibit a move. */
    public static boolean commandMoveAllowed(BasicEntityShip ship) {
        return ShipMovementGate.active() && ship.isAlive() && !ship.isOrderedToSit()
                && !ShipActionGate.blocked(ship, ActionKind.MOVEMENT)
                && ShipMovementGate.allows(ship, MovementActivity.COMMANDED_MOVE);
    }

    /** Called only for an accepted block-pointer command, using that ship's final formation placement. */
    public static boolean recall(BasicEntityShip ship, ServerPlayer issuer, CommandPos position) {
        ServerPlayer owner = owner(ship);
        if (owner != issuer || !commandMoveAllowed(ship)) return false;
        var identity = ShipCommandStateAdapter.handle(owner);
        ShipMovementExecutor executor = ship.shipMovementExecutor();
        var state = VerticalSeparation.atCommand(executor.separation.orElseGet(VerticalSeparation.State::empty), identity,
                ship.getY() - owner.getY(), ship.getServer().getTickCount());
        executor.separation = Optional.of(state);
        if (!VerticalSeparation.canRecall(state, identity,
                ship.getY() - owner.getY(), ship.getServer().getTickCount())) return false;
        MovementPoint at = new MovementPoint(position.x(), position.y(), position.z());
        boolean success = ShipMovementExecutor.tryTeleport(ship, new MovementStep.Teleport(MovementBody.SELF, at,
                MovementReason.VERTICAL_RECALL, identity.dimension()));
        if (success) executor.separation = Optional.of(VerticalSeparation.consume(state));
        return success;
    }

    private static void notifyOwner(BasicEntityShip ship, ServerPlayer owner) {
        owner.displayClientMessage(Component.translatable("chat.shincolle_kai.ship.separated", ship.getDisplayName()), true);
    }
}
