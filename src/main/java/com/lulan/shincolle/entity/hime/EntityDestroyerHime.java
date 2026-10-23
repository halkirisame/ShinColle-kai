package com.lulan.shincolle.entity.hime;

import com.lulan.shincolle.ai.ShipPickItemGoal;
import com.lulan.shincolle.ai.ShipRangeAttackGoal;
import com.lulan.shincolle.entity.BasicEntityShipSmall;
import com.lulan.shincolle.entity.IShipRiderType;
import com.lulan.shincolle.handler.ConfigHandler;
import com.lulan.shincolle.reference.ID;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * Destroyer Hime entity - fast destroyer type with item pickup.
 * Provides MOVEMENT_SPEED and DIG_SPEED (Haste) to nearby player.
 */
public class EntityDestroyerHime extends BasicEntityShipSmall implements IShipRiderType {

    private int riderType;

    public EntityDestroyerHime(EntityType<? extends EntityDestroyerHime> type, Level level) {
        super(type, level);
        this.setStateMinor(ID.M.ShipType, ID.ShipType.HIME);
        this.setStateMinor(ID.M.ShipClass, ID.ShipClass.DDHime);
        this.setStateMinor(ID.M.DamageType, ID.ShipDmgType.DESTROYER);
        this.setStateMinor(ID.M.NumState, 6);
        this.setGrudgeConsumption(ConfigHandler.consumeGrudgeShip[ID.ShipConsume.DD]);
        this.setAmmoConsumption(ConfigHandler.consumeAmmoShip[ID.ShipConsume.DD]);
        this.ModelPos = new float[]{0F, 20F, 0F, 45F};

        // cannon only
        this.StateFlag[ID.F.AtkType_AirLight] = false;
        this.StateFlag[ID.F.AtkType_AirHeavy] = false;
        this.StateFlag[ID.F.CanPickItem] = true;

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
        this.goalSelector.addGoal(20, new ShipPickItemGoal(this, 4.0F));
    }

    @Override
    public void aiStep() {
        super.aiStep();

        if (!this.level().isClientSide()) {
            if (this.tickCount % 128 == 0) {
                // Ring effect: MOVEMENT_SPEED + DIG_SPEED (Haste) to nearby player
                java.util.UUID ownerUUID = this.getOwnerUUID();
                Player player = ownerUUID != null ? this.level().getPlayerByUUID(ownerUUID) : null;
                if (player != null && getStateFlag(ID.F.IsMarried) && getStateFlag(ID.F.UseRingEffect) &&
                        getStateMinor(ID.M.NumGrudge) > 0 &&
                        this.distanceToSqr(player) < 256.0D) {
                    int speedLevel = getStateMinor(ID.M.ShipLevel) / 45 + 1;
                    int duration = 80 + getStateMinor(ID.M.ShipLevel);
                    int digLevel = getStateMinor(ID.M.ShipLevel) / 30;
                    player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, duration, speedLevel, false, false));
                    player.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, duration, digLevel, false, false));
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
