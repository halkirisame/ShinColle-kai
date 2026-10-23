package com.lulan.shincolle.entity.hime;

import com.lulan.shincolle.ai.ShipCarrierAttackGoal;
import com.lulan.shincolle.ai.ShipRangeAttackGoal;
import com.lulan.shincolle.entity.BasicEntityShipCV;
import com.lulan.shincolle.entity.IShipRiderType;
import com.lulan.shincolle.entity.ShipSupportAbilities;
import com.lulan.shincolle.entity.other.EntityFloatingFort;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.handler.ConfigHandler;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.utility.EmotionHelper;
import com.lulan.shincolle.utility.ParticleHelper;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * Northern Hime entity - small CV type with light self-healing.
 * Directly heals nearby allies while the ring effect is enabled.
 */
public class EntityNorthernHime extends BasicEntityShipCV implements IShipRiderType {

    private int riderType;
    private final HimeRiding riding = new HimeRiding(this);

    public EntityNorthernHime(EntityType<? extends EntityNorthernHime> type, Level level) {
        super(type, level);
        this.setStateMinor(ID.M.ShipType, ID.ShipType.HIME);
        this.setStateMinor(ID.M.ShipClass, ID.ShipClass.NorthernHime);
        this.setStateMinor(ID.M.DamageType, ID.ShipDmgType.AVIATION);
        this.setStateMinor(ID.M.NumState, 4);
        this.setGrudgeConsumption(ConfigHandler.consumeGrudgeShip[ID.ShipConsume.BBV]);
        this.setAmmoConsumption(ConfigHandler.consumeAmmoShip[ID.ShipConsume.BBV]);
        this.ModelPos = new float[]{0F, 10F, 0F, 45F};

        // hybrid attacker: cannon + aircraft
        this.StateFlag[ID.F.AtkType_Light] = true;
        this.StateFlag[ID.F.AtkType_Heavy] = true;
        this.StateFlag[ID.F.AtkType_AirLight] = true;
        this.StateFlag[ID.F.AtkType_AirHeavy] = true;

        // initialize aircraft counts
        this.setNumAircraftLight(6);
        this.setNumAircraftHeavy(3);

        // airplanes leave from the same point on the model as in 1.10.2
        this.launchHeight = 0.9F * 1.0F;

        this.postInit();
    }

    public int getEquipType() {
        return 2;
    }

    /**
     * The heavy cannon shot of the northern princess is a floating fortress rather than a missile.
     * The heavy airplane stays the ordinary takoyaki of every carrier.
     */
    @Override
    protected boolean launchHeavyEntity(Entity target) {
        float launchPos = (float) this.getY() + this.getBbHeight();
        if (this.getShipDepth() > 0D) {
            launchPos += 0.2F;
        }
        EntityFloatingFort fort = new EntityFloatingFort(ModEntities.FLOATING_FORT.get(), this.level());
        fort.initAttrs(this, target, 0, launchPos);
        this.level().addFreshEntity(fort);
        return true;
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
    public void aiStep() {
        ShipSupportAbilities.tickHealing(this, ShipSupportAbilities.HealingProfile.NORTHERN);
        super.aiStep();
        if (!this.level().isClientSide()) {
            this.riding.tick();
        }

        // [BETA STOPGAP] Dripping water while the ship's water-drip model part is on.
        // Ported from the original's 8-tick client effect (particle type 28).
        if (this.level().isClientSide()) {
            if (this.tickCount % 8 == 0
                    && EmotionHelper.checkModelState(2, this.getStateEmotion(ID.S.State))
                    && !this.getStateFlag(ID.F.NoFuel)) {
                double dripY = (this.getIsSitting() || this.isPassenger()) ? 0.9D : 1.1D;
                ParticleHelper.spawnAttackParticleAt(this.level(),
                        this.getX(), this.getY() + dripY, this.getZ(), 0D, 0D, 0D, 28);
            }
        }

    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (com.lulan.shincolle.handler.AllySpellDamageGuard.shouldBlock(source, this)) {
            return false;
        }
        if (!this.level().isClientSide() && !source.is(DamageTypes.IN_WALL)) {
            this.riding.dismount();
        }
        return super.hurt(source, amount);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag nbt) {
        this.riding.beforeSave();
        super.addAdditionalSaveData(nbt);
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
