package com.lulan.shincolle.entity.other;

import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.entity.BasicEntitySummon;
import com.lulan.shincolle.entity.IShipAttackBase;
import com.lulan.shincolle.entity.IShipEmotion;
import com.lulan.shincolle.equip.ShipOnHitEffects;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.unitclass.Attrs;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;

import java.util.EnumSet;
import java.util.Objects;

/**
 * Rensouhou summoned turret entity.
 * Spawned by Shimakaze to provide additional firepower.
 * Ported from 1.10.2 EntityRensouhou.
 * <p>
 * Implements IShipEmotion (with no-op stubs, same pattern as
 * BasicEntityAirplane) because ModelRensouhou/ModelRensouhouS cast their
 * entity to IShipEmotion unconditionally in setupAnim() - without this the
 * cast throws every render frame and the entity silently never renders.
 */
public class EntityRensouhou extends BasicEntitySummon implements IShipEmotion {

    public EntityRensouhou(EntityType<? extends EntityRensouhou> type, Level level) {
        super(type, level);
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return EntityDimensions.fixed(0.3F, 0.7F);
    }

    @Override
    protected void setAIList() {
        this.clearAITasks();
        this.clearAITargetTasks();

        // rensouhou attack AI: chase target and attack when in range
        EntityRensouhou self = this;
        this.goalSelector.addGoal(1, new Goal() {
            private int attackCooldown = 0;
            private int pathfindCooldown = 0;

            {
                this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
            }

            @Override
            public boolean canUse() {
                LivingEntity target = self.getTarget();
                return target != null && target.isAlive() && self.numAmmoLight > 0;
            }

            @Override
            public boolean canContinueToUse() {
                return canUse();
            }

            @Override
            public void tick() {
                Entity target = self.getEntityTarget();
                if (target == null) return;

                self.getLookControl().setLookAt(target, 30.0F, 30.0F);

                // pathfind toward target every 10 ticks
                if (--pathfindCooldown <= 0) {
                    pathfindCooldown = 10;
                    self.getNavigation().moveTo(target, 1.0D);
                }

                // attack when in range (5 blocks)
                double distSq = self.distanceToSqr(target);
                if (distSq <= 25.0D) {
                    if (--attackCooldown <= 0) {
                        attackCooldown = 20;
                        self.attackTarget(target);
                    }
                }

                // re-acquire target from host if current target died
                if (!target.isAlive() && self.host != null) {
                    LivingEntity newTarget = self.getTarget();
                    if (newTarget != null && newTarget.isAlive()) {
                        self.setEntityTarget(newTarget);
                    }
                }
            }
        });
    }

    @Override
    protected void returnSummonResource() {
        if (this.host instanceof BasicEntityShip ship) {
            // return remaining ammo to host
            int ammoConsumption = ship.getAmmoConsumption();
            if (this.numAmmoLight > 0) {
                ship.setStateMinor(ID.M.NumAmmoLight,
                        ship.getStateMinor(ID.M.NumAmmoLight) + this.numAmmoLight * ammoConsumption);
            }
        }
    }

    @Override
    public void initAttrs(IShipAttackBase host, Entity target, int scaleLevel, float... par2) {
        this.host = host;
        this.setScaleLevel(scaleLevel);

        // without this the attack goal's canUse() (self.getTarget() != null)
        // never passes and the turret just sits there
        if (target instanceof LivingEntity livingTarget) {
            this.setTarget(livingTarget);
        }

        if (host instanceof BasicEntityShip ship) {
            // position near host
            this.setPos(ship.getX(), ship.getY(), ship.getZ());

            // copy attrs from host
            this.shipAttrs = Attrs.copyAttrs(ship.getAttrs());
            this.shipAttrs.setAttrsBuffed(ID.Attrs.HP,
                    10F + ship.getAttrs().getAttrsBuffed(ID.Attrs.HP) * 0.05F);
            this.shipAttrs.setAttrsBuffed(ID.Attrs.ATK_L,
                    ship.getAttrs().getAttackDamage() * 0.5F);
            this.shipAttrs.setAttrsBuffed(ID.Attrs.MOV,
                    ship.getAttrs().getMoveSpeed());

            // apply to entity
            Objects.requireNonNull(this.getAttribute(Attributes.MAX_HEALTH))
                    .setBaseValue(this.shipAttrs.getAttrsBuffed(ID.Attrs.HP));
            Objects.requireNonNull(this.getAttribute(Attributes.MOVEMENT_SPEED))
                    .setBaseValue(this.shipAttrs.getAttrsBuffed(ID.Attrs.MOV));

            if (this.getHealth() < this.getMaxHealth()) {
                this.setHealth(this.getMaxHealth());
            }

            this.numAmmoLight = 6;
            this.postInit();
            this.setAIList();
        }
    }

