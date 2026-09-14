package com.lulan.shincolle.ai;

import com.lulan.shincolle.entity.BasicEntityShipHostile;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.RandomPos;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * Random wander goal for hostile ship entities.
 * <p>
 * [PORT] 1.10.2 -> 1.20.1: restores hostile idle movement behavior using
 * ship-specific navigation rather than PathfinderMob goals.
 */
public class ShipHostileWanderGoal extends Goal {

    private final BasicEntityShipHostile ship;
    private final int rangeXZ;
    private final int rangeY;
    private final double speed;
    private double targetX;
    private double targetY;
    private double targetZ;

    public ShipHostileWanderGoal(BasicEntityShipHostile ship, int rangeXZ, int rangeY, double speed) {
        this.ship = ship;
        this.rangeXZ = rangeXZ;
        this.rangeY = rangeY;
        this.speed = speed;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK, Goal.Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        if (this.ship.isPassenger()) {
            return false;
        }
        if (this.ship.getRandom().nextInt(180) != 0) {
            return false;
        }
        Vec3 target = RandomPos.generateRandomPos(() -> {
            BlockPos direction = RandomPos.generateRandomDirection(
                    this.ship.getRandom(), this.rangeXZ, this.rangeY);
            BlockPos candidate = this.ship.blockPosition().offset(direction);
            return this.ship.level().hasChunkAt(candidate)
                    && this.ship.getNavigation().isStableDestination(candidate)
                    ? candidate : null;
        }, ignored -> 0D);
        if (target == null) {
            return false;
        }
        this.targetX = target.x;
        this.targetY = target.y;
        this.targetZ = target.z;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return !ship.getNavigation().isDone();
    }

    @Override
    public void start() {
        this.ship.getNavigation().moveTo(this.targetX, this.targetY, this.targetZ, this.speed);
    }
}
