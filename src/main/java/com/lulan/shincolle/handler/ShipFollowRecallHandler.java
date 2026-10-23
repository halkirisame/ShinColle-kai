package com.lulan.shincolle.handler;

import com.lulan.shincolle.ai.ShipMovementGate;
import com.lulan.shincolle.ai.command.ShipCommandStateAdapter;
import com.lulan.shincolle.ai.domain.DimensionKey;
import com.lulan.shincolle.ai.domain.movement.DepartureRoster;
import com.lulan.shincolle.ai.domain.movement.FollowRecallRequest;
import com.lulan.shincolle.ai.domain.movement.MovementPoint;
import com.lulan.shincolle.ai.domain.movement.OwnerAirborneDetector;
import com.lulan.shincolle.ai.domain.movement.OwnerJumpDetector;
import com.lulan.shincolle.ai.domain.movement.OwnerSettleDetector;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.reference.Reference;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Holds a departing owner's loaded followers briefly, and once the owner lands, calls back the
 * ones the departure left behind, even if their chunk has since unloaded. Everything here is in
 * memory only; the vanilla tickets keep their own timeouts.
 */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ShipFollowRecallHandler {
    private static final TicketType<UUID> FOLLOW_RECALL =
            TicketType.create("ship_follow_recall", UUID::compareTo, OwnerJumpDetector.HOLD_TICKS);
    /** Holds the chunk a walking follower has reached, only as long as the recall wait that is still running. */
    private static final TicketType<UUID> FOLLOW_RECALL_WALK =
            TicketType.create("ship_follow_recall_walk", UUID::compareTo, FollowRecallRequest.WAIT_TICKS + 20);
    /** Holds a departed follower's chunk only while it is read back in, and no longer than the wait for it. */
    private static final TicketType<UUID> FOLLOW_RECALL_LOAD =
            TicketType.create("ship_follow_recall_load", UUID::compareTo, (int) DepartureRoster.LOAD_WAIT_TICKS);
    /** Five by five entity-ticking chunks leave room to walk while waiting for the recall. */
    private static final int ENTITY_TICKING_RADIUS = 4;
    /** The centre chunk alone ticks, enough for the follower to be read back in. */
    private static final int LOAD_RADIUS = 2;
    /** The roster is looked at this often while the owner is settled, never every tick. */
    private static final long ROSTER_INTERVAL = 10L;
    private static final Map<UUID, OwnerJumpDetector.State> HISTORY = new HashMap<>();
    private static final Map<UUID, OwnerSettleDetector.State> SETTLE = new HashMap<>();
    private static final Map<UUID, OwnerAirborneDetector.State> AIRBORNE = new HashMap<>();
    private static final Map<UUID, List<DepartureRoster.Entry>> ROSTER = new HashMap<>();
    private static final Map<UUID, Long> ROSTER_CHECKED = new HashMap<>();
    /** Times of the recall requests this owner's roster issued, so a held request can tell it is still wanted. */
    private static final Map<UUID, List<Long>> ISSUED = new HashMap<>();

    private ShipFollowRecallHandler() { }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            observe(player, event.getServer().getTickCount());
        }
    }

    /** Called before world chunk processing, while the source entities are still available. */
    public static void observe(ServerPlayer player, long now) {
        if (!player.isAlive()) {
            forget(player.getUUID());
            return;
        }
        var dimension = ShipCommandStateAdapter.handle(player).dimension();
        var point = new MovementPoint(player.getX(), player.getY(), player.getZ());
        var observed = OwnerJumpDetector.observe(HISTORY.getOrDefault(player.getUUID(),
                OwnerJumpDetector.State.initial(dimension)), dimension, point, now);
        HISTORY.put(player.getUUID(), observed.state());
        observed.departedFrom().ifPresent(from -> holdFollowers(player, from, dimension, now));

        var facts = new OwnerSettleDetector.Facts(player.isFallFlying(),
                player.onGround() || player.isInWater() || player.isPassenger());
        var settle = OwnerSettleDetector.observe(SETTLE.getOrDefault(player.getUUID(),
                OwnerSettleDetector.State.initial(dimension)), dimension, point, facts, now);
        SETTLE.put(player.getUUID(), settle.state());
        AIRBORNE.put(player.getUUID(), OwnerAirborneDetector.observe(AIRBORNE.getOrDefault(player.getUUID(),
                OwnerAirborneDetector.State.initial(dimension)), dimension, point,
                new OwnerAirborneDetector.Facts(player.isFallFlying(),
                        player.onGround() || player.isInWater() || player.isPassenger() || player.onClimbable()), now));
        tendRoster(player, dimension, now, settle.settled(), settle.becameSettled());
    }

    private static void holdFollowers(ServerPlayer player, MovementPoint from, DimensionKey dimension, long now) {
        ServerLevel level = player.serverLevel();
        double radius = (player.getServer().getPlayerList().getSimulationDistance() + 1) * 16D;
        AABB source = new AABB(from.x() - radius, level.getMinBuildHeight(), from.z() - radius,
                from.x() + radius, level.getMaxBuildHeight(), from.z() + radius);
        for (BasicEntityShip ship : level.getEntitiesOfClass(BasicEntityShip.class, source)) {
            if (!ShipMovementGate.recallEligible(ship, player)) continue;
            // A follower still waiting on an earlier departure keeps that wait and its ticket: holding it again
            // would start the wait over and keep extending the ticket while the owner keeps moving fast.
            boolean waiting = ship.shipMovementExecutor().followRecallRemaining(ShipCommandStateAdapter.handle(player), now).isPresent();
            if (!waiting) {
                level.getChunkSource().addRegionTicket(FOLLOW_RECALL, ship.chunkPosition(), ENTITY_TICKING_RADIUS, player.getUUID());
                ship.shipMovementExecutor().requestFollowRecall(new FollowRecallRequest(ShipCommandStateAdapter.handle(player), now));
            }
            ChunkPos chunk = ship.chunkPosition();
            ROSTER.compute(player.getUUID(), (id, old) -> DepartureRoster.record(old == null ? List.of() : old,
                    DepartureRoster.Entry.departed(ship.getUUID(), ship.getShipUID(), dimension, chunk.x, chunk.z, now)));
        }
    }

    /**
     * While followers walk off in the hold, where each one stands is kept up to date, so an unloaded
     * one is read back from the chunk it stopped in. Once the owner has settled, each departed
     * follower is also loaded if its chunk is gone and asked to come by the ordinary follow
     * teleport. The look is every few ticks, not every tick.
     */
    private static void tendRoster(ServerPlayer player, DimensionKey dimension, long now, boolean settled, boolean justSettled) {
        UUID owner = player.getUUID();
        List<DepartureRoster.Entry> roster = ROSTER.get(owner);
        if (roster == null || roster.isEmpty()) return;
        if (!justSettled && now - ROSTER_CHECKED.getOrDefault(owner, Long.MIN_VALUE / 2) < ROSTER_INTERVAL) return;
        ROSTER_CHECKED.put(owner, now);
        if (settled && !ConfigHandler.canTeleport()) {
            ROSTER.remove(owner);
            return;
        }
        ServerLevel level = player.serverLevel();
        List<DepartureRoster.Entry> next = new ArrayList<>();
        for (DepartureRoster.Entry entry : roster) {
            Entity found = level.getEntity(entry.ship());
            BasicEntityShip ship = found instanceof BasicEntityShip s && s.isAlive() && s.getShipUID() == entry.shipUid() ? s : null;
            if (ship != null) {
                ChunkPos at = ship.chunkPosition();
                entry = entry.seenIn(at.x, at.z);
            }
            if (!settled) {
                if (!DepartureRoster.stale(entry, now, dimension)) next.add(entry);
                continue;
            }
            DepartureRoster.Found state;
            if (found == null) {
                state = DepartureRoster.Found.NOT_LOADED;
            } else if (ship == null || !ShipMovementGate.recallEligible(ship, player)) {
                state = DepartureRoster.Found.INELIGIBLE;
            } else {
                state = !ShipMovementGate.recallNeeded(ship, player) ? DepartureRoster.Found.NEAR
                        : level.isPositionEntityTicking(ship.blockPosition())
                        && ship.shipMovementExecutor().followRecallRemaining(ShipCommandStateAdapter.handle(player), now).isPresent()
                        ? DepartureRoster.Found.REQUESTED : DepartureRoster.Found.FAR;
            }
            switch (DepartureRoster.decide(entry, state, now, dimension)) {
                case DROP -> { }
                case WAIT -> next.add(entry);
                case START_LOAD -> {
                    level.getChunkSource().addRegionTicket(FOLLOW_RECALL_LOAD,
                            new ChunkPos(entry.chunkX(), entry.chunkZ()), LOAD_RADIUS, owner);
                    next.add(entry.loadStarted(now));
                }
                case REQUEST -> {
                    ChunkPos chunk = ship.chunkPosition();
                    // A follower that walked out of its ticking area while the departure's wait runs gets
                    // its chunk held for the rest of that wait only, and keeps the wait: a new request would restart it.
                    boolean waiting = ship.shipMovementExecutor().followRecallRemaining(ShipCommandStateAdapter.handle(player), now).isPresent();
                    level.getChunkSource().addRegionTicket(waiting ? FOLLOW_RECALL_WALK : FOLLOW_RECALL, chunk, ENTITY_TICKING_RADIUS, owner);
                    if (!waiting) {
                        ship.shipMovementExecutor().requestFollowRecall(
                                new FollowRecallRequest(ShipCommandStateAdapter.handle(player), now));
                        List<Long> issued = ISSUED.computeIfAbsent(owner, id -> new ArrayList<>());
                        issued.removeIf(time -> now - time >= OwnerJumpDetector.HOLD_TICKS);
                        issued.add(now);
                    }
                    next.add(entry.requested(chunk.x, chunk.z, now));
                }
            }
        }
        if (next.isEmpty()) ROSTER.remove(owner);
        else ROSTER.put(owner, List.copyOf(next));
    }

    /** Whether a held request is the owner's own departure or one the roster issued, not a stale one. */
    public static boolean isCurrent(UUID player, long detectedAt) {
        var history = HISTORY.get(player);
        if (history != null && history.detectedAt(detectedAt)) return true;
        var issued = ISSUED.get(player);
        return issued != null && issued.contains(detectedAt);
    }

    /** Whether the owner has landed and stopped, so a recall would not drop a ship out of the air. */
    public static boolean isSettled(UUID player) {
        var settle = SETTLE.get(player);
        return settle != null && settle.settled();
    }

    /** Whether the owner is gliding or falling, so no follow teleport may put a ship into the air. */
    public static boolean isAirborne(UUID player) {
        var airborne = AIRBORNE.get(player);
        return airborne != null && airborne.airborne();
    }

    /** Only observation history and the roster are removed; the vanilla tickets keep their own timeout. */
    public static void forget(UUID player) {
        HISTORY.remove(player);
        SETTLE.remove(player);
        AIRBORNE.remove(player);
        ROSTER.remove(player);
        ROSTER_CHECKED.remove(player);
        ISSUED.remove(player);
    }

    private static void clearAll() {
        HISTORY.clear();
        SETTLE.clear();
        AIRBORNE.clear();
        ROSTER.clear();
        ROSTER_CHECKED.clear();
        ISSUED.clear();
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        forget(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        forget(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        forget(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) forget(player.getUUID());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        clearAll();
    }
}
