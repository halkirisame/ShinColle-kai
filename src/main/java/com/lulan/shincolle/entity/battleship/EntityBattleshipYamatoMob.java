package com.lulan.shincolle.entity.battleship;

import com.lulan.shincolle.entity.BasicEntityShipHostile;
import com.lulan.shincolle.entity.other.EntityProjectileBeam;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.init.ModSounds;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.utility.ParticleHelper;

import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Battleship Yamato mob entity.
 * model state: 0:cannon, 1:head, 2:umbrella, 3:leg equip
 */
public class EntityBattleshipYamatoMob extends BasicEntityShipHostile {

    private float entityWidth = 0.8F;
    private float entityHeight = 2.1F;

    public EntityBattleshipYamatoMob(EntityType<? extends EntityBattleshipYamatoMob> type, Level level) {
        super(type, level);

        // init values
        this.setStateMinor(ID.M.ShipClass, ID.ShipClass.BBYamato);
        this.setStateMinor(ID.M.ShipType, ID.ShipType.BATTLESHIP);
        this.setStateMinor(ID.M.DamageType, ID.ShipDmgType.BATTLESHIP);
        this.setStateMinor(ID.M.NumState, 4);

        // hostile flags: no air attacks
        this.StateFlag[ID.F.AtkType_AirLight] = false;
        this.StateFlag[ID.F.AtkType_AirHeavy] = false;

        // unlimited ammo/grudge for hostile entity
        this.StateMinor[ID.M.NumAmmoLight] = 999;
        this.StateMinor[ID.M.NumAmmoHeavy] = 999;
        this.StateMinor[ID.M.NumGrudge] = 999;
        this.setAmmoConsumption(1);

        // boss bar color: PINK (unique!)
        this.bossBarColor = BossEvent.BossBarColor.PINK;

        // model display: state = 15
        this.setStateEmotion(ID.S.State, 15, false);

        this.postInit();
    }

    @Override
    public void setSizeWithScaleLevel() {
        switch (this.getScaleLevel()) {
            case 3:
                this.entityWidth = 2.3F;
                this.entityHeight = 8.4F;
                break;
            case 2:
                this.entityWidth = 1.8F;
                this.entityHeight = 6.3F;
                break;
            case 1:
                this.entityWidth = 1.3F;
                this.entityHeight = 4.2F;
                break;
            default:
                this.entityWidth = 0.8F;
                this.entityHeight = 2.1F;
                break;
        }
        this.refreshDimensions();
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return EntityDimensions.fixed(this.entityWidth, this.entityHeight);
    }

    @Override
    public void aiStep() {
        super.aiStep();

        if (this.level().isClientSide() && this.tickCount % 16 == 0
                && this.getStateEmotion(ID.S.Phase) > 0) {
            ParticleHelper.spawnStickyLightningParticle(this, 0.1F + this.getScaleLevel(), 16, 1);
        }
    }

    /**
     * Yamato's heavy attack is a two-tick special: the first hit charges (no
     * damage, sound only), the second fires a beam. Ported from 1.10.2
     * EntityBattleshipYMTMob#attackEntityWithHeavyAmmo, which used ID.S.Phase
     * the same way and, unlike the friendly version, does not consume
     * ammo/grudge/morale or flare the target - hostiles have unlimited ammo and
     * no flare mechanic.
     */
    @Override
    public boolean attackEntityWithHeavyAmmo(Entity target) {
        setCombatTick(this.tickCount);
        float atk = getAttackBaseDamage(2, target);

        if (getStateEmotion(ID.S.Phase) > 0) {
            // charged: fire the beam
            this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                    ModSounds.SHIP_YAMATO_SHOT.get(), this.getSoundSource(), 1F, 1F);

            Vec3 beamStart = new Vec3(this.getX(), this.getY() + this.getBbHeight() * 0.5D, this.getZ());
            Vec3 beamVector = target.getBoundingBox().getCenter().subtract(beamStart);

            EntityProjectileBeam beam = new EntityProjectileBeam(ModEntities.PROJECTILE_BEAM.get(), this.level());
            beam.initBeam(this, beamVector.x, beamVector.y, beamVector.z, atk);
            this.level().addFreshEntity(beam);

            this.setStateEmotion(ID.S.Phase, 0, true);
            applyEmotesReaction(3);
            return true;
        } else {
            // charging: no damage yet
            this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                    ModSounds.SHIP_YAMATO_READY.get(), this.getSoundSource(), 1F, 1F);

            this.setStateEmotion(ID.S.Phase, 1, true);
            applyEmotesReaction(3);

            return false;
        }
    }

}
