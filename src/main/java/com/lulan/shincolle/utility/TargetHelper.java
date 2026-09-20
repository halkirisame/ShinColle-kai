package com.lulan.shincolle.utility;

import com.lulan.shincolle.ai.observation.MinecraftTargetClassificationAdapter;
import com.lulan.shincolle.ai.domain.TargetObservationProfiler;
import com.lulan.shincolle.ai.domain.TargetPredicateEvaluator;
import com.lulan.shincolle.ai.domain.TargetPredicateKind;
import com.lulan.shincolle.ai.domain.TargetPredicatePolicy;
import com.lulan.shincolle.entity.*;
import com.lulan.shincolle.handler.ConfigHandler;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.server.ServerDataManager;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;

import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

/**
 * Target selection helper.
 * <p>
 * Provides Predicate-based selectors for ship AI target acquisition
 * and utility methods for target validation.
 * <p>
 * Ported from 1.10.2 TargetHelper. TeamHelper logic is inlined using
 * IShipOwner.getPlayerUID() for ownership checks.
 */
public class TargetHelper {

    // ========== Sorter ==========

    /**
     * Update AI targets — called every tick on server side.
     * Clears dead, friendly, or expired targets.
     */
    public static void updateTarget(IShipAttackBase host) {
        Entity hostEntity = (Entity) host;

        if (host instanceof BasicEntityShip ship && ship.getManualTarget() != null
                && !isValidManualTarget(ship)) {
            if (ship.getEntityTarget() == ship.getManualTarget()) {
                ship.setEntityTarget(null);
            }
            ship.setManualTarget(null);
        }

        // clear dead or friendly attack target
        Entity atkTarget = host.getEntityTarget();
        if (atkTarget != null) {
            if (!atkTarget.isAlive()) {
                host.setEntityTarget(null);
            } else if (host instanceof BasicEntityShip && checkIsAlly(hostEntity, atkTarget)) {
                host.setEntityTarget(null);
            }
        }

        // clear dead or expired revenge target
        Entity rvgTarget = host.getEntityRevengeTarget();
        if (rvgTarget != null) {
            if (!rvgTarget.isAlive()) {
                host.setEntityRevengeTarget(null);
            } else if (host.getTickExisted() - host.getEntityRevengeTime() > 200) {
                host.setEntityRevengeTarget(null);
            }
        }

        // clear vanilla attack target for hostile ships
        if (host instanceof BasicEntityShipHostile hostile) {
            LivingEntity vanillaTarget = hostile.getTarget();
            if (vanillaTarget != null) {
                if (!vanillaTarget.isAlive()) {
                    hostile.setTarget(null);
                } else if (checkSameOwner(hostEntity, vanillaTarget)) {
                    hostile.setTarget(null);
                }
            }
        }

        // clear invisible target every 64 ticks if no detection equipment
        if ((host.getTickExisted() & 63) == 0) {
            Entity target = host.getEntityTarget();
            if (target != null && target.isInvisible()) {
                if (host.getStateMinor(ID.M.LevelFlare) < 1
                        && host.getStateMinor(ID.M.LevelSearchlight) < 1) {
                    host.setEntityTarget(null);
                }
            }
        }
    }

    /** Command validity excludes range and elapsed time; the goal handles range separately. */
    /**
     * Whether a manual designation is still worth holding.
     *
     * <p>The automatic selection predicate ({@link Selector}) must not gate this. A manual
     * designation is the player overriding automatic selection, so applying the automatic
     * predicate to it cancels exactly the orders that only a player can give: a neutral mob,
     * or a player while {@code shipAttackPlayer} is 0. Those orders are accepted at the
     * pointer and were then dropped on the next tick, so the ship never fired.
     *
     * <p>What remains are the conditions the order itself refuses in
     * {@code C2SGUIInputPacket#handleAttackTarget}, re-checked every tick because a target can
     * become invulnerable or join the owner's team after the order was given.
     */
    public static boolean isValidManualTarget(BasicEntityShip ship) {
        Entity target = ship.getManualTarget();
        return ship.isAlive() && target instanceof LivingEntity && target.isAlive() && !target.isRemoved()
                && target.level() == ship.level()
                && !isEntityInvulnerable(target)
                && !checkSameOwner(ship, target)
                && !checkIsAlly(ship, target);
    }

    // ========== Selector (friendly ship target) ==========

