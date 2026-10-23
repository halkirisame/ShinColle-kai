package com.lulan.shincolle.client.visual;

import com.lulan.shincolle.network.S2CShipVisualEventPacket;
import com.lulan.shincolle.reference.Reference;
import com.lulan.shincolle.shipdata.DefinitionBackedShip;
import com.lulan.shincolle.visualdata.ShipVisualEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.WeakHashMap;

@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ShipVisualClient {
    public record Attack(ShipVisualEvent event, long sequence, long gameTime) { }

    private static final Map<Entity, Attack> ATTACKS = new WeakHashMap<>();
    private static long sequence;

    private ShipVisualClient() { }

    @SubscribeEvent
    public static void registerReloadListener(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(ShipVisuals.LISTENER);
        S2CShipVisualEventPacket.bindClient(packet -> {
            var level = Minecraft.getInstance().level;
            if (level == null) return;
            Entity entity = level.getEntity(packet.entityId());
            if (entity instanceof DefinitionBackedShip) {
                ATTACKS.put(entity, new Attack(packet.event(), ++sequence, level.getGameTime()));
            }
        });
    }

    public static Attack attacks(Entity entity) {
        return ATTACKS.get(entity);
    }
}
