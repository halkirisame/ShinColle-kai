package com.lulan.shincolle.entity;

import com.lulan.shincolle.handler.ConfigHandler;
import com.lulan.shincolle.network.ModNetworking;
import com.lulan.shincolle.network.ParticlePacketRoute;
import com.lulan.shincolle.network.S2CSpawnParticlePacket;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Values;
import com.lulan.shincolle.utility.TeamHelper;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.PacketDistributor;

/** Server-owned periodic healing and cargo supply abilities. */
public final class ShipSupportAbilities {
    private ShipSupportAbilities() {}

    public enum HealingProfile {
        AIRFIELD(128, 12D, 0.06F, 15, 2, 0.96F, 1F, 0.04F, 0.04F, 0.1F, 50),
        HARBOUR(128, 12D, 0.06F, 50, 1, 0.94F, 4F, 0.06F, 0.06F, 0.2F, 50),
        NORTHERN(64, 8D, 0.03F, 25, 1, 0.98F, 1F, 0.02F, 0.02F, 0.1F, 25);

        private final int interval;
        private final double range;
        private final float selfRatio;
        private final int levelsPerTarget;
        private final int baseTargets;
        private final float healthThreshold;
        private final float baseHeal;
        private final float targetRatio;
        private final float playerLevelRatio;
        private final float shipLevelRatio;
        private final int grudgeCost;

        HealingProfile(int interval, double range, float selfRatio, int levelsPerTarget, int baseTargets,
                       float healthThreshold, float baseHeal, float targetRatio, float playerLevelRatio,
                       float shipLevelRatio, int grudgeCost) {
            this.interval = interval;
            this.range = range;
            this.selfRatio = selfRatio;
            this.levelsPerTarget = levelsPerTarget;
            this.baseTargets = baseTargets;
            this.healthThreshold = healthThreshold;
            this.baseHeal = baseHeal;
            this.targetRatio = targetRatio;
            this.playerLevelRatio = playerLevelRatio;
            this.shipLevelRatio = shipLevelRatio;
            this.grudgeCost = grudgeCost;
        }
    }

    public static void tickHealing(BasicEntityShip ship, HealingProfile profile) {
        if (ship.level().isClientSide() || !ship.isAlive() || ship.tickCount % profile.interval != 0) {
            return;
        }
        if (ship.getGrudge() > 0 && ship.getHealth() < ship.getMaxHealth()) {
            ship.heal(ship.getMaxHealth() * profile.selfRatio + 1F);
        }
        if (!ship.getStateFlag(ID.F.IsMarried) || !ship.getStateFlag(ID.F.UseRingEffect)
                || ship.getGrudge() <= profile.grudgeCost) {
            return;
        }
        int remaining = ship.getLevel() / profile.levelsPerTarget + profile.baseTargets;
        for (LivingEntity target : ship.level().getEntitiesOfClass(LivingEntity.class,
                ship.getBoundingBox().inflate(profile.range))) {
            if (remaining <= 0 || ship.getGrudge() < profile.grudgeCost) {
                break;
            }
            if (target == ship || !target.isAlive() || !(target instanceof Player || target instanceof BasicEntityShip)
                    || !TeamHelper.checkIsAlly(ship, target)
                    || target.getHealth() / target.getMaxHealth() >= profile.healthThreshold) {
                continue;
            }
            float levelRatio = target instanceof Player ? profile.playerLevelRatio : profile.shipLevelRatio;
            target.heal(profile.baseHeal + target.getMaxHealth() * profile.targetRatio + ship.getLevel() * levelRatio);
            ship.decrGrudgeNum(profile.grudgeCost);
            showSupportLine(ship, target);
            remaining--;
        }
    }

    public static void tickTransport(BasicEntityShip ship) {
        if (ship.level().isClientSide() || !ship.isAlive() || ship.tickCount % 128 != 0) {
            return;
        }
        if (ship.getGrudge() <= 5400) {
            replenish(ship, 4, 5, Values.N.BaseGrudge, ID.Attrs.GRUDGE, ID.M.NumGrudge);
        }
        if (ship.getStateMinor(ID.M.NumAmmoLight) <= 540) {
            replenish(ship, 0, 2, Values.N.BaseLightAmmo, ID.Attrs.AMMO, ID.M.NumAmmoLight);
        }
        if (ship.getStateMinor(ID.M.NumAmmoHeavy) <= 270) {
            replenish(ship, 1, 3, Values.N.BaseHeavyAmmo, ID.Attrs.AMMO, ID.M.NumAmmoHeavy);
        }
        if (ship.tickCount % 256 != 0 || ship.getStateFlag(ID.F.NoFuel)) {
            return;
        }
        int remaining = ship.getLevel() / 50 + 1;
        double range = 2D + ship.getAttrs().getAttackRange() * 0.5D;
        for (BasicEntityShip target : ship.level().getEntitiesOfClass(BasicEntityShip.class,
                ship.getBoundingBox().inflate(range))) {
            if (remaining <= 0) {
                break;
            }
            if (target == ship || !target.isAlive() || !TeamHelper.checkSameOwner(ship, target)) {
                continue;
            }
            boolean supplied = false;
            if (ship.getGrudge() > 5400 && target.getGrudge() < 2700) {
                ship.addGrudge(-5400);
                target.addGrudge((int) (5400F * target.getAttrs().getAttrsBuffed(ID.Attrs.GRUDGE)));
                supplied = true;
            }
            if (ship.getStateMinor(ID.M.NumAmmoLight) >= 540 && target.getStateMinor(ID.M.NumAmmoLight) < 270) {
                ship.addAmmoLight(-540);
                target.addAmmoLight((int) (540F * target.getAttrs().getAttrsBuffed(ID.Attrs.AMMO)));
                supplied = true;
            }
            if (ship.getStateMinor(ID.M.NumAmmoHeavy) >= 270 && target.getStateMinor(ID.M.NumAmmoHeavy) < 135) {
                ship.addAmmoHeavy(-270);
                target.addAmmoHeavy((int) (270F * target.getAttrs().getAttrsBuffed(ID.Attrs.AMMO)));
                supplied = true;
            }
            if (supplied) {
                ship.addShipExp(ConfigHandler.expGain[6] * 20);
                showSupportLine(ship, target);
                remaining--;
            }
        }
    }

    private static void replenish(BasicEntityShip ship, int item, int container, int baseAmount,
                                  int attribute, int resource) {
        int amount;
        if (ship.decrSupplies(item)) {
            amount = baseAmount;
        } else if (ship.decrSupplies(container)) {
            amount = baseAmount * 9;
        } else {
            return;
        }
        float multiplier = ship.getAttrs().getAttrsBuffed(attribute);
        int added = (int) (amount * multiplier * (ConfigHandler.easyMode() ? 10F : 1F));
        switch (resource) {
            case ID.M.NumGrudge -> ship.addGrudge(added);
            case ID.M.NumAmmoLight -> ship.addAmmoLight(added);
            case ID.M.NumAmmoHeavy -> ship.addAmmoHeavy(added);
            default -> throw new IllegalArgumentException("Unsupported supply resource");
        }
    }

    private static void showSupportLine(BasicEntityShip ship, LivingEntity target) {
        ModNetworking.sendToNear(S2CSpawnParticlePacket.targetParameters(ParticlePacketRoute.SUPPORT_LINE_TYPE,
                ship.getId(), target.getId(), 0D, 0D, 0D),
                new PacketDistributor.TargetPoint(ship.getX(), ship.getY(), ship.getZ(), 64D, ship.level().dimension()));
    }
}
