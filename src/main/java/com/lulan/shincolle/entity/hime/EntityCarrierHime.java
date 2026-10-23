package com.lulan.shincolle.entity.hime;

import com.lulan.shincolle.ai.ShipCarrierAttackGoal;
import com.lulan.shincolle.ai.ShipClassAttackGate;
import com.lulan.shincolle.entity.BasicEntityShipCV;
import com.lulan.shincolle.entity.BasicEntityMount;
import com.lulan.shincolle.entity.IShipRiderType;
import com.lulan.shincolle.entity.mounts.EntityMountCaH;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.handler.ConfigHandler;
import com.lulan.shincolle.reference.ID;

import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * Carrier Hime entity - pure aircraft carrier type.
 * Provides JUMP boost to nearby allied ships.
 */
public class EntityCarrierHime extends BasicEntityShipCV implements IShipRiderType {

    private int riderType;

    public EntityCarrierHime(EntityType<? extends EntityCarrierHime> type, Level level) {
        super(type, level);
        this.setStateMinor(ID.M.ShipType, ID.ShipType.HIME);
        this.setStateMinor(ID.M.ShipClass, ID.ShipClass.CVHime);
        this.setStateMinor(ID.M.DamageType, ID.ShipDmgType.CARRIER);
        this.setStateMinor(ID.M.NumState, 3);
        this.setGrudgeConsumption(ConfigHandler.consumeGrudgeShip[ID.ShipConsume.CV]);
        this.setAmmoConsumption(ConfigHandler.consumeAmmoShip[ID.ShipConsume.CV]);
        this.ModelPos = new float[]{0F, 25F, 0F, 45F};

        // aircraft only: disable cannon attacks
        this.StateFlag[ID.F.AtkType_Light] = false;
        this.StateFlag[ID.F.AtkType_Heavy] = false;
        this.StateFlag[ID.F.AtkType_AirLight] = true;
        this.StateFlag[ID.F.AtkType_AirHeavy] = true;

        // initialize aircraft counts
        this.setNumAircraftLight(6);
        this.setNumAircraftHeavy(3);

        // airplanes leave from the same point on the model as in 1.10.2
        this.launchHeight = 1.9F * 0.9F;

        this.postInit();
    }

    public int getEquipType() {
        return 3;
    }

    @Override
    public void calcShipAttributesAddEquip() {
        super.calcShipAttributesAddEquip();
        ShipClassAttackGate.finalizeEquipment(this);
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
    }

    @Override
    public boolean hasShipMounts() {
        return true;
    }

    @Override
    public BasicEntityMount summonMountEntity() {
        return new EntityMountCaH(ModEntities.MOUNT_CAH.get(), this.level());
    }

    @Override
    public void aiStep() {
        super.aiStep();

        if (!this.level().isClientSide()) {
            if (this.tickCount % 128 == 0) {
                // marriage ring aura: jump boost to the same-owner ships around, this one included
                int shipLevel = getStateMinor(ID.M.ShipLevel);
                this.applyMarriageFleetAura(shipLevel / 70, 50 + shipLevel, MobEffects.JUMP);
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
