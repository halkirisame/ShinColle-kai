package com.lulan.shincolle.ai;

import com.lulan.shincolle.ai.domain.AircraftPriorityTarget;
import com.lulan.shincolle.ai.domain.AircraftTargetPolicy;
import com.lulan.shincolle.ai.domain.OwnerAssistObservation;
import com.lulan.shincolle.ai.domain.RecentOwnerAttack;
import com.lulan.shincolle.ai.domain.TargetLock;
import com.lulan.shincolle.ai.domain.TargetLockReducer;
import com.lulan.shincolle.ai.domain.TargetSource;
import com.lulan.shincolle.ai.domain.action.ActionKind;
import com.lulan.shincolle.ai.observation.MinecraftTargetResolver;
import com.lulan.shincolle.entity.BasicEntityAirplane;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.handler.ShipOwnerAssistHandler;
import com.lulan.shincolle.utility.TargetHelper;
import com.lulan.shincolle.utility.TeamHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.Optional;

/** Minecraft observation/projection boundary; priority and age remain in the pure domain. */
public final class ShipOwnerAssistGate {
    private ShipOwnerAssistGate() { }

    public static Optional<OwnerAssistObservation> observe(BasicEntityShip ship, Entity observer, double retentionRange) {
        if (!(observer.level() instanceof ServerLevel level) || ship.getOwnerUUID() == null) return Optional.empty();
        Optional<RecentOwnerAttack> latest = ShipOwnerAssistHandler.latest(level, ship.getOwnerUUID());
        return latest.map(attack -> {
            ServerPlayer owner = ship.getOwner() instanceof ServerPlayer player ? player : null;
            Entity target = new MinecraftTargetResolver(level).resolve(attack.target()).orElse(null);
            boolean ownerAvailable = availableOwner(ship, owner);
            boolean sameDimension = owner != null && owner.level() == level && ship.level() == level;
            boolean eligible = target != null && !target.isInvulnerable() && !TeamHelper.checkIsAlly(ship, target)
                    && new TargetHelper.RevengeSelector(ship).test(target);
            boolean actionAllowed = !ShipActionGate.blocked(ship, ActionKind.TARGET_ACQUISITION)
                    && !ship.getIsSitting() && !ShipMovementGate.craneBusy(ship);
            return new OwnerAssistObservation(attack, level.getServer().getTickCount(),
                    target == null ? Double.POSITIVE_INFINITY : observer.distanceToSqr(target), retentionRange,
                    ownerAvailable, sameDimension && target != null, eligible, actionAllowed);
        });
    }

    public static Optional<AircraftPriorityTarget> aircraft(BasicEntityAirplane aircraft) {
        if (!(aircraft.getHost() instanceof BasicEntityShip ship) || !ship.hasTargetAuthority()
                || !ship.isAlive() || ship.level() != aircraft.level() || !aircraft.activeForOwnerAssist()) {
            return Optional.empty();
        }
        if (!(ship.getOwner() instanceof ServerPlayer owner) || !availableOwner(ship, owner)
                || owner.level() != aircraft.level()) return Optional.empty();
        // Only the typed, engaged MANUAL authority is a candidate, never a raw command or AUTO/REVENGE.
        Optional<TargetLock> manual = ship.currentTargetLock().filter(lock -> lock.source() == TargetSource.MANUAL)
                .filter(lock -> ShipCombatGate.engagement(ship).engaged())
                .filter(lock -> TargetHelper.isValidAuthorityManualTarget(ship));
        return AircraftTargetPolicy.select(true, manual, observe(ship, aircraft, aircraft.getAttrs().getAttackRange()));
    }

    /** Consumers must not see a stale assist between the authority goal's scheduled observations. */
    public static Optional<TargetLock> current(BasicEntityShip ship, Optional<TargetLock> lock, double retentionRange) {
        if (lock.filter(target -> target.source() == TargetSource.OWNER_ASSIST).isEmpty()) return lock;
        return TargetLockReducer.projectCurrent(lock, observe(ship, ship, retentionRange));
    }

    private static boolean availableOwner(BasicEntityShip ship, ServerPlayer owner) {
        return owner != null && owner.isAlive() && !owner.isRemoved() && !owner.hasDisconnected()
                && owner.getUUID().equals(ship.getOwnerUUID())
                && owner.level().getPlayerByUUID(owner.getUUID()) == owner;
    }
}
