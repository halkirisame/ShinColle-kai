package com.lulan.shincolle.ai;

import com.lulan.shincolle.entity.BasicEntityShipHostile;
import com.lulan.shincolle.entity.IShipAttackBase;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.utility.LogHelper;
import com.lulan.shincolle.utility.TargetHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

import java.util.EnumSet;

/**
 * Revenge target goal - sets revenge target as attack target.
 * Ported from EntityAIShipRevengeTarget (setMutexBits: 1)
 */
public class ShipRevengeTargetGoal extends Goal {

    private final IShipAttackBase host;
    private final Mob entity;
    private final java.util.function.Predicate<Entity> targetSelector;
    private final int range;
    private int oldRevengeTime;
    private Entity targetEntity;

    public ShipRevengeTargetGoal(IShipAttackBase host) {
        this.host = host;
        this.entity = (Mob) host;
        if (host instanceof BasicEntityShipHostile) {
            this.targetSelector = new TargetHelper.RevengeSelectorForHostile((Entity) host);
        } else {
            this.targetSelector = new TargetHelper.RevengeSelector((Entity) host);
        }
        int attackRange = Math.round(host.getAttrs().getAttackRange());
        this.range = attackRange < 2
                ? Math.max(2, host.getStateMinor(ID.M.FollowMax) + 2)
                : attackRange;
        this.oldRevengeTime = 0;
        // [PORT] 1.10.2 targetTasks mutex -> 1.20.1 TARGET control flag.
        this.setFlags(EnumSet.of(Goal.Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        if (this.oldRevengeTime != this.host.getEntityRevengeTime() && this.host.getEntityRevengeTarget() != null) {
            Entity revengeTarget = this.host.getEntityRevengeTarget();
            if (this.targetSelector.test(revengeTarget)) {
                this.targetEntity = revengeTarget;
                return true;
            }
        }
        return false;
    }

    @Override
    public void start() {
        LogHelper.debug("DEBUG: revenge target AI: " + this.host
                + " switching target to revenge target=" + this.host.getEntityRevengeTarget()
                + " (overrides range-target selection this tick)");
        this.host.setEntityTarget(this.targetEntity);
        this.oldRevengeTime = this.host.getEntityRevengeTime();
        this.host.setEntityRevengeTarget(null);
    }

    @Override
    public boolean canContinueToUse() {
        Entity target = this.host.getEntityTarget();
        if (!(target instanceof LivingEntity) || !target.isAlive()) {
            return false;
        }

        double rangeSq = this.range * this.range;
        if (this.entity.distanceToSqr(target) > rangeSq) {
            return false;
        }

        return !(target instanceof Player player) || !player.getAbilities().invulnerable;
    }

    @Override
    public void stop() {
        if (this.entity.getTarget() == this.targetEntity) {
            this.entity.setTarget(null);
        }
        this.targetEntity = null;
    }
}
