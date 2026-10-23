package com.lulan.shincolle.gametest;

import com.lulan.shincolle.handler.ConfigHandler;
import com.lulan.shincolle.reference.Reference;

import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.GameRules;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Applies the vanilla GameTest world rule that Forge's GameTest launch path skips.
 *
 * <p>Forge starts {@link GameTestServer} from the dedicated-server world loader, so the world is
 * created with default game rules instead of vanilla's GameTest rules. Natural spawning then stays
 * enabled, and the chunks that GameTest structures force-load spawn mobs without any player nearby.
 * A naturally spawned monster can become the nearest automatic target of a ship fixture or wander
 * into a test area. Only the GameTest server is affected; ordinary servers and clients never reach
 * the rule change, and entities that tests add themselves are not natural spawns. GameTest servers
 * also report the ship AI target authority they run under, which is always NEW: the
 * {@code shincolle.gametest.authority} property is only a compatibility input.</p>
 */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class GameTestWorldRules {

    private GameTestWorldRules() {
    }

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        MinecraftServer server = event.getServer();
        if (server instanceof GameTestServer) {
            // Fixtures also override cached settings across ticks. Keep watcher syncs out of the suite.
            ConfigHandler.CONFIG_RELOAD_TEST_BARRIER.begin();
            server.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
            String requested = System.getProperty("shincolle.gametest.authority", "NEW");
            ConfigHandler.setShipAiTargetAuthorityForTest(
                    ConfigHandler.ShipAiTargetAuthority.valueOf(requested));
            com.lulan.shincolle.utility.LogHelper.info("GameTest ship AI authority: requested " + requested
                    + ", effective " + ConfigHandler.shipAiTargetAuthority());
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        if (event.getServer() instanceof GameTestServer) {
            ConfigHandler.CONFIG_RELOAD_TEST_BARRIER.end();
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        if (event.getServer() instanceof GameTestServer) {
            ConfigHandler.CONFIG_RELOAD_TEST_BARRIER.end();
        }
    }
}
