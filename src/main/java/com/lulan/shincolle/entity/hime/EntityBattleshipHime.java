package com.lulan.shincolle.entity.hime;

import com.lulan.shincolle.ai.ShipRangeAttackGoal;
import com.lulan.shincolle.ai.ShipClassAttackGate;
import com.lulan.shincolle.entity.BasicEntityShipSmall;
import com.lulan.shincolle.entity.BasicEntityMount;
import com.lulan.shincolle.entity.IShipRiderType;
import com.lulan.shincolle.entity.ShipInnateAttackEffects;
import com.lulan.shincolle.entity.mounts.EntityMountBaH;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.handler.ConfigHandler;
import com.lulan.shincolle.reference.ID;

import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * Battleship Hime entity - heavy cannon ship.
 * Provides DAMAGE_RESISTANCE and FIRE_RESISTANCE to nearby allied ships.
 */
public class EntityBattleshipHime extends BasicEntityShipSmall implements IShipRiderType {

    private int riderType;

    public EntityBattleshipHime(EntityType<? extends EntityBattleshipHime> type, Level level) {
        super(type, level);
        this.setStateMinor(ID.M.ShipType, ID.ShipType.HIME);
        this.setStateMinor(ID.M.ShipClass, ID.ShipClass.BBHime);
        this.setStateMinor(ID.M.DamageType, ID.ShipDmgType.BATTLESHIP);
        this.setStateMinor(ID.M.NumState, 1);
        this.setGrudgeConsumption(ConfigHandler.consumeGrudgeShip[ID.ShipConsume.BB]);
        this.setAmmoConsumption(ConfigHandler.consumeAmmoShip[ID.ShipConsume.BB]);
        this.ModelPos = new float[]{0F, 25F, 0F, 45F};

        // cannon only
        this.StateFlag[ID.F.AtkType_AirLight] = false;
        this.StateFlag[ID.F.AtkType_AirHeavy] = false;

        this.postInit();
    }

    public int getEquipType() {
        return 1;
    }

    @Override
    public void calcShipAttributesAddEquip() {
        super.calcShipAttributesAddEquip();
        ShipClassAttackGate.finalizeEquipment(this);
    }

    @Override
    public void calcShipAttributesAddEffect() {
        super.calcShipAttributesAddEffect();
        int level = this.getLevel();
        ShipInnateAttackEffects.put(this, ShipInnateAttackEffects.MINING_FATIGUE,
                level / 70, 100 + level, level);
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
    public boolean hasShipMounts() {
        return true;
    }

    @Override
    public BasicEntityMount summonMountEntity() {
        return new EntityMountBaH(ModEntities.MOUNT_BAH.get(), this.level());
    }

    @Override
    public void aiStep() {
        super.aiStep();

        if (!this.level().isClientSide()) {
            if (this.tickCount % 128 == 0) {
                // marriage ring aura: damage resistance and fire resistance to the same-owner ships around, this one included
                int shipLevel = getStateMinor(ID.M.ShipLevel);
                this.applyMarriageFleetAura(shipLevel / 70, 50 + shipLevel, MobEffects.DAMAGE_RESISTANCE, MobEffects.FIRE_RESISTANCE);
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
