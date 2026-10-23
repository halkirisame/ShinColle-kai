package com.lulan.shincolle.ai;

import com.lulan.shincolle.ai.domain.action.ActionKind;
import com.lulan.shincolle.ai.domain.combat.ProjectileTrailDelay;
import com.lulan.shincolle.ai.domain.combat.RuBurstDecision;
import com.lulan.shincolle.ai.domain.combat.RuBurstState;
import com.lulan.shincolle.entity.BasicEntityMount;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.entity.battleship.EntityBattleshipRu;
import com.lulan.shincolle.entity.other.EntityAbyssMissile;
import com.lulan.shincolle.handler.ConfigHandler;
import com.lulan.shincolle.init.ModSounds;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.unitclass.MissileData;
import com.lulan.shincolle.utility.CombatHelper;
import com.lulan.shincolle.utility.ParticleHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/** Adapts the dedicated burst authority without pausing ordinary combat or movement. */
public final class RuBurstAttackGate {
    private RuBurstAttackGate() { }

    public static boolean supported(Entity host) { return host instanceof EntityBattleshipRu; }

    public static boolean busy(Entity host) {
        if (host instanceof BasicEntityMount mount) host = mount.getHost();
        return supported(host) && ((BasicEntityShip) host).shipCombatState().ruBurst().state().busy();
    }

    private static RuBurstDecision.Permission permission(BasicEntityShip ship) {
        return new RuBurstDecision.Permission(BasicEntityShip.stopAI, ship.isNoAi(), ship.isAlive(), ship.isRemoved(),
                ship.getIsSitting(), ShipMovementGate.craneBusy(ship), !ShipActionGate.blocked(ship, ActionKind.FIRING));
    }

    public static boolean start(BasicEntityShip ship, Entity target) {
        if (target == null) return false;
        return start(ship, target.getX(), target.getY(), target.getZ(), target.getBbHeight() * .35D,
                target.getBbWidth(), (float) Math.sqrt(ship.distanceToSqr(target)), target, null);
    }

    public static boolean start(BasicEntityShip ship, BlockPos target) {
        if (target == null) return false;
        return start(ship, target.getX(), target.getY(), target.getZ(), .5D, 0F,
                (float) Math.sqrt(ship.distanceToSqr(target.getX() + .5D, target.getY() + .5D, target.getZ() + .5D)), null, target);
    }

    private static boolean start(BasicEntityShip ship, double x, double y, double z, double height, float width,
                                 float distance, Entity target, BlockPos block) {
        if (!supported(ship) || ship.level().isClientSide()) return false;
        RuBurstRuntime runtime = ship.shipCombatState().ruBurst();
        RuBurstDecision.Permission permission = permission(ship);
        RuBurstState.Aim aim = aim(x, y + height, z);
        RuBurstDecision.Plan preflight = RuBurstDecision.start(runtime.state(), ship.getLevel(), aim, permission, true);
        if (preflight.effects().contains(RuBurstDecision.Effect.REJECT)) {
            runtime.apply(preflight);
            return false;
        }
        // Keep the existing inventory-refill and configured consumption semantics, once per burst.
        if (!ship.decrAmmoNum(1, ship.getAmmoConsumption())) {
            runtime.apply(RuBurstDecision.start(runtime.state(), ship.getLevel(), aim, permission, false));
            return false;
        }
        ship.addShipExp(ConfigHandler.expGain[2]);
        ship.decrGrudgeNum(ConfigHandler.consumeGrudgeAction[1]);
        ship.decrMorale(2);
        ship.setCombatTick(ship.tickCount);
        if (ship.getRandom().nextFloat() <= CombatHelper.calcMissRate(ship, distance)) {
            double[] missed = CombatHelper.calcMissAimPoint(x, (float) y, z, ship.getX(), ship.getZ(), width,
                    ship.getRandom().nextFloat(), ship.getRandom().nextFloat(), ship.getRandom().nextFloat());
            aim = aim(missed[0], missed[1] + height, missed[2]);
            ParticleHelper.spawnAttackTextParticle(ship, 0);
        }
        runtime.apply(RuBurstDecision.start(runtime.state(), ship.getLevel(), aim, permission, true));
        runtime.dimension = Optional.of(ship.level().dimension());
        project(ship);
        ship.playSound(ModSounds.SHIP_HITMETAL.get(), (float) ConfigHandler.volumeAttack(), 1F);
        if (ship.getRandom().nextInt(10) > 7) ship.playVoice(ship.getCustomSound(1, ship), (float) ConfigHandler.volumeShip(), ship.getVoicePitch());
        ship.applyParticleAtAttacker(2, target == null ? ship : target, ship);
        if (ConfigHandler.canFlare()) {
            if (target != null) ship.flareTarget(target);
            else ship.flareTarget(block);
        }
        return true;
    }

