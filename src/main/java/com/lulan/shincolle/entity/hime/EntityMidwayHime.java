package com.lulan.shincolle.entity.hime;

import com.lulan.shincolle.ai.ShipCarrierAttackGoal;
import com.lulan.shincolle.ai.ShipRangeAttackGoal;
import com.lulan.shincolle.entity.BasicEntityShipCV;
import com.lulan.shincolle.entity.BasicEntityMount;
import com.lulan.shincolle.entity.IShipRiderType;
import com.lulan.shincolle.entity.mounts.EntityMountMiH;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.handler.ConfigHandler;
import com.lulan.shincolle.reference.ID;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * Midway Hime entity - powerful CV type with highest self-healing.
 * Provides ABSORPTION to both nearby allied ships and owner player.
 */
public class EntityMidwayHime extends BasicEntityShipCV implements IShipRiderType {

    private int riderType;

    public EntityMidwayHime(EntityType<? extends EntityMidwayHime> type, Level level) {
        super(type, level);
        this.setStateMinor(ID.M.ShipType, ID.ShipType.HIME);
        this.setStateMinor(ID.M.ShipClass, ID.ShipClass.MidwayHime);
        this.setStateMinor(ID.M.DamageType, ID.ShipDmgType.AVIATION);
        this.setStateMinor(ID.M.NumState, 2);
        this.setGrudgeConsumption(ConfigHandler.consumeGrudgeShip[ID.ShipConsume.BBV]);
        this.setAmmoConsumption(ConfigHandler.consumeAmmoShip[ID.ShipConsume.BBV]);
        this.ModelPos = new float[]{0F, 25F, 0F, 45F};

        // hybrid attacker: cannon + aircraft
        this.StateFlag[ID.F.AtkType_Light] = true;
        this.StateFlag[ID.F.AtkType_Heavy] = true;
        this.StateFlag[ID.F.AtkType_AirLight] = true;
        this.StateFlag[ID.F.AtkType_AirHeavy] = true;

        // initialize aircraft counts
        this.setNumAircraftLight(6);
        this.setNumAircraftHeavy(3);

        // airplanes leave from the same point on the model as in 1.10.2
        this.launchHeight = 2.0F * 0.7F;

        this.postInit();
    }

    public int getEquipType() {
        return 2;
    }

    @Override
    public int getRiderType() {
        return this.riderType;
    }

    @Override
    public void setRiderType(int type) {
        this.riderType = type;
    }

    @Override
    public void setAIList() {
        super.setAIList();
        this.goalSelector.addGoal(11, new ShipCarrierAttackGoal(this));
        this.goalSelector.addGoal(12, new ShipRangeAttackGoal(this));
    }

    @Override
    public boolean hasShipMounts() {
        return true;
    }

    @Override
    public BasicEntityMount summonMountEntity() {
        return new EntityMountMiH(ModEntities.MOUNT_MIH.get(), this.level());
    }

    @Override
    public void aiStep() {
        super.aiStep();

        if (!this.level().isClientSide() && this.tickCount % 128 == 0) {
            // Self-heal: 9% max HP + 1, whenever any grudge is left
            if (getStateMinor(ID.M.NumGrudge) > 0 && this.getHealth() < this.getMaxHealth()) {
                this.heal(this.getMaxHealth() * 0.09F + 1F);
            }

            // Ring effect: ABSORPTION to the fleet around and to the owner player
            int shipLevel = getStateMinor(ID.M.ShipLevel);
            int amplifier = shipLevel / 50;
            int duration = 50 + shipLevel;
            this.applyMarriageFleetAura(amplifier, duration, MobEffects.ABSORPTION);

            java.util.UUID ownerUUID = this.getOwnerUUID();
            if (ownerUUID != null && getStateFlag(ID.F.IsMarried) && getStateFlag(ID.F.UseRingEffect) &&
                    getStateMinor(ID.M.NumGrudge) > 0) {
                Player player = this.level().getPlayerByUUID(ownerUUID);
                if (player != null && this.distanceToSqr(player) < 256.0D) {
                    player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, duration, amplifier, false, false));
                }
            }
        }
    }

    @Override
    public double getPassengersRidingOffset() {
        if (this.isOrderedToSit()) {
            return this.getBbHeight() * 0.16F;
        } else {
            return this.getBbHeight() * 0.67F;
        }
    }
}
