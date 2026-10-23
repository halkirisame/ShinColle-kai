package com.lulan.shincolle.entity.carrier;

import com.lulan.shincolle.ai.ShipCarrierAttackGoal;
import com.lulan.shincolle.entity.BasicEntityShipCV;
import com.lulan.shincolle.handler.ConfigHandler;
import com.lulan.shincolle.reference.ID;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;


/**
 * Carrier Wo-class entity.
 * model state: 0:head, 1:weapon, 2:neck, 3:cloak, 4:eye effect
 */
public class EntityCarrierWo extends BasicEntityShipCV {

    public EntityCarrierWo(EntityType<? extends EntityCarrierWo> type, Level level) {
        super(type, level);
        this.setStateMinor(ID.M.ShipType, ID.ShipType.STANDARD_CARRIER);
        this.setStateMinor(ID.M.ShipClass, ID.ShipClass.CVWO);
        this.setStateMinor(ID.M.DamageType, ID.ShipDmgType.CARRIER);
        this.setStateMinor(ID.M.NumState, 5);
        this.setGrudgeConsumption(ConfigHandler.consumeGrudgeShip[ID.ShipConsume.CV]);
        this.setAmmoConsumption(ConfigHandler.consumeAmmoShip[ID.ShipConsume.CV]);
        this.ModelPos = new float[]{0F, 20F, 0F, 30F};

        // set attack type: carrier has no cannon attacks
        this.StateFlag[ID.F.AtkType_Light] = false;
        this.StateFlag[ID.F.AtkType_Heavy] = false;

        // initialize aircraft counts
        this.setNumAircraftLight(6);
        this.setNumAircraftHeavy(3);

        // airplanes leave from the same point on the model as in 1.10.2
        this.launchHeight = 1.9F * 0.9F;

        this.postInit();
    }

    @Override
    public void calcShipAttributesAddRaw() {
        super.calcShipAttributesAddRaw();
        this.maxAircraftLight += this.getLevel() * 0.25F;
        this.maxAircraftHeavy += this.getLevel() * 0.15F;
    }

    /**
     * Equip type: 3=airplane+misc
     */
    @Override
    public int getEquipType() {
        return 3;
    }

    @Override
    public void setAIList() {
        super.setAIList();

        // carrier attack
        this.goalSelector.addGoal(11, new ShipCarrierAttackGoal(this));
    }

    @Override
    public void aiStep() {
        super.aiStep();

        if (!this.level().isClientSide()) {
            if (this.tickCount % 128 == 0) {
                // marriage ring aura: damage boost to the same-owner ships around, this one included
                int shipLevel = getStateMinor(ID.M.ShipLevel);
                this.applyMarriageFleetAura(shipLevel / 80, 30 + shipLevel, MobEffects.DAMAGE_BOOST);
            }
        }
    }

    @Override
    public double getPassengersRidingOffset() {
        if (this.isOrderedToSit()) {
            if (getStateEmotion(ID.S.Emotion) == ID.Emotion.BORED) {
                return this.getBbHeight() * 0.2F;
            } else {
                return this.getBbHeight() * 0.43F;
            }
        } else {
            return this.getBbHeight() * 0.68F;
        }
    }
}
