package com.lulan.shincolle.handler;

import com.lulan.shincolle.compat.ftbquests.ShipFTBQuestsIntegration;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.entity.BasicEntitySummon;
import com.lulan.shincolle.entity.IShipOwner;
import com.lulan.shincolle.reference.Reference;
import com.lulan.shincolle.utility.LogHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

import java.lang.reflect.Field;
import java.util.IdentityHashMap;
import java.util.Map;

/** Supplements completed ship kills without changing damage, loot or vanilla's existing recipient. */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ShipKillCreditHandler {
    private static final Map<MinecraftServer, DeferredKillCreditQueue<LivingEntity, Receipt>> SERVERS = new IdentityHashMap<>();

    private ShipKillCreditHandler() { }

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void onDrops(LivingDropsEvent event) {
        LivingEntity victim = event.getEntity();
        if (!(victim.level() instanceof ServerLevel level) || victim instanceof Player
                || victim instanceof BasicEntityShip || victim instanceof BasicEntitySummon
                || victim instanceof IShipOwner || victim instanceof EnderDragon) return;
        DamageSource source = event.getSource();
        BasicEntityShip ship = motherShip(source.getEntity());
        if (ship == null || ship.isRemoved() || !(ship.getOwner() instanceof ServerPlayer owner)
                || !present(owner, level)) return;
        DeathState death = readDeath(victim);
        if (death == null || !death.dead() || victim.getHealth() > 0F || victim.isRemoved()) return;
        LivingEntity nativeCredit = victim.getKillCredit();
        ServerPlayer recipient = nativeCredit instanceof ServerPlayer player ? player : null;
        var queue = SERVERS.computeIfAbsent(level.getServer(), ignored -> new DeferredKillCreditQueue<>());
        queue.offer(victim, new Receipt(victim, level, source, ship, owner, recipient, death.score()));
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        var queue = SERVERS.get(event.getServer());
        if (queue == null) return;
        for (var entry : queue.drain()) {
            Receipt receipt = entry.value();
            boolean vanilla = queue.claim(entry.key(), DeferredKillCreditQueue.Lane.VANILLA);
            boolean ftb = queue.claim(entry.key(), DeferredKillCreditQueue.Lane.FTB);
            if (!valid(receipt)) continue;
            if (vanilla) {
                try {
                    creditVanilla(receipt);
                } catch (RuntimeException error) {
                    LogHelper.error("Could not supplement vanilla ship kill credit: " + error);
                }
            }
            if (ftb && ModList.get().isLoaded("ftbquests")) {
                try {
                    ShipFTBQuestsIntegration.creditKill(receipt.owner(), receipt.victim());
                } catch (RuntimeException | LinkageError error) {
                    LogHelper.error("Could not supplement FTB ship kill credit: " + error);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            var queue = SERVERS.get(level.getServer());
            if (queue != null) queue.discardPending(receipt -> receipt.level() == level);
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        var queue = SERVERS.remove(event.getServer());
        if (queue != null) queue.clear();
    }

    private static BasicEntityShip motherShip(Entity attacker) {
        if (attacker instanceof BasicEntityShip ship) return ship;
        if (attacker instanceof BasicEntitySummon summon && summon.getHost() instanceof BasicEntityShip ship) return ship;
        return null;
    }

    private static boolean present(ServerPlayer player, ServerLevel level) {
        return !player.isRemoved() && player.level() == level && level.getPlayerByUUID(player.getUUID()) == player;
    }

    private static boolean valid(Receipt receipt) {
        LivingEntity victim = receipt.victim();
        if (victim.level() != receipt.level() || victim.isRemoved() || victim.getHealth() > 0F
                || receipt.ship().isRemoved() || !present(receipt.owner(), receipt.level())
                || motherShip(receipt.source().getEntity()) != receipt.ship()
                || receipt.ship().getOwner() != receipt.owner()) return false;
        DeathState death = readDeath(victim);
        return death != null && death.dead();
    }

    private static void creditVanilla(Receipt receipt) {
        ServerPlayer recipient = receipt.nativeRecipient();
        if (recipient == null) {
            recipient = receipt.owner();
            if (receipt.score() >= 0) {
                recipient.awardKillScore(receipt.victim(), receipt.score(), receipt.source());
            }
        } else if (!present(recipient, receipt.level())) {
            return;
        }
        // Per-type statistics use Player.killedEntity, not the native awardKillScore path.
        recipient.awardStat(Stats.ENTITY_KILLED.get(receipt.victim().getType()));
    }

    private static DeathState readDeath(LivingEntity victim) {
        try {
            return new DeathState(DeathFields.DEAD.getBoolean(victim), DeathFields.SCORE.getInt(victim));
        } catch (IllegalAccessException | RuntimeException | LinkageError error) {
            LogHelper.error("Could not read completed death for ship kill accounting: " + error);
            return null;
        }
    }

    private static final class DeathFields {
        private static final Field DEAD = ObfuscationReflectionHelper.findField(LivingEntity.class, "f_20890_");
        private static final Field SCORE = ObfuscationReflectionHelper.findField(LivingEntity.class, "f_20897_");
    }

    private record DeathState(boolean dead, int score) { }

    private record Receipt(LivingEntity victim, ServerLevel level, DamageSource source, BasicEntityShip ship,
                           ServerPlayer owner, ServerPlayer nativeRecipient, int score) { }
}
