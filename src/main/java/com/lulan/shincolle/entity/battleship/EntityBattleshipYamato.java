package com.lulan.shincolle.entity.battleship;

import com.lulan.shincolle.ai.ShipPickItemGoal;
import com.lulan.shincolle.ai.ShipRangeAttackGoal;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.entity.BasicEntityShipSmall;
import com.lulan.shincolle.entity.other.EntityProjectileBeam;
import com.lulan.shincolle.handler.ConfigHandler;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.init.ModSounds;
import com.lulan.shincolle.reference.ID;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.UUID;

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

        if (!this.level().isClientSide()) {
            if (this.tickCount % 128 == 0) {
                // marriage ring aura: damage resistance + fire resistance to nearby same-owner ships
                UUID ownerUUID = this.getOwnerUUID();
                if (ownerUUID != null && getStateFlag(ID.F.IsMarried) && getStateFlag(ID.F.UseRingEffect) &&
                        getStateMinor(ID.M.NumGrudge) > 0) {
                    int shipLevel = getStateMinor(ID.M.ShipLevel);
                    int amp = shipLevel / 80;
                    int dur = 50 + shipLevel;

                    List<BasicEntityShip> nearby = this.level().getEntitiesOfClass(
                            BasicEntityShip.class, this.getBoundingBox().inflate(16D, 16D, 16D));
                    for (BasicEntityShip s : nearby) {
                        UUID sOwner = s.getOwnerUUID();
                        if (sOwner != null && sOwner.equals(ownerUUID) && this.distanceToSqr(s) < 256.0D) {
                            s.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, dur, amp, false, false));
                            s.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, dur, amp, false, false));
                        }
                    }
                }
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

        float atk = getAttackBaseDamage(2, target);

        if (getStateEmotion(ID.S.Phase) > 0) {
            // charged: fire the beam
            this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                    ModSounds.SHIP_YAMATO_SHOT.get(), this.getSoundSource(), 1F, 1F);

            double dx = target.getX() - this.getX();
            double dy = (target.getY() + target.getBbHeight() * 0.5D) - this.getY();
            double dz = target.getZ() - this.getZ();

            EntityProjectileBeam beam = new EntityProjectileBeam(ModEntities.PROJECTILE_BEAM.get(), this.level());
            beam.initBeam(this, dx, dy, dz, atk, 32F, 20);
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
}
