package com.lulan.shincolle.init;

import com.lulan.shincolle.api.equipment.ShipEquipmentProvider;
import com.lulan.shincolle.api.equipment.ShipEquipmentProviders;
import com.lulan.shincolle.reference.Reference;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.IForgeRegistry;
import net.minecraftforge.registries.RegistryBuilder;

import java.util.function.Supplier;

/**
 * Registry of ship-equipment providers. Not saved, not synchronized, no overrides.
 */
public final class ModEquipmentProviders {

    public static final DeferredRegister<ShipEquipmentProvider> PROVIDERS =
            DeferredRegister.create(ShipEquipmentProviders.REGISTRY_KEY, Reference.MOD_ID);
    public static final Supplier<IForgeRegistry<ShipEquipmentProvider>> REGISTRY = PROVIDERS.makeRegistry(
            () -> new RegistryBuilder<ShipEquipmentProvider>().disableSaving().disableSync().disableOverrides());

    private ModEquipmentProviders() {
    }

    public static void register(IEventBus eventBus) {
        PROVIDERS.register(eventBus);
    }
}
