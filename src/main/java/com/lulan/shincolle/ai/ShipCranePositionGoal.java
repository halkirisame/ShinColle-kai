package com.lulan.shincolle.ai;

import com.lulan.shincolle.ai.domain.action.ActionKind;
import com.lulan.shincolle.ai.domain.crane.CranePositionPlanner;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/** Executes only the crane-position intent supplied by the movement gate. */
public final class ShipCranePositionGoal extends Goal {
    private final Mob host;
    private CranePositionPlanner.State state = CranePositionPlanner.State.INITIAL;

    public ShipCranePositionGoal(Mob host) {
        this.host = host;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public boolean canUse() {
        return !ShipActionGate.blocked(host, ActionKind.MOVEMENT) && ShipMovementGate.cranePosition(host).isPresent();
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void tick() {
        if (ShipActionGate.blocked(host, ActionKind.MOVEMENT)) return;
        ShipMovementGate.cranePosition(host).ifPresent(intent -> {
            CranePositionPlanner.Result planned = CranePositionPlanner.step(state, intent,
                    ShipMovementGate.point(host), host.tickCount);
            state = planned.state();
            ShipMovementExecutor.run(host, planned.plan());
        });
    }

    @Override
    public void stop() {
        state = CranePositionPlanner.State.INITIAL;
        ShipMovementExecutor.run(host, ShipMovementExecutor.GOAL_STOPPED);
    }
}
