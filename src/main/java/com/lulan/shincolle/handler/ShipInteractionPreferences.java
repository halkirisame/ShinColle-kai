package com.lulan.shincolle.handler;

import com.lulan.shincolle.reference.Reference;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Server-thread session preferences; never written to player or ship saves. */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public final class ShipInteractionPreferences {
    private static final Set<UUID> SWAPPED_PLAYERS = new HashSet<>();

    private ShipInteractionPreferences() { }

    public static boolean swapClicks(UUID playerId) {
        return SWAPPED_PLAYERS.contains(playerId);
    }

    public static void setSwapClicks(UUID playerId, boolean swapped) {
        if (swapped) {
            SWAPPED_PLAYERS.add(playerId);
        } else {
            SWAPPED_PLAYERS.remove(playerId);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!event.getEntity().level().isClientSide()) {
            SWAPPED_PLAYERS.remove(event.getEntity().getUUID());
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        SWAPPED_PLAYERS.clear();
    }
}
