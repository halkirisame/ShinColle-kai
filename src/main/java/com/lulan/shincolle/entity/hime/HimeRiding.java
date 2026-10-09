package com.lulan.shincolle.entity.hime;

import com.lulan.shincolle.ai.command.ShipCommandStateAdapter;
import com.lulan.shincolle.ai.domain.command.MovementOrder;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;
import com.lulan.shincolle.utility.TeamHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/** Shared riding behavior for the two small hime. */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class HimeRiding {
    private final BasicEntityShip ship;
    private LivingEntity rideTarget;
    private int rideTicks;

    public HimeRiding(BasicEntityShip ship) {
        this.ship = ship;
    }

    public static boolean canRideOwner(BasicEntityShip ship, Player player, InteractionHand hand) {
        return !ship.level().isClientSide() && ship.isAlive() && hand == InteractionHand.MAIN_HAND
                && !player.isShiftKeyDown()
                && TeamHelper.checkSameOwner(ship, player)
                && (ShipCommandStateAdapter.isNew() ? ship.getCommandState().sitting() : ship.isOrderedToSit())
                && ship.getStateEmotion(ID.S.Emotion) == ID.Emotion.BORED
                && player.getItemInHand(InteractionHand.MAIN_HAND).isEmpty()
                && player.getPassengers().isEmpty() && !ship.isPassenger();
    }

    public void tick() {
        if (ship.level().isClientSide() || !ship.isAlive()) return;

        Entity vehicle = ship.getVehicle();
        if ((vehicle instanceof Player || vehicle instanceof BasicEntityShip) && vehicle.isShiftKeyDown()) {
            dismount();
        }

        if (ship.isOrderedToSit() || ship.isLeashed() || ship.getStateFlag(ID.F.NoFuel)) {
            cancel();
            return;
        }
        if (hasMovementOrder()) {
            cancel();
            if (!ship.isPassenger()) return;
        }

        if (ship.tickCount % 256 == 0 && ship.getRandom().nextInt(3) == 0) {
            cancel();
            if (ship.isPassenger()) {
                if (ship.getRandom().nextInt(2) == 0) dismount();
            } else if (!hasMovementOrder()) {
                List<LivingEntity> candidates = ship.level().getEntitiesOfClass(LivingEntity.class,
                        ship.getBoundingBox().inflate(6D, 4D, 6D), this::eligible);
                if (!candidates.isEmpty()) {
                    rideTarget = candidates.get(ship.getRandom().nextInt(candidates.size()));
                    rideTicks = 0;
                }
            }
        }

        if (rideTarget != null) {
            if (++rideTicks > 200 || !eligible(rideTarget) || ship.isPassenger()
                    || !ship.getPassengers().isEmpty()) {
                cancel();
            } else if (ship.distanceToSqr(rideTarget) <= 4D) {
                if (ship.startRiding(rideTarget, true)) {
                    ship.getNavigation().stop();
                    ship.sendSyncPacketRiders();
                }
                cancel();
            } else if (ship.tickCount % 32 == 0) {
                ship.getNavigation().moveTo(rideTarget, 1D);
            }
        }
    }

    private boolean hasMovementOrder() {
        if (ShipCommandStateAdapter.isNew()) {
            return !(ship.getCommandState().movement() instanceof MovementOrder.Follow);
        }
        return !ship.getStateFlag(ID.F.CanFollow);
    }

    private boolean eligible(LivingEntity target) {
        return target != ship && target.isAlive() && !target.isRemoved()
                && target.level() == ship.level()
                && (target instanceof BasicEntityShip || target instanceof Player)
                && !(target instanceof Player player && player.isSpectator())
                && !target.isPassenger() && target.getPassengers().isEmpty()
                && TeamHelper.checkSameOwner(ship, target);
    }

    public void cancel() {
        if (rideTarget != null) {
            rideTarget = null;
            rideTicks = 0;
            ship.getNavigation().stop();
        }
    }

    public void dismount() {
        if (ship.getVehicle() instanceof Player || ship.getVehicle() instanceof BasicEntityShip) {
            ship.stopRiding();
            ship.sendSyncPacketRiders();
        }
        cancel();
    }

    public void beforeSave() {
        if (!ship.level().isClientSide() && ship.getVehicle() instanceof Player) dismount();
    }

    /** Detach and sync before vanilla unRide ejects the player's passengers on logout. */
    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity().level().isClientSide()) return;
        for (Entity passenger : List.copyOf(event.getEntity().getPassengers())) {
            if (passenger instanceof EntityNorthernHime || passenger instanceof EntitySSNH) {
                passenger.stopRiding();
                ((BasicEntityShip) passenger).sendSyncPacketRiders();
            }
        }
    }
}
