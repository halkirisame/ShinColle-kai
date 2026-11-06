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
import com.lulan.shincolle.ai.domain.movement.RecallCancellation;
import com.lulan.shincolle.ai.domain.movement.RecallLoadQueue;
import com.lulan.shincolle.ai.domain.movement.RecallNoticeWindow;
import com.lulan.shincolle.ai.domain.movement.RecallRequestKey;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.reference.Reference;
import com.lulan.shincolle.utility.LogHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;

/** Transient, server-owned departure recalls and their explicitly owned chunk tickets. */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ShipFollowRecallHandler {
    private static final TicketType<Long> FOLLOW_RECALL =
            TicketType.create("ship_follow_recall", Long::compareTo, OwnerJumpDetector.HOLD_TICKS);
    private static final TicketType<Long> FOLLOW_RECALL_WALK =
            TicketType.create("ship_follow_recall_walk", Long::compareTo, FollowRecallRequest.WAIT_TICKS + 20);
    private static final TicketType<Long> FOLLOW_RECALL_LOAD =
            TicketType.create("ship_follow_recall_load", Long::compareTo, RecallLoadQueue.TICKET_TIMEOUT);
    private static final int ENTITY_TICKING_RADIUS = 4;
    private static final int LOAD_RADIUS = 2;
    private static final long ROSTER_INTERVAL = 10L;
    private static final Map<UUID, OwnerJumpDetector.State> HISTORY = new HashMap<>();
    private static final Map<UUID, OwnerSettleDetector.State> SETTLE = new HashMap<>();
    private static final Map<UUID, OwnerAirborneDetector.State> AIRBORNE = new HashMap<>();
    private static final Map<UUID, Long> ROSTER_CHECKED = new HashMap<>();
    private static final Map<UUID, Session> SESSIONS = new LinkedHashMap<>();
    private static final Map<RecallRequestKey, Active> ACTIVE = new LinkedHashMap<>();
    private static final Map<Long, LoadTicket> LOAD_TICKETS = new HashMap<>();
    private static final Map<HoldLocation, HoldTicket> HOLDS = new HashMap<>();
    private static RecallLoadQueue loads = new RecallLoadQueue();
    private static MinecraftServer server;
    private static volatile UUID serverRun = UUID.randomUUID();
    private static long sequence;
    private static long loginSequence;
    private static long ticketSequence;

    private static final class Session {
        private final ServerPlayer player;
        private final ServerLevel level;
        private final long login;
        private long departure;
        private final Map<Long, RecallNoticeWindow> notices = new LinkedHashMap<>();
        private Session(ServerPlayer player) {
            this.player = player;
            this.level = player.serverLevel();
            this.login = ++loginSequence;
        }
    }

    private static final class Active {
        private final RecallRequestKey key;
        private final ServerLevel level;
        private final long departure;
        private DepartureRoster.Entry entry;
        private OptionalLong issuedAt = OptionalLong.empty();
        private Active(RecallRequestKey key, ServerLevel level, long departure, DepartureRoster.Entry entry) {
            this.key = key;
            this.level = level;
            this.departure = departure;
            this.entry = entry;
        }
    }

    private record LoadTicket(ServerLevel level, ChunkPos chunk, long key, long refreshed) { }
    private record HoldLocation(ServerLevel level, ChunkPos chunk, boolean walking) { }
    private static final class HoldTicket {
        private final long key = ++ticketSequence;
        private final Map<RecallRequestKey, Long> until = new HashMap<>();
    }

    private ShipFollowRecallHandler() { }

    public static List<DepartureRoster.Entry> rosterSnapshot(UUID owner) {
        return ACTIVE.values().stream().filter(a -> a.key.owner().equals(owner) && a.entry != null)
                .map(a -> a.entry).toList();
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            observe(player, event.getServer().getTickCount());
        }
        maintain(event.getServer().getTickCount());
    }

    /** Called before world chunk processing, while the source entities are still available. */
    public static void observe(ServerPlayer player, long now) {
        if (server != player.getServer()) {
            clearAll();
            server = player.getServer();
            serverRun = UUID.randomUUID();
        }
        Session old = SESSIONS.get(player.getUUID());
        if (old != null && (old.player != player || old.level != player.level())) forget(player.getUUID());
        if (!player.isAlive() || !ConfigHandler.canTeleport() || !ShipMovementGate.active()) {
            forget(player.getUUID());
            return;
        }
        SESSIONS.computeIfAbsent(player.getUUID(), ignored -> new Session(player));
        maintain(now);
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
        loads.promote(clock(now));
        syncLoadTickets(now);
    }

    private static RecallLoadQueue.Clock clock(long now) {
        return new RecallLoadQueue.Clock(now, System.nanoTime());
    }

    private static Active find(UUID owner, UUID ship) {
        return ACTIVE.values().stream().filter(a -> a.key.owner().equals(owner) && a.key.ship().equals(ship))
                .findFirst().orElse(null);
    }

    private static void holdFollowers(ServerPlayer player, MovementPoint from, DimensionKey dimension, long now) {
        Session session = SESSIONS.get(player.getUUID());
        session.departure++;
        ServerLevel level = player.serverLevel();
        double radius = (player.getServer().getPlayerList().getSimulationDistance() + 1) * 16D;
        AABB source = new AABB(from.x() - radius, level.getMinBuildHeight(), from.z() - radius,
                from.x() + radius, level.getMaxBuildHeight(), from.z() + radius);
        for (BasicEntityShip ship : level.getEntitiesOfClass(BasicEntityShip.class, source)) {
            if (!ShipMovementGate.recallEligible(ship, player)) continue;
            Active active = find(player.getUUID(), ship.getUUID());
            if (active == null) {
                List<DepartureRoster.Entry> roster = rosterSnapshot(player.getUUID());
                if (roster.size() >= DepartureRoster.MAX_ENTRIES) {
                    Active oldest = find(player.getUUID(), roster.get(0).ship());
                    cancel(oldest, RecallCancellation.ROSTER_EVICTED);
                }
                ChunkPos chunk = ship.chunkPosition();
                RecallRequestKey key = new RecallRequestKey(serverRun, player.getUUID(), session.login,
                        ship.getUUID(), ship.getShipUID(), ++sequence);
                active = new Active(key, level, session.departure, DepartureRoster.Entry.departed(
                        ship.getUUID(), ship.getShipUID(), dimension, chunk.x, chunk.z, now));
                ACTIVE.put(key, active);
            }
            if (active.entry == null) {
                List<DepartureRoster.Entry> roster = rosterSnapshot(player.getUUID());
                if (roster.size() >= DepartureRoster.MAX_ENTRIES) {
                    cancel(find(player.getUUID(), roster.get(0).ship()), RecallCancellation.ROSTER_EVICTED);
                }
                ChunkPos chunk = ship.chunkPosition();
                active.entry = DepartureRoster.Entry.departed(ship.getUUID(), ship.getShipUID(), dimension, chunk.x, chunk.z, now);
            }
            boolean waiting = ship.shipMovementExecutor().followRecallRemaining(ShipCommandStateAdapter.handle(player), now).isPresent();
            if (!waiting) issue(active, ship, player, now);
        }
    }

    private static void issue(Active active, BasicEntityShip ship, ServerPlayer owner, long now) {
        if (!isCurrent(active.key) || !ConfigHandler.canTeleport() || !ShipMovementGate.recallEligible(ship, owner)) return;
        hold(active, ship.chunkPosition(), false, now + OwnerJumpDetector.HOLD_TICKS);
        active.issuedAt = OptionalLong.of(now);
        ship.shipMovementExecutor().requestFollowRecall(new FollowRecallRequest(ShipCommandStateAdapter.handle(owner), now, active.key));
    }

    private static void tendRoster(ServerPlayer player, DimensionKey dimension, long now, boolean settled, boolean justSettled) {
        UUID owner = player.getUUID();
        if (!justSettled && now - ROSTER_CHECKED.getOrDefault(owner, Long.MIN_VALUE / 2) < ROSTER_INTERVAL) return;
        ROSTER_CHECKED.put(owner, now);
        for (Active active : List.copyOf(ACTIVE.values())) {
            if (!active.key.owner().equals(owner) || active.entry == null) continue;
            DepartureRoster.Entry entry = active.entry;
            Entity found = active.level.getEntity(entry.ship());
            BasicEntityShip ship = found instanceof BasicEntityShip s && s.isAlive() && s.getShipUID() == entry.shipUid() ? s : null;
            if (ship != null) active.entry = entry = entry.seenIn(ship.chunkPosition().x, ship.chunkPosition().z);
            if (ship != null && loads.view(active.key).isPresent()) {
                var result = loads.observe(active.key, RecallLoadQueue.Observation.REGISTERED, clock(now));
                if (result != RecallLoadQueue.Result.REGISTERED) {
                    terminate(active, result, now);
                    continue;
                }
                active.entry = entry = entry.loadFinished();
            } else if (found == null && loads.view(active.key).isPresent()) {
                var view = loads.view(active.key).orElseThrow();
                ChunkPos recorded = new ChunkPos(view.chunk().x(), view.chunk().z());
                var result = loads.observe(active.key, RecallLoadObservation.entitiesLoaded(active.level, recorded)
                        ? RecallLoadQueue.Observation.ENTITIES_LOADED_WITHOUT_SHIP : RecallLoadQueue.Observation.PENDING, clock(now));
                if (result == RecallLoadQueue.Result.ABSENT_AFTER_LOAD) {
                    terminate(active, result, now);
                    continue;
                }
                if (result != RecallLoadQueue.Result.WAIT) {
                    terminate(active, result, now);
                    continue;
                }
            }
            if (!settled) continue;
            DepartureRoster.Found state;
            if (found == null) state = DepartureRoster.Found.NOT_LOADED;
            else if (ship == null || !ShipMovementGate.recallEligible(ship, player)) state = DepartureRoster.Found.INELIGIBLE;
            else state = !ShipMovementGate.recallNeeded(ship, player) ? DepartureRoster.Found.NEAR
                    : active.level.isPositionEntityTicking(ship.blockPosition())
                    && ship.shipMovementExecutor().followRecallRemaining(ShipCommandStateAdapter.handle(player), now).isPresent()
                    ? DepartureRoster.Found.REQUESTED : DepartureRoster.Found.FAR;
            switch (DepartureRoster.decide(entry, state, now, dimension)) {
                case DROP -> {
                    if (state == DepartureRoster.Found.NEAR && active.issuedAt.isPresent()) {
                        // Leaving the roster does not shorten an already-running five-second wait.
                        active.entry = null;
                        loads.forget(active.key);
                    } else cancel(active, state == DepartureRoster.Found.NEAR
                            ? RecallCancellation.ARRIVED : RecallCancellation.INELIGIBLE);
                }
                case WAIT -> { }
                case START_LOAD -> {
                    if (!loads.enqueue(active.key, new RecallLoadQueue.Chunk(entry.dimension(), entry.chunkX(), entry.chunkZ()), clock(now))) {
                        terminate(active, RecallLoadQueue.Result.QUEUE_BUDGET_EXCEEDED, now);
                    } else active.entry = entry.loadStarted(now);
                }
                case REQUEST -> {
                    boolean waiting = ship.shipMovementExecutor().followRecallRemaining(ShipCommandStateAdapter.handle(player), now).isPresent();
                    if (!waiting) issue(active, ship, player, now);
                    else {
                        long remaining = ship.shipMovementExecutor().followRecallRemaining(ShipCommandStateAdapter.handle(player), now).orElse(0L);
                        hold(active, ship.chunkPosition(), true, now + remaining + 20L);
                    }
                    active.entry = entry.requested(ship.chunkPosition().x, ship.chunkPosition().z, now);
                }
            }
        }
    }

    /** Cancellation and budgets are maintained even while the owner is airborne. */
    private static void maintain(long now) {
        for (Session session : List.copyOf(SESSIONS.values())) {
            if (!session.player.isAlive() || session.player.isRemoved() || session.player.level() != session.level
                    || !ConfigHandler.canTeleport() || !ShipMovementGate.active()) forget(session.player.getUUID());
        }
        for (Active active : List.copyOf(ACTIVE.values())) {
            Session session = SESSIONS.get(active.key.owner());
            if (session == null) { cancel(active, RecallCancellation.OWNER_GONE); continue; }
            Entity found = active.level.getEntity(active.key.ship());
            if (found != null && (!(found instanceof BasicEntityShip ship) || ship.getShipUID() != active.key.shipUid()
                    || !ShipMovementGate.recallEligible(ship, session.player))) {
                cancel(active, RecallCancellation.INELIGIBLE);
                continue;
            }
            if (active.entry != null && DepartureRoster.stale(active.entry, now, ShipCommandStateAdapter.handle(session.player).dimension())) {
                terminate(active, RecallLoadQueue.Result.ROSTER_EXPIRED, now);
                continue;
            }
            if (loads.view(active.key).isPresent()) {
                var deadline = loads.deadline(active.key, clock(now));
                if (deadline != RecallLoadQueue.Result.WAIT) { terminate(active, deadline, now); continue; }
            }
            if (active.issuedAt.isPresent() && now - active.issuedAt.getAsLong() >= OwnerJumpDetector.HOLD_TICKS) {
                issuedFinished(active.key);
            }
        }
        for (var item : List.copyOf(HOLDS.entrySet())) {
            item.getValue().until.entrySet().removeIf(e -> now >= e.getValue() || !isCurrent(e.getKey()));
            if (item.getValue().until.isEmpty()) releaseHold(item.getKey(), item.getValue());
        }
        loads.promote(clock(now));
        syncLoadTickets(now);
        for (Session session : SESSIONS.values()) {
            for (var item : List.copyOf(session.notices.entrySet())) {
                RecallNoticeWindow notice = item.getValue();
                if (!notice.due(now)) continue;
                if (session.player.isAlive() && !session.player.isRemoved() && session.player.level() == session.level) {
                    session.player.sendSystemMessage(Component.translatable("chat.shincolle_kai.follow_recall.summary",
                            notice.absent(), notice.failures(), notice.limits()));
                }
                session.notices.remove(item.getKey());
            }
        }
    }

    private static void terminate(Active active, RecallLoadQueue.Result result, long now) {
        Session session = SESSIONS.get(active.key.owner());
        if (session != null) {
            session.notices.compute(active.departure, (departure, old) ->
                    (old == null ? RecallNoticeWindow.empty(now) : old).add(result));
        }
        LogHelper.debug("DEBUG: recall: " + active.key + " ended: " + result);
        cancel(active, RecallCancellation.INELIGIBLE);
    }

    private static void hold(Active active, ChunkPos chunk, boolean walking, long until) {
        HoldLocation location = new HoldLocation(active.level, chunk, walking);
        HoldTicket ticket = HOLDS.computeIfAbsent(location, ignored -> new HoldTicket());
        if (ticket.until.putIfAbsent(active.key, until) == null) {
            location.level.getChunkSource().addRegionTicket(walking ? FOLLOW_RECALL_WALK : FOLLOW_RECALL,
                    chunk, ENTITY_TICKING_RADIUS, ticket.key);
        }
    }

    private static void releaseHold(HoldLocation location, HoldTicket ticket) {
        location.level.getChunkSource().removeRegionTicket(location.walking ? FOLLOW_RECALL_WALK : FOLLOW_RECALL,
                location.chunk, ENTITY_TICKING_RADIUS, ticket.key);
        HOLDS.remove(location, ticket);
    }

    private static void removeHolds(RecallRequestKey key) {
        for (var item : List.copyOf(HOLDS.entrySet())) {
            item.getValue().until.remove(key);
            if (item.getValue().until.isEmpty()) releaseHold(item.getKey(), item.getValue());
        }
    }

    private static void syncLoadTickets(long now) {
        var leases = loads.leases();
        for (LoadTicket ticket : List.copyOf(LOAD_TICKETS.values())) {
            if (leases.stream().anyMatch(l -> l.generation() == ticket.key)) continue;
            ticket.level.getChunkSource().removeRegionTicket(FOLLOW_RECALL_LOAD, ticket.chunk, LOAD_RADIUS, ticket.key);
            LOAD_TICKETS.remove(ticket.key);
        }
        for (var lease : leases) {
            LoadTicket ticket = LOAD_TICKETS.get(lease.generation());
            if (ticket != null && now - ticket.refreshed < RecallLoadQueue.TICKET_REFRESH) continue;
            ServerLevel level = ticket != null ? ticket.level : ACTIVE.values().stream()
                    .filter(a -> loads.view(a.key).filter(v -> v.chunk().equals(lease.chunk())).isPresent())
                    .map(a -> a.level).findFirst().orElseThrow();
            ChunkPos chunk = new ChunkPos(lease.chunk().x(), lease.chunk().z());
            level.getChunkSource().addRegionTicket(FOLLOW_RECALL_LOAD, chunk, LOAD_RADIUS, lease.generation());
            LOAD_TICKETS.put(lease.generation(), new LoadTicket(level, chunk, lease.generation(), now));
        }
    }

    public static boolean isCurrent(RecallRequestKey key) {
        if (key == null || !key.serverRun().equals(serverRun) || !ACTIVE.containsKey(key)) return false;
        Session session = SESSIONS.get(key.owner());
        return session != null && session.login == key.login() && session.player.isAlive()
                && !session.player.isRemoved() && session.player.level() == session.level
                && ConfigHandler.canTeleport() && ShipMovementGate.active();
    }

    /** Diagnostic only: departure history never authorizes a ship's request. */
    public static boolean departureDetectedAt(UUID player, long tick) {
        var history = HISTORY.get(player);
        return history != null && history.detectedAt(tick);
    }

    public static int loadTicketCount() { return LOAD_TICKETS.size(); }

    public static int loadTicketCount(UUID owner) { return loads.ownerLeases(owner); }

    /** Releases only this key's issued wait, not the still-live departure roster. */
    public static void issuedFinished(RecallRequestKey key) {
        Active active = ACTIVE.get(key);
        if (active == null) return;
        active.issuedAt = OptionalLong.empty();
        removeHolds(key);
        if (active.entry == null) cancel(active, RecallCancellation.ARRIVED);
    }

    public static void cancelShip(BasicEntityShip ship, RecallCancellation reason) {
        cancelOwnedShip(ship, null, reason);
    }

    public static void cancelOwnedShip(BasicEntityShip ship, UUID previousOwner, RecallCancellation reason) {
        if (!(ship.level() instanceof ServerLevel)) return;
        for (Active active : List.copyOf(ACTIVE.values())) {
            if (active.key.ship().equals(ship.getUUID()) && active.key.shipUid() == ship.getShipUID()
                    && active.level == ship.level() && (previousOwner == null || active.key.owner().equals(previousOwner))) cancel(active, reason);
        }
        syncLoadTickets(ship.getServer().getTickCount());
    }

    private static void cancel(Active active, RecallCancellation reason) {
        cancel(active, Set.of(reason));
    }

    private static void cancel(Active active, Set<RecallCancellation> reasons) {
        if (active == null || ACTIVE.remove(active.key) == null) return;
        loads.forget(active.key);
        removeHolds(active.key);
        Entity found = active.level.getEntity(active.key.ship());
        if (found instanceof BasicEntityShip ship) ship.shipMovementExecutor().cancelFollowRecall(active.key);
        LogHelper.debug("DEBUG: recall: " + active.key + " cancelled: " + reasons);
    }

    public static boolean isSettled(UUID player) {
        var settle = SETTLE.get(player);
        return settle != null && settle.settled();
    }

    public static boolean isAirborne(UUID player) {
        var airborne = AIRBORNE.get(player);
        return airborne != null && airborne.airborne();
    }

    public static void forget(UUID player) {
        for (Active active : List.copyOf(ACTIVE.values())) {
            if (active.key.owner().equals(player)) cancel(active, RecallCancellation.OWNER_GONE);
        }
        HISTORY.remove(player);
        SETTLE.remove(player);
        AIRBORNE.remove(player);
        SESSIONS.remove(player);
        ROSTER_CHECKED.remove(player);
        syncLoadTickets(server == null ? 0 : server.getTickCount());
    }

    /** Capture a disabled reload before a later reload can turn teleport back on. */
    public static void onConfigReload() {
        if (ConfigHandler.canTeleport() && ShipMovementGate.active()) return;
        MinecraftServer current = ServerLifecycleHooks.getCurrentServer();
        UUID expectedRun = serverRun;
        if (current != null) current.execute(() -> {
            if (server == current && serverRun.equals(expectedRun)) clearAll();
        });
    }

    private static void clearAll() {
        for (UUID owner : List.copyOf(SESSIONS.keySet())) forget(owner);
        for (var item : List.copyOf(HOLDS.entrySet())) releaseHold(item.getKey(), item.getValue());
        loads = new RecallLoadQueue();
        syncLoadTickets(0);
        HISTORY.clear();
        SETTLE.clear();
        AIRBORNE.clear();
        ROSTER_CHECKED.clear();
        server = null;
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) { forget(event.getEntity().getUUID()); }
    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) { forget(event.getEntity().getUUID()); }
    @SubscribeEvent
    public static void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) { forget(event.getEntity().getUUID()); }
    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) { clearAll(); }
    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) { clearAll(); }
    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        for (Session session : List.copyOf(SESSIONS.values())) {
            if (session.level == level) forget(session.player.getUUID());
        }
    }
}
