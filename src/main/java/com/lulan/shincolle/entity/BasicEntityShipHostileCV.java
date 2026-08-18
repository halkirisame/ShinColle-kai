package com.lulan.shincolle.entity;

import com.lulan.shincolle.ai.ShipCarrierAttackGoal;
import com.lulan.shincolle.entity.other.EntityAirplaneTMob;
import com.lulan.shincolle.entity.other.EntityAirplaneZeroMob;
import com.lulan.shincolle.init.ModEntities;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * Base class for hostile carrier-type ships with aircraft attacks.
 * Extends BasicEntityShipHostile and adds IShipAircraftAttack.
 * Spawns hostile airplane variants (ZeroMob, TMob).
 * <p>
 * Ported from 1.10.2 BasicEntityShipHostileCV.
 */
public abstract class BasicEntityShipHostileCV extends BasicEntityShipHostile implements IShipAircraftAttack {

    protected double launchHeight = 2.0D;

    protected BasicEntityShipHostileCV(EntityType<? extends BasicEntityShipHostileCV> type, Level level) {
        super(type, level);
    }

    // ========== AI Setup ==========

    // 1.10.2's EntityCarrierAkagiMob/KagaMob attack with EntityAIShipCarrierAttack
    // rather than the cannon goal every other hostile Mob subclass picks.
    @Override
    protected boolean usesCannonAttack() {
        return false;
    }

    @Override
    protected void setAIList() {
        super.setAIList();
        this.goalSelector.addGoal(11, new ShipCarrierAttackGoal(this));
    }

    // ========== Aircraft Count ==========

    @Override
    public int getNumAircraftLight() {
        // Hostile carriers did not consume a finite aircraft inventory in the
        // legacy implementation.  Returning a positive count keeps the shared
        // carrier goal available without introducing a hidden spawn-time setup
        // requirement for every hostile carrier subtype.
        return 10;
    }

    @Override
    public void setNumAircraftLight(int par1) {
        // Hostile carrier aircraft are unlimited.
    }

    @Override
    public int getNumAircraftHeavy() {
        return 10;
    }

    @Override
    public void setNumAircraftHeavy(int par1) {
        // Hostile carrier aircraft are unlimited.
    }

    @Override
    public boolean hasAirLight() {
        return true;
    }

    @Override
    public boolean hasAirHeavy() {
        return true;
    }

    public double getLaunchHeight() {
        return this.launchHeight;
    }

    // ========== Airplane Factory ==========

    /**
     * Create a hostile airplane entity for attacking.
     * Subclasses can override to change airplane types.
     */
    protected BasicEntityAirplane getAttackAirplane(boolean isLight) {
        if (isLight) {
            return new EntityAirplaneZeroMob(ModEntities.AIRPLANE_ZERO_MOB.get(), this.level());
        } else {
            return new EntityAirplaneTMob(ModEntities.AIRPLANE_T_MOB.get(), this.level());
        }
    }

    // ========== Light Aircraft Attack ==========

    @Override
    public boolean attackEntityWithAircraft(Entity target) {
        // launch position
        float summonHeight = (float) (this.getY() + launchHeight);

        if (!level().getBlockState(
                new net.minecraft.core.BlockPos(
                        (int) this.getX(),
                        (int) (this.getY() + launchHeight),
                        (int) this.getZ())).isAir()) {
            summonHeight = (float) this.getY() + 1F;
        }

        if (this.getVehicle() instanceof BasicEntityMount) {
            summonHeight -= 1.5F;
        }

        // spawn airplane
        BasicEntityAirplane plane = getAttackAirplane(true);
        plane.initAttrs(this, target, this.getScaleLevel(), summonHeight);
        this.level().addFreshEntity(plane);

        applySoundAtAttacker(3, target);
        applyParticleAtAttacker(3, target, target);
        applyEmotesReaction(3);

        return true;
    }

    // ========== Heavy Aircraft Attack ==========

    @Override
    public boolean attackEntityWithHeavyAircraft(Entity target) {
        float summonHeight = (float) (this.getY() + launchHeight);

        if (!level().getBlockState(
                new net.minecraft.core.BlockPos(
                        (int) this.getX(),
                        (int) (this.getY() + launchHeight),
                        (int) this.getZ())).isAir()) {
            summonHeight = (float) this.getY() + 0.5F;
        }

        if (this.getVehicle() instanceof BasicEntityMount) {
            summonHeight -= 1.5F;
        }

        BasicEntityAirplane plane = getAttackAirplane(false);
        plane.initAttrs(this, target, this.getScaleLevel(), summonHeight);
        this.level().addFreshEntity(plane);

        applySoundAtAttacker(4, target);
        applyParticleAtAttacker(4, target, target);
        applyEmotesReaction(3);

        return true;
    }
}
