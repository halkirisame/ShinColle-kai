package com.lulan.shincolle.entity.hime;

import com.lulan.shincolle.ai.ShipRangeAttackGoal;
import com.lulan.shincolle.entity.BasicEntityShipSmall;
import com.lulan.shincolle.entity.IShipRiderType;
import com.lulan.shincolle.handler.ConfigHandler;
import com.lulan.shincolle.reference.ID;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * CA (Heavy Cruiser) Hime entity - cruiser type with night combat bonuses.
 * Grants itself MOVEMENT_SPEED and JUMP at night.
 * Gains CRI and THIT bonuses at night.
 */
public class EntityCAHime extends BasicEntityShipSmall implements IShipRiderType {

    private int riderType;

    public EntityCAHime(EntityType<? extends EntityCAHime> type, Level level) {
        super(type, level);
        this.setStateMinor(ID.M.ShipType, ID.ShipType.HIME);
        this.setStateMinor(ID.M.ShipClass, ID.ShipClass.CAHime);
        this.setStateMinor(ID.M.DamageType, ID.ShipDmgType.CRUISER);
        this.setStateMinor(ID.M.NumState, 5);
        this.setGrudgeConsumption(ConfigHandler.consumeGrudgeShip[ID.ShipConsume.CA]);
        this.setAmmoConsumption(ConfigHandler.consumeAmmoShip[ID.ShipConsume.CA]);
        this.ModelPos = new float[]{0F, 10F, 0F, 45F};

        // cannon only
        this.StateFlag[ID.F.AtkType_AirLight] = false;
        this.StateFlag[ID.F.AtkType_AirHeavy] = false;

        this.postInit();
    }

    public int getEquipType() {
        return 1;
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
        this.goalSelector.addGoal(11, new ShipRangeAttackGoal(this));
    }

    @Override
    public void calcShipAttributesAddRaw() {
        super.calcShipAttributesAddRaw();

        // Night bonus: +0.1 CRI and +0.2 THIT
        if (!this.level().isDay()) {
            this.shipAttrs.setAttrsBuffed(ID.Attrs.CRI,
                    this.shipAttrs.getAttrsBuffed(ID.Attrs.CRI) + 0.1F);
            this.shipAttrs.setAttrsBuffed(ID.Attrs.THIT,
                    this.shipAttrs.getAttrsBuffed(ID.Attrs.THIT) + 0.2F);
        }
    }

    @Override
    public void aiStep() {
        super.aiStep();

        if (!this.level().isClientSide()) {
            if (this.tickCount % 128 == 0) {
                // Ring effect: MOVEMENT_SPEED + JUMP on self whenever it is not daytime
                if (getStateFlag(ID.F.UseRingEffect) && !this.level().isDay()) {
                    int level = getStateMinor(ID.M.ShipLevel);
                    this.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 150, level / 50, false, false));
                    this.addEffect(new MobEffectInstance(MobEffects.JUMP, 150, level / 40, false, false));
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
