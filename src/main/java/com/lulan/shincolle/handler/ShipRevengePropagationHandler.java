package com.lulan.shincolle.handler;

import com.lulan.shincolle.entity.BasicEntityShipHostile;
import com.lulan.shincolle.reference.Reference;
import com.lulan.shincolle.utility.TargetHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Reference.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ShipRevengePropagationHandler {

    private ShipRevengePropagationHandler() {
    }

    @SubscribeEvent(priority = EventPriority.NORMAL, receiveCanceled = true)
    public static void onLivingAttack(LivingAttackEvent event) {
        LivingEntity target = event.getEntity();
        Entity attacker = event.getSource().getEntity();
        Entity attackerSource = event.getSource().getDirectEntity();

        if (!target.level().isClientSide() && attackerSource != null) {
            double dist = target.distanceToSqr(attackerSource);

            if (attackerSource instanceof Player player) {
                TargetHelper.setRevengeTargetAroundPlayer(player, 32D, target);
            }

            if (target instanceof Player player) {
                if (dist < 1024D) {
                    TargetHelper.setRevengeTargetAroundPlayer(player, 32D, attackerSource);
                } else {
                    TargetHelper.setRevengeTargetAroundPlayer(player, 32D, attacker);
                }
            } else if (target instanceof BasicEntityShipHostile hostile) {
                if (attackerSource instanceof BasicEntityShipHostile) {
                    return;
                }

                if (dist < 1024D) {
                    TargetHelper.setRevengeTargetAroundHostileShip(hostile, 64D, attackerSource);
                } else {
                    TargetHelper.setRevengeTargetAroundHostileShip(hostile, 64D, attacker);
                }
            }
        }
    }
}
