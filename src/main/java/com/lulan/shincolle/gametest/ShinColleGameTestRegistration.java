package com.lulan.shincolle.gametest;

import com.lulan.shincolle.reference.Reference;

import net.minecraftforge.event.RegisterGameTestsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Reference.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ShinColleGameTestRegistration {

    private ShinColleGameTestRegistration() {
    }

    @SubscribeEvent
    public static void registerGameTests(RegisterGameTestsEvent event) {
        event.register(ShinColleEntityRegistryGameTests.class);
        event.register(HostileDespawnGameTests.class);
        event.register(ShipInventoryPageGateGameTests.class);
        event.register(ShipEquipmentResolverGameTests.class);
        event.register(ChestLootInjectionGameTests.class);
    }
}