    /**
     * Set revenge target for all friendly ships within range of a player.
     */
    public static void setRevengeTargetAroundPlayer(Player player, double dist, Entity target) {
        if (player == null || target == null)
            return;

        AABB box = player.getBoundingBox().inflate(dist);
        List<BasicEntityShip> ships = player.level().getEntitiesOfClass(BasicEntityShip.class, box);

        for (BasicEntityShip ship : ships) {
            if (!ship.equals(target) && checkSameOwner(player, ship)) {
                ship.setEntityRevengeTarget(target);
                ship.setEntityRevengeTime();
            }
        }
    }

    // ========== RevengeSelector (friendly ship revenge) ==========

    /**
     * Set revenge target for all hostile ships within range.
     */
    public static void setRevengeTargetAroundHostileShip(BasicEntityShipHostile host, double dist, Entity target) {
        if (host == null || target == null)
            return;

        AABB box = host.getBoundingBox().inflate(dist);
        List<BasicEntityShipHostile> ships = host.level().getEntitiesOfClass(BasicEntityShipHostile.class, box);

        for (BasicEntityShipHostile ship : ships) {
            ship.setEntityRevengeTarget(target);
            ship.setEntityRevengeTime();
        }
    }

    // ========== SelectorForHostile (mob ship target) ==========

    /**
     * Check if an entity should never be attacked (projectiles, hangings, etc.).
     */
    public static boolean isEntityInvulnerable(Entity target) {
        if (target == null) {
            return true;
        }

        if (target instanceof Projectile || target instanceof AreaEffectCloud) {
            return true;
        }

        // [PORT] 1.10.2 -> 1.20.1: preserve server-side unattackable class list.
        if (!target.level().isClientSide()) {
            return checkUnattackTargetList(target);
        }

        return false;
    }

    // ========== RevengeSelectorForHostile ==========

    /**
     * Check if target class is in server-side unattackable class list.
     */
    public static boolean checkUnattackTargetList(Entity target) {
        if (target == null) {
            return false;
        }

        java.util.HashMap<Integer, String> unattackable = ServerDataManager.getUnattackableTargetClass();
        if (unattackable == null) {
            return false;
        }

        String targetClass = target.getClass().getSimpleName();
        return unattackable.containsKey(targetClass.hashCode());
    }

    // ========== updateTarget ==========

    /**
     * Check if target class is in player's custom attack target list.
     */
    public static boolean checkAttackTargetList(Entity host, Entity target) {
        // A custom class may broaden the target type, but it must never bypass
        // current ownership/alliance rules.
        return isAttackTargetClassListed(host, target) && !checkIsAlly(host, target);
    }

    // ========== Revenge propagation ==========

    /**
     * Check if two entities have the same owner (same PlayerUID).
     * Returns true if they belong to the same player or are the same player.
     */
    public static boolean checkSameOwner(Entity a, Entity b) {
        int uidA = getOwnerUID(a);
        int uidB = getOwnerUID(b);

        // both have valid UIDs
        if (uidA > 0 && uidB > 0) {
            return uidA == uidB;
        }

        // if a is the owner of b or b is the owner of a
        if (a instanceof Player player && b instanceof BasicEntityShip ship) {
            return ship.isOwnedBy(player);
        }
        if (b instanceof Player player && a instanceof BasicEntityShip ship) {
            return ship.isOwnedBy(player);
        }

        // check OwnableEntity (vanilla tameable)
        if (b instanceof OwnableEntity ownable) {
            return a.equals(ownable.getOwner());
        }
        if (a instanceof OwnableEntity ownable) {
            return b.equals(ownable.getOwner());
        }

        return false;
    }

    // ========== Utility methods ==========

    /**
     * Check if two entities are allies (same owner or allied team).
     * Delegates to TeamHelper for full team-based alliance checking.
     */
    public static boolean checkIsAlly(Entity a, Entity b) {
        return TeamHelper.checkIsAlly(a, b);
    }

    /**
     * Check if target is banned (hostile) relative to host.
     * Delegates to TeamHelper for team-based ban checking.
     */
    public static boolean checkIsBanned(Entity host, Entity target) {
        return TeamHelper.checkIsBanned(host, target);
    }

    /**
     * Get the owner player UID for an entity.
     * Returns -1 if entity has no owner.
     */
    private static int getOwnerUID(Entity entity) {
        if (entity instanceof IShipOwner owner) {
            return owner.getPlayerUID();
        }
        // players don't have a UID in this system — return -1
        return -1;
    }

