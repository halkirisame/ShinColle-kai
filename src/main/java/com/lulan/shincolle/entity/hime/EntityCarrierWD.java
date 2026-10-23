package com.lulan.shincolle.entity.hime;

import com.lulan.shincolle.ai.ShipCarrierAttackGoal;
import com.lulan.shincolle.ai.ShipClassAttackGate;
import com.lulan.shincolle.ai.ShipRangeAttackGoal;
import com.lulan.shincolle.entity.BasicEntityShipCV;
import com.lulan.shincolle.entity.BasicEntityMount;
import com.lulan.shincolle.entity.IShipRiderType;
import com.lulan.shincolle.entity.mounts.EntityMountCaWD;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.handler.ConfigHandler;
import com.lulan.shincolle.reference.ID;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Entity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * Carrier Water Demon entity - aircraft carrier demon type (not hime).
 * Provides DIG_SPEED (Haste) to nearby allied ships.
 * Self-buffs with NIGHT_VISION.
 */
public class EntityCarrierWD extends BasicEntityShipCV implements IShipRiderType {

    private int riderType;

    public EntityCarrierWD(EntityType<? extends EntityCarrierWD> type, Level level) {
        super(type, level);
        this.setStateMinor(ID.M.ShipType, ID.ShipType.DEMON);
        this.setStateMinor(ID.M.ShipClass, ID.ShipClass.CVWD);
        this.setStateMinor(ID.M.DamageType, ID.ShipDmgType.CARRIER);
        this.setStateMinor(ID.M.NumState, 2);
        this.setGrudgeConsumption(ConfigHandler.consumeGrudgeShip[ID.ShipConsume.CV]);
        this.setAmmoConsumption(ConfigHandler.consumeAmmoShip[ID.ShipConsume.CV]);
        this.ModelPos = new float[]{0F, 25F, 0F, 45F};

        // This hull carries a light cannon and aircraft, never a heavy cannon.
        ShipClassAttackGate.restoreCapabilities(this);

        // initialize aircraft counts
        this.setNumAircraftLight(6);
        this.setNumAircraftHeavy(3);

        // airplanes leave from the same point on the model as in 1.10.2
        this.launchHeight = 1.9F * 1.2F;

        this.postInit();
    }

    public int getEquipType() {
        return 3;
    }

    @Override
    public boolean attackEntityWithHeavyAmmo(Entity target) {
        return false;
    }

    @Override
    public boolean attackEntityWithHeavyAmmo(BlockPos target) {
        return false;
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
        // WD is the one carrier hull that also runs cannons in 1.10.2; the
        // other carriers (Akagi/Kaga/Wo/CarrierHime/MountCaH) are aircraft-only.
        this.goalSelector.addGoal(12, new ShipRangeAttackGoal(this));
    }

    @Override
    public boolean hasShipMounts() {
        return true;
    }

    @Override
    public BasicEntityMount summonMountEntity() {
        return new EntityMountCaWD(ModEntities.MOUNT_CAWD.get(), this.level());
    }

    @Override
    public void aiStep() {
        super.aiStep();

        if (!this.level().isClientSide()) {
            if (this.tickCount % 128 == 0) {
                // Self-buff: NIGHT_VISION
                this.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 150, 0, false, false));

                // marriage ring aura: haste to the same-owner ships around, this one included
                int shipLevel = getStateMinor(ID.M.ShipLevel);
                this.applyMarriageFleetAura(shipLevel / 70, 50 + shipLevel, MobEffects.DIG_SPEED);
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
