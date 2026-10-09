package com.lulan.shincolle.handler;

import com.lulan.shincolle.ai.ShipMovementGate;
import com.lulan.shincolle.ai.command.ShipCommandStateAdapter;
import com.lulan.shincolle.ai.domain.movement.FollowRecallRequest;
import com.lulan.shincolle.ai.domain.movement.MovementPoint;
import com.lulan.shincolle.ai.domain.movement.OwnerJumpDetector;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.reference.Reference;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Holds a departing owner's loaded followers briefly, without persistent force-load state. */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ShipFollowRecallHandler {
    private static final TicketType<UUID> FOLLOW_RECALL =
            TicketType.create("ship_follow_recall", UUID::compareTo, OwnerJumpDetector.HOLD_TICKS);
    /** Five by five entity-ticking chunks leave room to walk while waiting for the recall. */
    private static final int ENTITY_TICKING_RADIUS = 4;
    private static final Map<UUID, OwnerJumpDetector.State> HISTORY = new HashMap<>();

    private ShipFollowRecallHandler() { }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        if (!ShipMovementGate.active()) {
            HISTORY.clear();
            return;
        }
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            observe(player, event.getServer().getTickCount());
        }
    }

    /** Called before world chunk processing, while the source entities are still available. */
    public static void observe(ServerPlayer player, long now) {
        if (!ShipMovementGate.active() || !player.isAlive()) {
            forget(player.getUUID());
            return;
        }
        var dimension = ShipCommandStateAdapter.handle(player).dimension();
        var observed = OwnerJumpDetector.observe(HISTORY.getOrDefault(player.getUUID(),
                OwnerJumpDetector.State.initial(dimension)), dimension,
                new MovementPoint(player.getX(), player.getY(), player.getZ()), now);
        HISTORY.put(player.getUUID(), observed.state());
        observed.departedFrom().ifPresent(from -> holdFollowers(player, from, now));
    }

    private static void holdFollowers(ServerPlayer player, MovementPoint from, long now) {
        ServerLevel level = player.serverLevel();
        double radius = (player.getServer().getPlayerList().getSimulationDistance() + 1) * 16D;
        AABB source = new AABB(from.x() - radius, level.getMinBuildHeight(), from.z() - radius,
                from.x() + radius, level.getMaxBuildHeight(), from.z() + radius);
        for (BasicEntityShip ship : level.getEntitiesOfClass(BasicEntityShip.class, source)) {
            if (!ShipMovementGate.recallEligible(ship, player)) continue;
            level.getChunkSource().addRegionTicket(FOLLOW_RECALL, ship.chunkPosition(), ENTITY_TICKING_RADIUS, player.getUUID());
            ship.shipMovementExecutor().requestFollowRecall(new FollowRecallRequest(ShipCommandStateAdapter.handle(player), now));
        }
    }

    public static boolean isCurrent(UUID player, long detectedAt) {
        var history = HISTORY.get(player);
        return history != null && history.cooldownUntil().isPresent()
                && history.cooldownUntil().getAsLong() == detectedAt + OwnerJumpDetector.HOLD_TICKS;
    }

    /** Only observation history is removed; the vanilla ticket keeps its own timeout. */
    public static void forget(UUID player) {
        HISTORY.remove(player);
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
        HISTORY.clear();
    }
}
