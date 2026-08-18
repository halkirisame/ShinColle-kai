package com.lulan.shincolle.handler;

import com.lulan.shincolle.capability.CapaTeitoku;
import com.lulan.shincolle.capability.CapaTeitokuProvider;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.entity.BasicEntityShipHostile;
import com.lulan.shincolle.entity.IShipAttackBase;
import com.lulan.shincolle.item.MarriageRing;
import com.lulan.shincolle.network.ModNetworking;
import com.lulan.shincolle.network.S2CEntitySyncPacket;
import com.lulan.shincolle.network.S2CGUISyncPacket;
import com.lulan.shincolle.reference.Reference;
import com.lulan.shincolle.server.ServerDataManager;
import com.lulan.shincolle.utility.EntityHelper;
import com.lulan.shincolle.utility.LogHelper;
import com.lulan.shincolle.utility.TeamHelper;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Server-side event handler for ShinColle.
 * <p>
 * Manages the ServerDataManager lifecycle:
 * - Initializes when overworld loads
 * - Saves and resets on server stop
 * - Ticks every server tick
 * - Updates player UIDs on login
 */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ServerEventHandler {

    /**
     * Initialize ServerDataManager when the overworld level loads.
     */
    @SubscribeEvent
    public static void onLevelLoad(LevelEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel serverLevel) {
            if (serverLevel.dimension() == Level.OVERWORLD) {
                ServerDataManager.init(serverLevel);
            }
        }
    }

    /**
     * Save and reset ServerDataManager when the server stops.
     */
    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        ServerDataManager.reset();
    }

    /**
     * Tick the ServerDataManager every server tick.
     */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            ServerDataManager.onServerTick();
        }
    }

    /**
     * Player periodic server tick:
     * - updates ring ownership/active state
     * - keeps player UID initialized
     * - runs hostile mob/boss spawn ticks
     * - decrements team cooldown
     */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.START) {
            return;
        }

        Player player = event.player;
        if (player == null || player.level().isClientSide()) {
            return;
        }

        CapaTeitoku capa = player.getCapability(CapaTeitokuProvider.CAPABILITY).orElse(null);
        // Caps are invalidated while the player entity is being removed (death,
        // dimension change) but the player keeps ticking for a few more ticks.
        if (capa == null) {
            return;
        }

        // [PORT] 1.10.2 -> 1.20.1: keep ring possession tracking used by ring-gated
        // systems (spawn, movement buffs).
        if ((player.tickCount & 15) == 0) {
            updateRingState(player, capa);
        }

        // every 32 ticks: ensure UID exists and run slower periodic logic
        if (player.tickCount > 0 && (player.tickCount & 31) == 0) {
            if (capa.getPlayerUID() < 100) {
                ServerDataManager.updatePlayerID(player);
                if (capa.getPlayerUID() < 100) {
                    LogHelper.debug("player tick: failed to initialize player UID, skip spawn tick");
                    return;
                }
            }

            if ((player.tickCount & 127) == 0) {
                TeamHelper.updateTeamList(player, capa);
                EntityHelper.spawnMobShip(player, capa);
            }
        }

        EntityHelper.spawnBossShip(player, capa);

        int teamCooldown = capa.getTeamCooldown();
        if (teamCooldown > 0) {
            int remaining = teamCooldown - 1;
            capa.setTeamCooldown(remaining);
            if (player instanceof ServerPlayer serverPlayer
                    && (remaining == 0 || (player.tickCount % 20) == 0)) {
                ModNetworking.sendToPlayer(S2CGUISyncPacket.syncPlayerMisc(capa), serverPlayer);
            }
        }
    }

    /**
     * Assign or update player UID on login.
     */
    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        updatePlayerCacheOnServer(event.getEntity());
    }

    /**
     * Update player cache on respawn.
     */
    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        updatePlayerCacheOnServer(event.getEntity());
    }

    /**
     * Update player cache on dimension change.
     */
    @SubscribeEvent
    public static void onPlayerChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        updatePlayerCacheOnServer(event.getEntity());
    }

    /** Send custom ship state when a client begins tracking an existing entity. */
    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof ServerPlayer player
                && event.getTarget() instanceof BasicEntityShip ship) {
            ModNetworking.sendToPlayer(S2CEntitySyncPacket.syncAllMisc(ship), player);
            ModNetworking.sendToPlayer(S2CEntitySyncPacket.syncAttrs(ship), player);
            ModNetworking.sendToPlayer(S2CEntitySyncPacket.syncRiders(ship), player);
            ModNetworking.sendToPlayer(S2CEntitySyncPacket.syncUnitName(ship), player);
            ModNetworking.sendToPlayer(S2CEntitySyncPacket.syncBuffMap(ship), player);
        }
    }

    /**
     * Save player data on logout.
     */
    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!event.getEntity().level().isClientSide()) {
            Player player = event.getEntity();
            CapaTeitoku capa = ServerDataManager.getTeitokuCapability(player);
            if (capa != null && capa.getPlayerUID() > 0) {
                updatePlayerCacheOnServer(player);
                LogHelper.info("player logged out: " + player.getGameProfile().getName()
                        + " uid=" + capa.getPlayerUID());
            }
        }
    }

    /**
     * Handle entity death side effects (kill count, morale and nearby emote
     * reaction).
     */
    @SubscribeEvent
    public static void onEntityDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide()) {
            return;
        }

        LivingEntity deadEntity = event.getEntity();

        if (deadEntity instanceof BasicEntityShip ship) {
            // [PORT] 1.10.2 -> 1.20.1: keep ship cache update on ship death.
            ship.updateShipCacheDataWithoutNewID();
        }

        Entity killerEntity = event.getSource().getDirectEntity();
        if (killerEntity == null) {
            killerEntity = event.getSource().getEntity();
        }

        if (killerEntity instanceof BasicEntityShip killerShip) {
            // [PORT] 1.10.2 -> 1.20.1: restore direct-kill reward contract.
            killerShip.addKills();
            killerShip.addMorale(2);
        } else if (killerEntity instanceof IShipAttackBase attackBase
                && attackBase.getHostEntity() instanceof BasicEntityShip hostShip) {
            // [PORT] 1.10.2 -> 1.20.1: summon/projectile kills count for host ship.
            hostShip.addKills();
        }

        if (deadEntity instanceof BasicEntityShip deadShip) {
            // [PORT] 1.10.2 -> 1.20.1: restore nearby shock reaction when ship dies.
            deadShip.applyParticleEmotion(8);
            EntityHelper.applyShipEmotesAOE(deadShip.level(), deadShip.getX(), deadShip.getY(), deadShip.getZ(), 16D,
                    6);
        } else if (deadEntity instanceof BasicEntityShipHostile) {
            // [PORT] 1.10.2 -> 1.20.1: keep hostile death AOE reaction path.
            EntityHelper.applyShipEmotesAOEHostile(
                    deadEntity.level(), deadEntity.getX(), deadEntity.getY(), deadEntity.getZ(), 48D, 6);
        }
    }

    private static void updatePlayerCacheOnServer(Player player) {
        if (player != null && !player.level().isClientSide()) {
            ServerDataManager.updatePlayerID(player);
            CapaTeitoku capa = player.getCapability(CapaTeitokuProvider.CAPABILITY).orElse(null);
            if (capa != null && capa.getPlayerUID() > 0) {
                TeamHelper.updateTeamList(player, capa);
            }
            if (player instanceof ServerPlayer serverPlayer && capa != null) {
                syncPlayerCapability(serverPlayer, capa);
            }
        }
    }

    /**
     * Custom player capabilities are local objects on each logical side. Forge
     * does not mirror them automatically, so send the authoritative state at
     * every player-entity lifecycle boundary.
     */
    private static void syncPlayerCapability(ServerPlayer player, CapaTeitoku capa) {
        ModNetworking.sendToPlayer(S2CGUISyncPacket.syncPlayerFull(capa), player);
        ModNetworking.sendToPlayer(S2CGUISyncPacket.syncShipsAll(capa), player);
        ModNetworking.sendToPlayer(S2CGUISyncPacket.syncUnitNames(capa), player);
        ModNetworking.sendToPlayer(S2CGUISyncPacket.syncTargetClasses(
                ServerDataManager.getPlayerTargetClass(capa.getPlayerUID())), player);
        ModNetworking.sendToPlayer(S2CGUISyncPacket.syncTeamData(capa,
                TeamHelper.getTeamDataByUID(capa.getPlayerUID()),
                ServerDataManager.getAllTeamWorldData()), player);
    }

    private static void updateRingState(Player player, CapaTeitoku capa) {
        boolean wasPresent = capa.hasRing();
        boolean wasActive = capa.isRingActive();
        boolean wasFlying = capa.isRingFlying();
        boolean hasRing = MarriageRing.hasAnyRing(player);
        boolean isActive = MarriageRing.hasActiveRing(player);

        if ((!hasRing || !isActive) && capa.isRingFlying()) {
            if (!player.getAbilities().instabuild && player.getAbilities().flying) {
                player.getAbilities().flying = false;
                player.onUpdateAbilities();
            }
            capa.setRingFlying(false);
        }

        capa.setHasRing(hasRing);
        capa.setRingActive(isActive);

        if (player instanceof ServerPlayer serverPlayer
                && (wasPresent != capa.hasRing() || wasActive != capa.isRingActive()
                || wasFlying != capa.isRingFlying())) {
            ModNetworking.sendToPlayer(S2CGUISyncPacket.syncPlayerMisc(capa), serverPlayer);
        }
    }

}