    private static RuBurstState.Aim aim(double x, double y, double z) {
        BlockPos floor = BlockPos.containing(x, y, z);
        return new RuBurstState.Aim(floor.getX(), floor.getY(), floor.getZ());
    }

    public static void observe(BasicEntityShip ship) {
        if (!supported(ship) || ship.level().isClientSide()) return;
        RuBurstRuntime runtime = ship.shipCombatState().ruBurst();
        runtime.apply(RuBurstDecision.observe(runtime.state(), permission(ship),
                runtime.dimension.filter(ship.level().dimension()::equals).isPresent()));
        project(ship);
    }

    public static void tick(BasicEntityShip ship) {
        if (!supported(ship) || ship.level().isClientSide()) return;
        RuBurstRuntime runtime = ship.shipCombatState().ruBurst();
        RuBurstDecision.Plan plan = RuBurstDecision.tick(runtime.state(), ship.tickCount, permission(ship),
                runtime.dimension.filter(ship.level().dimension()::equals).isPresent());
        runtime.apply(plan);
        plan.shot().ifPresent(aim -> emit(ship, aim));
        project(ship);
    }

    private static void emit(BasicEntityShip ship, RuBurstState.Aim aim) {
        MissileData md = ship.getMissileData(2);
        Vec3 origin = new Vec3(ship.getX(), (float) ship.getY() + ship.getBbHeight() * .4F, ship.getZ());
        EntityAbyssMissile missile = EntityAbyssMissile.createBuiltInLaunch(ship.level(), ship, md.type, 1,
                ship.getAttackBaseDamage(2, null), .15F, origin, aim.x() + ship.getRandom().nextFloat() * 8F - 4F,
                aim.y() + ship.getRandom().nextFloat() * 4F - 2F, aim.z() + ship.getRandom().nextFloat() * 8F - 4F,
                160, .35F, md.vel0, md.accY1, md.accY2, ProjectileTrailDelay.NONE);
        ship.level().addFreshEntity(missile);
        ship.applySoundAtAttacker(2, null);
    }

    public static void cancel(BasicEntityShip ship) {
        if (!supported(ship) || ship.level().isClientSide()) return;
        ship.shipCombatState().ruBurst().cancel();
        project(ship);
    }

    public static void loaded(BasicEntityShip ship) {
        if (!supported(ship)) return;
        ship.shipCombatState().ruBurst().clear();
        ship.setStateEmotion(ID.S.Phase, 0, false);
    }

    /** A destroyed or transferred entity cannot carry an evaluation mark into another lifetime. */
    public static void ended(BasicEntityShip ship, RuBurstDecision.Reason reason) {
        if (!supported(ship) || ship.level().isClientSide()) return;
        RuBurstRuntime runtime = ship.shipCombatState().ruBurst();
        runtime.clear();
        runtime.apply(RuBurstDecision.cancel(runtime.state(), java.util.Set.of(reason)));
        project(ship);
    }

    private static void project(BasicEntityShip ship) {
        int phase = busy(ship) ? 1 : 0;
        if (ship.getStateEmotion(ID.S.Phase) != phase) ship.setStateEmotion(ID.S.Phase, phase, true);
    }
}
