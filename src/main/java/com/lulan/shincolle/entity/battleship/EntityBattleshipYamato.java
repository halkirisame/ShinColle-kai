package com.lulan.shincolle.entity.battleship;

import com.lulan.shincolle.ai.ShipPickItemGoal;
import com.lulan.shincolle.ai.ShipRangeAttackGoal;
import com.lulan.shincolle.entity.BasicEntityShipSmall;
import com.lulan.shincolle.entity.ShipInnateAttackEffects;
import com.lulan.shincolle.entity.other.EntityProjectileBeam;
import com.lulan.shincolle.handler.ConfigHandler;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.init.ModSounds;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.utility.ParticleHelper;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;


/**
 * Battleship Yamato entity.
 * model state: 0:cannon, 1:head, 2:umbrella, 3:leg equip
 */
public class EntityBattleshipYamato extends BasicEntityShipSmall {

    public EntityBattleshipYamato(EntityType<? extends EntityBattleshipYamato> type, Level level) {
        super(type, level);
        this.setStateMinor(ID.M.ShipType, ID.ShipType.BATTLESHIP);
        this.setStateMinor(ID.M.ShipClass, ID.ShipClass.BBYamato);
        this.setStateMinor(ID.M.DamageType, ID.ShipDmgType.BATTLESHIP);
        this.setStateMinor(ID.M.NumState, 4);
        this.setGrudgeConsumption(ConfigHandler.consumeGrudgeShip[ID.ShipConsume.BB]);
        this.setAmmoConsumption(ConfigHandler.consumeAmmoShip[ID.ShipConsume.BB]);
        this.ModelPos = new float[]{0F, 25F, 0F, 40F};

        // set attack type
        this.StateFlag[ID.F.AtkType_AirLight] = false;
        this.StateFlag[ID.F.AtkType_AirHeavy] = false;
        this.StateFlag[ID.F.CanPickItem] = true;

        this.postInit();
    }

    /**
     * Equip type: 1=cannon+misc
     */
    @Override
    public int getEquipType() {
        return 1;
    }

    @Override
    public void calcShipAttributesAddEffect() {
        super.calcShipAttributesAddEffect();
        int level = this.getLevel();
        ShipInnateAttackEffects.put(this, ShipInnateAttackEffects.MINING_FATIGUE,
                level / 70, 100 + level, level);
    }

    @Override
    public void setAIList() {
        super.setAIList();

        // range attack
        this.goalSelector.addGoal(11, new ShipRangeAttackGoal(this));

        // pick item
        this.goalSelector.addGoal(20, new ShipPickItemGoal(this, 4.0F));
    }

    @Override
    public void aiStep() {
        super.aiStep();

        if (!this.level().isClientSide() && this.getStateEmotion(ID.S.Phase) > 0) {
            Entity target = this.getEntityTarget();
            if (target == null || !target.isAlive()) {
                this.setStateEmotion(ID.S.Phase, 0, true);
            }
        }

        if (!this.level().isClientSide() && this.getStateEmotion(ID.S.Phase) > 0) {
            YamatoAim.turnToward(this, this.getEntityTarget(), YamatoAim.TURN_STEP);
        }

        if (this.level().isClientSide() && this.tickCount % 16 == 0
                && this.getStateEmotion(ID.S.Phase) > 0) {
            ParticleHelper.spawnStickyLightningParticle(this, 0.1F + this.getScaleLevel(), 16, 1);
        }

        if (!this.level().isClientSide()) {
            if (this.tickCount % 128 == 0) {
                // marriage ring aura: damage resistance and fire resistance to the same-owner ships around, this one included
                int shipLevel = getStateMinor(ID.M.ShipLevel);
                this.applyMarriageFleetAura(shipLevel / 70, 50 + shipLevel, MobEffects.DAMAGE_RESISTANCE, MobEffects.FIRE_RESISTANCE);
            }
        }
    }