    private static boolean isAttackTargetClassListed(Entity host, Entity target) {
        if (target == null || !(host instanceof IShipAttackBase attackHost)) {
            return false;
        }
        java.util.HashMap<Integer, String> targetList =
                ServerDataManager.getPlayerTargetClass(attackHost.getPlayerUID());
        if (targetList == null) {
            return false;
        }
        String targetClass = target.getClass().getSimpleName();
        return targetList.containsKey(targetClass.hashCode());
    }

    private static boolean evaluatePredicate(
            TargetPredicateKind kind,
            Entity host,
            Entity target,
            TargetPredicatePolicy policy) {
        if (target == null) {
            return false;
        }
        long observedAtTick = host == null ? target.tickCount : host.tickCount;
        return TargetPredicateEvaluator.test(kind,
                MinecraftTargetClassificationAdapter.classify(
                        host, target, kind, policy, observedAtTick,
                        TargetObservationProfiler.NOOP).classification().value(),
                policy);
    }

    /**
     * Sort entities by distance from a reference entity (nearest first).
     */
    public static class Sorter implements Comparator<Entity> {
        private final Entity origin;

        public Sorter(Entity origin) {
            this.origin = origin;
        }

        @Override
        public int compare(Entity a, Entity b) {
            double da = this.origin.distanceToSqr(a);
            double db = this.origin.distanceToSqr(b);
            return Double.compare(da, db);
        }
    }

    /**
     * Standard target selector for friendly (tamed) ships.
     * Checks PVP flag, anti-air, anti-sub, on-sight, invisibility, and team.
     */
    public static class Selector implements Predicate<Entity> {
        protected final Entity host;
        protected boolean isPVP;
        protected boolean isAA;
        protected boolean isASM;

        public Selector(Entity host) {
            this.host = host;
        }

        @Override
        public boolean test(Entity target) {
            // update flags from host
            if (host instanceof BasicEntityShip ship) {
                this.isPVP = ship.getStateFlag(ID.F.PVPFirst);
                this.isAA = ship.getStateFlag(ID.F.AntiAir);
                this.isASM = ship.getStateFlag(ID.F.AntiSS);
            } else {
                this.isPVP = false;
                this.isAA = false;
                this.isASM = false;
            }

            TargetPredicatePolicy policy = new TargetPredicatePolicy(
                    this.isPVP,
                    this.isAA,
                    this.isASM,
                    ConfigHandler.shipAttackPlayer(),
                    false);
            return evaluatePredicate(TargetPredicateKind.FRIENDLY_AUTOMATIC, host, target, policy);
        }
    }

    /**
     * Revenge target selector for friendly ships.
     * More permissive than standard Selector — accepts any non-ally attacker.
     */
    public static class RevengeSelector implements Predicate<Entity> {
        protected final Entity host;

        public RevengeSelector(Entity host) {
            this.host = host;
        }

        @Override
        public boolean test(Entity target) {
            return evaluatePredicate(
                    TargetPredicateKind.FRIENDLY_REVENGE,
                    host,
                    target,
                    TargetPredicatePolicy.neutral());
        }
    }

    /**
     * Target selector for hostile (mob) ships.
     * Targets players (if config allows), friendly ships, and their summons.
     */
    public static class SelectorForHostile implements Predicate<Entity> {
        private final Entity host;

        public SelectorForHostile(Entity host) {
            this.host = host;
        }

        @Override
        public boolean test(Entity target) {
            TargetPredicatePolicy policy = new TargetPredicatePolicy(
                    false,
                    false,
                    false,
                    0,
                    ConfigHandler.mobShipsAttackPlayer());
            return evaluatePredicate(TargetPredicateKind.HOSTILE_AUTOMATIC, host, target, policy);
        }
    }

    /**
     * Revenge target selector for hostile (mob) ships.
     * Attacks back anything except other hostile ships.
     */
    public static class RevengeSelectorForHostile implements Predicate<Entity> {
        private final Entity host;

        public RevengeSelectorForHostile(Entity host) {
            this.host = host;
        }

        @Override
        public boolean test(Entity target) {
            return evaluatePredicate(
                    TargetPredicateKind.HOSTILE_REVENGE,
                    host,
                    target,
                    TargetPredicatePolicy.neutral());
        }
    }

}
