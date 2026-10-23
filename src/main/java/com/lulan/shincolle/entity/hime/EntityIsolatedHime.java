package com.lulan.shincolle.entity.hime;

import com.lulan.shincolle.ai.ShipCarrierAttackGoal;
import com.lulan.shincolle.ai.ShipClassAttackGate;
import com.lulan.shincolle.ai.ShipRangeAttackGoal;
import com.lulan.shincolle.entity.BasicEntityShipCV;
import com.lulan.shincolle.entity.BasicEntityMount;
import com.lulan.shincolle.entity.IShipRiderType;
import com.lulan.shincolle.entity.ShipInnateAttackEffects;
import com.lulan.shincolle.entity.mounts.EntityMountIsH;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.handler.ConfigHandler;
import com.lulan.shincolle.reference.ID;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * Isolated Island Hime entity - CV type with both cannon and aircraft attacks.
 * Its attacks blind, and poison when married with the ring on.
 */
public class EntityIsolatedHime extends BasicEntityShipCV implements IShipRiderType {

    private int riderType;

    public EntityIsolatedHime(EntityType<? extends EntityIsolatedHime> type, Level level) {
        super(type, level);
        this.setStateMinor(ID.M.ShipType, ID.ShipType.HIME);
        this.setStateMinor(ID.M.ShipClass, ID.ShipClass.IsolatedHime);
        this.setStateMinor(ID.M.DamageType, ID.ShipDmgType.AVIATION);
        this.setStateMinor(ID.M.NumState, 8);
        this.setGrudgeConsumption(ConfigHandler.consumeGrudgeShip[ID.ShipConsume.BBV]);
        this.setAmmoConsumption(ConfigHandler.consumeAmmoShip[ID.ShipConsume.BBV]);
        this.ModelPos = new float[]{0F, 20F, 0F, 45F};

        // hybrid attacker: cannon + aircraft
        this.StateFlag[ID.F.AtkType_Light] = true;
        this.StateFlag[ID.F.AtkType_Heavy] = true;
        this.StateFlag[ID.F.AtkType_AirLight] = true;
        this.StateFlag[ID.F.AtkType_AirHeavy] = true;

        // initialize aircraft counts
        this.setNumAircraftLight(6);
        this.setNumAircraftHeavy(3);

        // airplanes leave from the same point on the model as in 1.10.2
        this.launchHeight = 1.6F * 0.7F;

        this.postInit();
    }

    public int getEquipType() {
        return 2;
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
        ShipInnateAttackEffects.put(this, ShipInnateAttackEffects.BLINDNESS,
                0, 100 + level, level);
        // Original 1.10.2 gates this poison on marriage, not on level. The
        // level / 75 term is the amplifier, so an unmarried ship gets nothing
        // while a married one gets poison I below level 75.
        if (getStateFlag(ID.F.IsMarried) && getStateFlag(ID.F.UseRingEffect)) {
            ShipInnateAttackEffects.put(this, ShipInnateAttackEffects.POISON,
                    level / 75, 80 + level, level);
        }
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
        return new EntityMountIsH(ModEntities.MOUNT_ISH.get(), this.level());
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
