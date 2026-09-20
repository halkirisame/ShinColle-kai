package com.lulan.shincolle.ai;

import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.utility.TargetHelper;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/** Holds the TARGET mutex below revenge and above automatic acquisition. */
public final class ShipManualTargetGoal extends Goal {
    private final BasicEntityShip host;
    private final int range;
    private Entity targetEntity;

    public ShipManualTargetGoal(BasicEntityShip host) {
        this.host = host;
        int attackRange = Math.round(host.getAttrs().getAttackRange());
        this.range = attackRange < 2 ? Math.max(2, host.getStateMinor(ID.M.FollowMax) + 2) : attackRange;
        this.setFlags(EnumSet.of(Goal.Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        return !this.host.isOrderedToSit() && !this.host.getStateFlag(ID.F.NoFuel)
                && TargetHelper.isValidManualTarget(this.host)
                && this.host.distanceToSqr(this.host.getManualTarget()) <= (double) this.range * this.range;
    }

    @Override
    public void start() {
        this.targetEntity = this.host.getManualTarget();
        this.host.setEntityTarget(this.targetEntity);
    }

    @Override
    public boolean canContinueToUse() {
        return this.targetEntity == this.host.getManualTarget() && this.canUse();
    }

    @Override
    public void stop() {
        if (this.host.getTarget() == this.targetEntity) {
            this.host.setEntityTarget(null);
        }
        this.targetEntity = null;
    }
}
