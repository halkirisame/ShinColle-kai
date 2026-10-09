package com.lulan.shincolle.ai;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/** Execution shell for the server's already chosen continuous skill sequence. */
public final class ShipSpecialAttackGoal extends Goal {
    private final Mob host;

    public ShipSpecialAttackGoal(Mob host) {
        this.host = host;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        return ShipSkillAttackGate.running(host);
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        ShipSkillAttackGate.stopPath(host);
    }

    @Override
    public void tick() {
        ShipSkillAttackGate.tick(host);
    }

    @Override
    public void stop() {
        ShipSkillAttackGate.cancel(host);
    }
}
