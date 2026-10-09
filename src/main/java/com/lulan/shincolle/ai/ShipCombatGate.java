package com.lulan.shincolle.ai;

import com.lulan.shincolle.ai.command.ShipCommandStateAdapter;
import com.lulan.shincolle.ai.domain.TargetHandle;
import com.lulan.shincolle.ai.domain.TargetLock;
import com.lulan.shincolle.ai.domain.action.ActionConstraints;
import com.lulan.shincolle.ai.domain.combat.CombatFacts;
import com.lulan.shincolle.ai.domain.combat.CombatIntent;
import com.lulan.shincolle.ai.domain.combat.CombatIntentResolver;
import com.lulan.shincolle.ai.domain.combat.HoldFireReason;
import com.lulan.shincolle.ai.domain.ShipAiCompatibilityRules;
import com.lulan.shincolle.ai.domain.combat.AttackPlan;
import com.lulan.shincolle.ai.domain.combat.CombatContext;
import com.lulan.shincolle.ai.domain.combat.CombatDecision;
import com.lulan.shincolle.ai.domain.combat.CombatLoadout;
import com.lulan.shincolle.ai.domain.combat.CombatTimingState;
import com.lulan.shincolle.ai.domain.combat.WeaponChannel;
import com.lulan.shincolle.ai.domain.combat.WeaponReadiness;
import com.lulan.shincolle.ai.observation.MinecraftTargetResolver;
import com.lulan.shincolle.entity.BasicEntityMount;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.entity.BasicEntityShipHostile;
import com.lulan.shincolle.entity.IShipAircraftAttack;
import com.lulan.shincolle.entity.IShipAttackBase;
import com.lulan.shincolle.entity.IShipCannonAttack;
import com.lulan.shincolle.handler.ConfigHandler;
import com.lulan.shincolle.reference.ID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;

import java.util.Optional;
import java.util.Set;

/**
 * Single entry point through which attack goals read whether their host may fire and at what.
 * It gathers the facts from the host on every call. A ship reads its own target lock, a mount
 * the lock of the ship it carries, and a hostile ship its own lock without action constraints.
 * Only the NEW authority uses it.
 */
public final class ShipCombatGate {
    private ShipCombatGate() { }

    /**
     * Whether the host reads combat through this gate: under NEW, and only while its lock owner
     * has a target authority. A host whose goals were registered under LEGACY keeps the target
     * its LEGACY goals chose until the goals are registered again, as {@code getEntityTarget} does.
     */
    public static boolean active(Entity host) {
        if (!ShipCommandStateAdapter.isNew()) return false;
        BasicEntityShip ship = host instanceof BasicEntityShip s ? s
                : host instanceof BasicEntityMount mount ? mount.getHost() : null;
        return ship != null ? ship.hasTargetAuthority()
                : host instanceof BasicEntityShipHostile hostile && hostile.hasTargetAuthority();
    }

    /** The intent, and the locked entity while the intent engages it. */
    public record Engagement(CombatIntent intent, Entity target) {
        public boolean engaged() {
            return this.intent instanceof CombatIntent.Engage;
        }

        public Set<HoldFireReason> holdReasons() {
            return this.intent instanceof CombatIntent.HoldFire hold ? hold.reasons() : Set.of();
        }
    }

    public static Engagement engagement(Entity host) {
        BasicEntityShip ship = host instanceof BasicEntityShip s ? s
                : host instanceof BasicEntityMount mount ? mount.getHost() : null;
        Entity lockOwner = ship != null ? ship : host instanceof BasicEntityShipHostile ? host : null;
        Optional<TargetLock> lock = ship != null ? ship.currentTargetLock()
                : host instanceof BasicEntityShipHostile hostile ? hostile.currentTargetLock() : Optional.empty();
        Entity target = null;
        if (lock.isPresent() && lockOwner.level() instanceof ServerLevel level) {
            target = new MinecraftTargetResolver(level).resolve(lock.get().target()).orElse(null);
        }
        if (target == null) lock = Optional.empty();
        Optional<ActionConstraints> constraints = ship == null ? Optional.empty()
                : Optional.of(ShipActionGate.constraints(ship,
                        host instanceof BasicEntityMount mount && mount.getControllingPassenger() instanceof Player));
        IShipAttackBase base = (IShipAttackBase) host;
        CombatIntent intent = CombatIntentResolver.resolve(new CombatFacts(lock, constraints,
                base.getIsSitting(), base.getIsRiding() && host.getVehicle() instanceof BasicEntityMount));
        return new Engagement(intent, intent instanceof CombatIntent.Engage ? target : null);
    }