    /**
     * Simple attack: deal light damage to target.
     */
    public boolean attackTarget(Entity target) {
        if (this.numAmmoLight <= 0) return false;
        this.numAmmoLight--;

        float atk = this.shipAttrs.getAttackDamage();
        if (target instanceof LivingEntity livingTarget) {
            boolean hurt = livingTarget.hurt(this.damageSources().mobAttack(this), atk);
            Entity hostEntity = this.getHostEntity();
            if (hurt && hostEntity instanceof LivingEntity livingHost) {
                ShipOnHitEffects.dispatch(livingHost, target, atk);
            }
            return hurt;
        }
        return false;
    }

    @Override
    public boolean canFly() {
        return false;
    }

    @Override
    public boolean isJumping() {
        return false;
    }

    @Override
    public float getMoveSpeed() {
        return 0;
    }

    @Override
    public float getJumpSpeed() {
        return 0;
    }

    // ========== IShipEmotion (no-op stubs, mirrors BasicEntityAirplane) ==========

    @Override
    public int getStateEmotion(int id) {
        return 0;
    }

    @Override
    public void setStateEmotion(int id, int value, boolean sync) {
    }

    @Override
    public int getStateTimer(int id) {
        return 0;
    }

    @Override
    public void setStateTimer(int id, int value) {
    }

    @Override
    public int getFaceTick() {
        return 0;
    }

    @Override
    public void setFaceTick(int par1) {
    }

    @Override
    public int getHeadTiltTick() {
        return 0;
    }

    @Override
    public void setHeadTiltTick(int par1) {
    }

    @Override
    public int getAttackTick() {
        return 0;
    }

    @Override
    public void setAttackTick(int par1) {
    }

    @Override
    public int getAttackTick2() {
        return 0;
    }

    @Override
    public void setAttackTick2(int par1) {
    }

    @Override
    public int getDeathTick() {
        return 0;
    }

    @Override
    public void setDeathTick(int par1) {
    }

    @Override
    public float getModelRotate(int par1) {
        return 0;
    }

    @Override
    public void setModelRotate(int par1, float par2) {
    }

    @Override
    public int getTickExisted() {
        return this.tickCount;
    }

    @Override
    public float getSwingTime(float partialTick) {
        return 0;
    }

    @Override
    public boolean getIsRiding() {
        return this.isPassenger();
    }

    @Override
    public boolean getIsSprinting() {
        return this.isSprinting();
    }

    @Override
    public boolean getIsSitting() {
        return false;
    }

    @Override
    public boolean getIsSneaking() {
        return this.isShiftKeyDown();
    }

    @Override
    public boolean getIsLeashed() {
        return this.isLeashed();
    }

    @Override
    public void setEntitySit(boolean sit) {
    }

    @Override
    public int getRidingState() {
        return 0;
    }

    @Override
    public void setRidingState(int state) {
    }

    @Override
    public RandomSource getRand() {
        return this.random;
    }

    @Override
    public double getShipDepth(int type) {
        return 0;
    }

    // ========== IShipFlags ==========

    @Override
    public int getStateMinor(int id) {
        return 0;
    }

    @Override
    public void setStateMinor(int state, int par1) {
    }

    @Override
    public boolean getStateFlag(int flag) {
        return false;
    }

    @Override
    public void setStateFlag(int id, boolean flag) {
    }

    @Override
    public void setUpdateFlag(int id, boolean value) {
    }

    @Override
    public boolean getUpdateFlag(int id) {
        return false;
    }
}