    /**
     * Yamato's heavy attack is a two-tick special: the first hit charges (no
     * damage, sound + flare only), the second fires a beam. Ported from 1.10.2
     * EntityBattleshipYMT#attackEntityWithHeavyAmmo, which used ID.S.Phase the
     * same way. Ammo/grudge/morale are spent on both the charge and the shot,
     * matching the original - charging isn't free.
     */
    @Override
    public boolean attackEntityWithHeavyAmmo(Entity target) {
        if (!decrAmmoNum(1, this.getAmmoConsumption()))
            return false;

        addShipExp(ConfigHandler.expGain[2]);
        decrGrudgeNum(ConfigHandler.consumeGrudgeAction[1]);
        decrMorale(2);
        setCombatTick(this.tickCount);
        if (this.random.nextInt(10) > 7) {
            this.playVoice(this.getCustomSound(1, this), this.getSoundVolume(), this.getVoicePitch());
        }

        float atk = getAttackBaseDamage(2, target);

        if (getStateEmotion(ID.S.Phase) > 0) {
            // charged: fire the beam
            YamatoAim.faceIfOff(this, target);
            this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                    ModSounds.SHIP_YAMATO_SHOT.get(), this.getSoundSource(), 1F, 1F);

            Vec3 beamStart = new Vec3(this.getX(), this.getY() + this.getBbHeight() * 0.5D, this.getZ());
            Vec3 beamVector = target.getBoundingBox().getCenter().subtract(beamStart);

            EntityProjectileBeam beam = new EntityProjectileBeam(ModEntities.PROJECTILE_BEAM.get(), this.level());
            beam.initBeam(this, beamVector.x, beamVector.y, beamVector.z, atk);
            this.level().addFreshEntity(beam);
            Vec3 beamDir = beamVector.normalize();
            com.lulan.shincolle.network.ModNetworking.sendToAllTracking(
                    com.lulan.shincolle.network.S2CSpawnParticlePacket.targetParameters(
                            com.lulan.shincolle.network.ParticlePacketRoute.YAMATO_BEAM_TYPE,
                            this.getId(), beam.getId(), beamDir.x, beamDir.y, beamDir.z), this);
            triggerAttackAnimation();

            this.setStateEmotion(ID.S.Phase, 0, true);
            applyEmotesReaction(3);
            return true;
        } else {
            // charging: no damage yet
            this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                    ModSounds.SHIP_YAMATO_READY.get(), this.getSoundSource(), 1F, 1F);

            this.setStateEmotion(ID.S.Phase, 1, true);
            com.lulan.shincolle.network.ModNetworking.sendToAllTracking(
                    com.lulan.shincolle.network.S2CSpawnParticlePacket.entityParameters(
                            com.lulan.shincolle.network.ParticlePacketRoute.YAMATO_CHARGE_TYPE,
                            this.getId(), 1D, 0D, 0D), this);
            applyEmotesReaction(3);

            if (ConfigHandler.canFlare()) {
                this.flareTarget(target);
            }
            return false;
        }
    }

    @Override
    public double getPassengersRidingOffset() {
        if (this.isOrderedToSit()) {
            if (getStateEmotion(ID.S.Emotion) == ID.Emotion.BORED) {
                return this.getBbHeight() * 0.1D;
            } else {
                return this.getBbHeight() * 0.4D;
            }
        } else {
            return this.getBbHeight() * 0.75D;
        }
    }

    /** Halves the wait between the charge and the shot; the wait after the shot is unchanged. */
    @Override
    public float heavyDelayScale() {
        return this.getStateEmotion(ID.S.Phase) > 0 ? 0.5F : 1F;
    }

    /** A charged beam goes out before the light cannons fire again, so a light shot cannot drop the charge. */
    @Override
    public boolean holdsLightFire() {
        return this.getStateEmotion(ID.S.Phase) > 0;
    }
}