    /** The target the host is engaged with, when it reads combat through this gate and is engaged. */
    public static Optional<TargetHandle> engagedTarget(Entity host) {
        if (!active(host)) return Optional.empty();
        Engagement engagement = engagement(host);
        return engagement.engaged() && engagement.target() != null
                ? Optional.of(ShipCommandStateAdapter.handle(engagement.target())) : Optional.empty();
    }

    /** Adds an attack goal's weapons to its host's loadout; every host that has one records it. */
    static void carry(Object host, WeaponChannel... channels) {
        if (host instanceof ShipCombatHost combatHost) combatHost.shipCombatState().addWeapons(Set.of(channels));
    }

    static ShipCombatState state(Entity host) {
        return host instanceof ShipCombatHost combatHost ? combatHost.shipCombatState() : null;
    }

    /** The decision for the host this tick, from its stored timer. */
    public static AttackPlan plan(Mob host, Engagement engagement) {
        ShipCombatState state = state(host);
        if (state == null || !engagement.engaged()) return AttackPlan.HOLD;
        return CombatDecision.decide(context(host, engagement, state.timing(host.tickCount), state.loadout(),
                host.getSensing().hasLineOfSight(engagement.target())));
    }

    static CombatContext context(Mob host, Engagement engagement, CombatTimingState timing, CombatLoadout loadout,
                                 boolean onSight) {
        IShipCannonAttack ship = (IShipCannonAttack) host;
        Entity target = engagement.target();
        double distanceSq = target == null ? Double.MAX_VALUE : host.distanceToSqr(target);
        double meleeDistanceSq = target == null ? Double.MAX_VALUE
                : host.distanceToSqr(target.getX(), target.getBoundingBox().minY, target.getZ());
        float range = ship.getAttrs().getAttackRange();
        double reach = host.getBbWidth() * host.getBbWidth() * 16F;
        return new CombatContext(engagement.intent(), timing, loadout, distanceSq, meleeDistanceSq,
                range * range, reach, onSight, aimTime(ship), readiness(host), ship.getStateFlag(ID.F.UseMelee),
                ConfigHandler.engageDistance() * 0.01D, host.tickCount);
    }

    static int aimTime(IShipAttackBase host) {
        return ShipAiCompatibilityRules.aimTime(host.getLevel());
    }

    static WeaponReadiness readiness(Mob host) {
        IShipCannonAttack ship = (IShipCannonAttack) host;
        boolean cannons = ship.getAttackType(ID.F.AtkType_Light) && ship.getStateFlag(ID.F.UseAmmoLight)
                && ship.hasAmmoLight()
                || ship.getAttackType(ID.F.AtkType_Heavy) && ship.getStateFlag(ID.F.UseAmmoHeavy)
                && ship.hasAmmoHeavy();
        boolean aircraft = false;
        boolean airLight = false;
        boolean airHeavy = false;
        if (host instanceof IShipAircraftAttack carrier) {
            airLight = carrier.hasAmmoLight() && carrier.hasAirLight();
            airHeavy = carrier.hasAmmoHeavy() && carrier.hasAirHeavy();
            aircraft = carrier.getAttackType(ID.F.AtkType_AirLight) && carrier.getStateFlag(ID.F.UseAirLight)
                    && airLight
                    || carrier.getAttackType(ID.F.AtkType_AirHeavy) && carrier.getStateFlag(ID.F.UseAirHeavy)
                    && airHeavy;
        }
        return new WeaponReadiness(cannons, ship.useAmmoLight(), ship.hasAmmoLight(),
                ship.useAmmoHeavy(), ship.hasAmmoHeavy(),
                aircraft, ship.getStateFlag(ID.F.UseAirLight), ship.getStateFlag(ID.F.UseAirHeavy), airLight, airHeavy,
                ShipMovementGate.craneBusy(ship), ship.getStateFlag(ID.F.UseMelee), host.isPassenger());
    }
}
