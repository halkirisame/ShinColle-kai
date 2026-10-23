package com.lulan.shincolle.entity.hime;

import com.lulan.shincolle.ai.ShipCarrierAttackGoal;
import com.lulan.shincolle.ai.ShipRangeAttackGoal;
import com.lulan.shincolle.entity.BasicEntityShipCV;
import com.lulan.shincolle.entity.BasicEntityMount;
import com.lulan.shincolle.entity.IShipRiderType;
import com.lulan.shincolle.entity.ShipSupportAbilities;
import com.lulan.shincolle.entity.mounts.EntityMountAfH;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.handler.ConfigHandler;
import com.lulan.shincolle.reference.ID;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * Airfield Hime entity - CV type with both cannon and aircraft attacks.
 * Self-heals and directly heals nearby allies while the ring effect is enabled.
 */
public class EntityAirfieldHime extends BasicEntityShipCV implements IShipRiderType {

    private int riderType;

    public EntityAirfieldHime(EntityType<? extends EntityAirfieldHime> type, Level level) {
        super(type, level);
        this.setStateMinor(ID.M.ShipType, ID.ShipType.HIME);
        this.setStateMinor(ID.M.ShipClass, ID.ShipClass.AirfieldHime);
        this.setStateMinor(ID.M.DamageType, ID.ShipDmgType.AVIATION);
        this.setStateMinor(ID.M.NumState, 4);
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
        this.launchHeight = 1.9F * 0.7F;

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
        return new EntityMountAfH(ModEntities.MOUNT_AFH.get(), this.level());
    }

    @Override
    public void aiStep() {
        ShipSupportAbilities.tickHealing(this, ShipSupportAbilities.HealingProfile.AIRFIELD);
        super.aiStep();
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
