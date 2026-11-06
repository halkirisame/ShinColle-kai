package com.lulan.shincolle.client;

import com.lulan.shincolle.network.C2SShipClickPreferencePacket;
import com.lulan.shincolle.network.ModNetworking;
import com.lulan.shincolle.reference.Reference;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Dist.CLIENT)
public final class ShipInteractionClientEvents {
    private ShipInteractionClientEvents() { }

    @SubscribeEvent
    public static void onLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        ModNetworking.sendToServer(new C2SShipClickPreferencePacket(ShipInteractionClientConfig.swapClicks()));
    }
}
