package com.lulan.shincolle.handler;

import com.lulan.shincolle.ai.domain.RecentOwnerAttack;
import com.lulan.shincolle.ai.domain.TargetHandle;
import com.lulan.shincolle.ai.observation.MinecraftEntityObservationAdapter;
import com.lulan.shincolle.reference.Reference;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Server-only, nonpersistent successful player attacks; entities live only in this tick's pending list. */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ShipOwnerAssistHandler {
    private static final Map<MinecraftServer, Runtime> SERVERS = new IdentityHashMap<>();

    private ShipOwnerAssistHandler() { }

    private static final class Runtime {
        private final Map<UUID, RecentOwnerAttack> latest = new HashMap<>();
        private final List<Pending> pending = new ArrayList<>();
        private long sequence;
    }

    private record Pending(LivingDamageEvent event, ServerPlayer owner, ResourceKey<Level> ownerDimension,
                           TargetHandle target, long occurredAt, long sequence) { }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onDamage(LivingDamageEvent event) {
        if (!(event.getEntity().level() instanceof ServerLevel level)
                || !(event.getSource().getEntity() instanceof ServerPlayer owner) || !available(owner)) return;
        Entity direct = event.getSource().getDirectEntity();
        if (direct != owner && !(direct instanceof Projectile projectile && projectile.getOwner() == owner)) return;
        Runtime runtime = SERVERS.computeIfAbsent(level.getServer(), ignored -> new Runtime());
        runtime.pending.add(new Pending(event, owner, owner.level().dimension(),
                MinecraftEntityObservationAdapter.observe(event.getEntity()).handle(),
                level.getServer().getTickCount(), ++runtime.sequence));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Runtime runtime = SERVERS.get(event.getServer());
        if (runtime == null) return;
        long now = event.getServer().getTickCount();
        List<Pending> pending = new ArrayList<>(runtime.pending);
        runtime.pending.clear();
        for (Pending attack : pending) {
            ServerPlayer owner = attack.owner();
            if (attack.occurredAt() != now || !available(owner)
                    || !owner.level().dimension().equals(attack.ownerDimension())) continue;
            RecentOwnerAttack candidate = new RecentOwnerAttack(attack.target(), attack.occurredAt(), attack.sequence());
            RecentOwnerAttack.confirm(Optional.ofNullable(runtime.latest.get(owner.getUUID())), candidate,
                    attack.event().getAmount(), attack.event().isCanceled())
                    .ifPresent(confirmed -> runtime.latest.put(owner.getUUID(), confirmed));
        }
        runtime.latest.values().removeIf(attack -> !attack.currentAt(now));
    }

    public static Optional<RecentOwnerAttack> latest(ServerLevel level, UUID owner) {
        Runtime runtime = SERVERS.get(level.getServer());
        return runtime == null ? Optional.empty() : Optional.ofNullable(runtime.latest.get(owner));
    }

    private static boolean available(ServerPlayer owner) {
        return owner.isAlive() && !owner.isRemoved() && !owner.hasDisconnected()
                && owner.level().getPlayerByUUID(owner.getUUID()) == owner;
    }

    private static void forget(Entity owner) {
        if (!(owner.level() instanceof ServerLevel level)) return;
        Runtime runtime = SERVERS.get(level.getServer());
        if (runtime != null) {
            runtime.latest.remove(owner.getUUID());
            runtime.pending.removeIf(attack -> attack.owner().getUUID().equals(owner.getUUID()));
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) { forget(event.getEntity()); }

    @SubscribeEvent
    public static void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) { forget(event.getEntity()); }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer) forget(event.getEntity());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) { SERVERS.remove(event.getServer()); }
}
