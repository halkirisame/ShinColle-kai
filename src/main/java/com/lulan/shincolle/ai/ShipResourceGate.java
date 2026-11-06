package com.lulan.shincolle.ai;

import com.lulan.shincolle.ai.domain.task.ShipResourceRules;
import com.lulan.shincolle.capability.CapaShipInventory;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.handler.ConfigHandler;
import com.lulan.shincolle.init.ModItems;
import com.lulan.shincolle.network.ModNetworking;
import com.lulan.shincolle.network.ParticlePacketRoute;
import com.lulan.shincolle.network.S2CSpawnParticlePacket;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.utility.LogHelper;
import com.lulan.shincolle.utility.ShipFluidTransfer;
import com.lulan.shincolle.utility.TaskHelper;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.PacketDistributor;

/** Server resource boundary; executors never grant their own resource rewards. */
public final class ShipResourceGate {
    private ShipResourceGate() { }

    public record TickResult(ShipResourceRules.Decision decision, ShipFluidTransfer.Result liquid,
                             TaskHelper.PumpXpResult experience) { }

    public static void pickedUp(BasicEntityShip ship, ShipResourceRules.PickupResult result) {
        if (ship.level().isClientSide()) return;
        int reward = ShipResourceRules.pickupReward(result, ConfigHandler.expGain[6]);
        if (reward != 0) ship.addShipExp(reward);
    }

    public static ShipResourceRules.Decision permission(BasicEntityShip ship) {
        CapaShipInventory inventory = ship.getCapaShipInventory();
        boolean drum = false;
        for (int slot = 0; slot <= 6 && slot < inventory.getSlots(); slot++) {
            ItemStack item = inventory.getStackInSlot(slot);
            if (item.is(ModItems.EQUIP_DRUM.get()) && item.hasTag() && item.getTag().getInt("EquipMeta") == 1) {
                drum = true;
                break;
            }
        }
        // With no player-control fact, these are precisely the existing shared death/fuel constraints.
        var actionReasons = ShipActionGate.constraints(ship, false).movement().reasons();
        return ShipResourceRules.decide(new ShipResourceRules.PumpFacts(
                ship.getStateFlag(ID.F.AutoPump), ship.isOrderedToSit(), BasicEntityShip.stopAI,
                actionReasons, ship.getShipType() == ID.ShipType.TRANSPORT, ship.getStateFlag(ID.F.IsMarried),
                drum, ship.getLevel(), ship.tickCount));
    }

    public static TickResult tickPump(BasicEntityShip ship) {
        var decision = permission(ship);
        var liquid = ShipFluidTransfer.Result.noOp();
        var experience = TaskHelper.PumpXpResult.NO_OP;
        if (ship.level().isClientSide() || !decision.allowed()) return new TickResult(decision, liquid, experience);
        if (decision.liquidDue()) {
            liquid = ShipFluidTransfer.pump(ship);
            if (liquid.status() == ShipFluidTransfer.Status.COMMITTED) {
                int reward = ShipResourceRules.liquidReward(true, ship.getShipType() == ID.ShipType.TRANSPORT,
                        ConfigHandler.expGain[6]);
                if (reward != 0) ship.addShipExp(reward);
                ModNetworking.sendToNear(S2CSpawnParticlePacket.entityParameters(
                        ParticlePacketRoute.FLUID_FILL_TYPE, ship.getId(), 0D, 0.1D, 0D),
                        new PacketDistributor.TargetPoint(ship.getX(), ship.getY(), ship.getZ(), 48D,
                                ship.level().dimension()));
                if (liquid.finiteRemoval() && ship.getRandom().nextInt(3) == 0) {
                    ship.playSound(SoundEvents.BUCKET_FILL, 0.5F, ship.getRandom().nextFloat() * 0.4F + 0.8F);
                }
            } else if (liquid.status() == ShipFluidTransfer.Status.EXTERNAL_CONTRACT_VIOLATION
                    && (ship.tickCount & 255) == 0) {
                LogHelper.diag("DIAG: pump_contract ship=" + ship.getId() + " pos=" + liquid.position()
                        + " slot=" + liquid.slot() + " expected=" + liquid.expected() + " actual=" + liquid.actual()
                        + " reason=" + liquid.reason());
            }
        }
        if (decision.xpDue()) experience = TaskHelper.pumpExperience(ship);
        return new TickResult(decision, liquid, experience);
    }
}
