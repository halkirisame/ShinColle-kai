package com.lulan.shincolle.ai;

import com.lulan.shincolle.entity.BasicEntityMount;
import com.lulan.shincolle.entity.IShipAttackBase;
import com.lulan.shincolle.network.ModNetworking;
import com.lulan.shincolle.network.S2CSpawnParticlePacket;
import com.lulan.shincolle.reference.ID;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * Skill attack goal - delegates to entity's updateSkillAttack method.
 * Active when ID.S.Phase != 0.
 * Ported from EntityAIShipSkillAttack (setMutexBits: 15)
 */
public class ShipSkillAttackGoal extends Goal {

    private final IShipAttackBase host;
    private final Mob entity;

    public ShipSkillAttackGoal(IShipAttackBase host) {
        this.host = host;
        this.entity = (Mob) host;
        // mutexBits 15 = all flags
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK, Goal.Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        if (this.host.getIsSitting() || this.host.getStateMinor(ID.M.CraneState) > 0) {
            // reset phase
            if (this.host.getStateEmotion(ID.S.Phase) > 0) {
                this.host.setStateEmotion(ID.S.Phase, 0, true);
            }
            return false;
        }

        if (this.host.getIsRiding()) {
            if (this.entity.getVehicle() instanceof BasicEntityMount) {
                return false;
            }
        }

        return this.host.getStateEmotion(ID.S.Phase) > 0;
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    /**
     * [BETA STOPGAP] Announce a skill activation visually.
     * <p>
     * The original gives every skill ship its own effect (Tenryuu 9 sites,
     * Tatsuta 9, Nagato 5, Yamato 5, ...), none of which were ported - skills
     * fire silently. Until those per-ship effects are restored, emit one
     * generic burst so a skill is visible at all. Types 2 and 8 are what the
     * port currently renders as an explosion and heavy smoke.
     */
    @Override
    public void start() {
        if (this.entity.level().isClientSide()) {
            return;
        }
        ModNetworking.sendToAllTracking(
                new S2CSpawnParticlePacket((byte) 2, this.entity.getId(), null), this.entity);
        ModNetworking.sendToAllTracking(
                new S2CSpawnParticlePacket((byte) 8, this.entity.getId(), null), this.entity);
    }

    @Override
    public void stop() {
    }

    @Override
    public void tick() {
        this.host.updateSkillAttack(this.host.getEntityTarget());
    }
}
