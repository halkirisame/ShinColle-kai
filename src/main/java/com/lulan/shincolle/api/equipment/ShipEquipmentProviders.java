package com.lulan.shincolle.api.equipment;

import com.lulan.shincolle.api.ShinColleApi;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

/**
 * Stable public identifiers for the ship-equipment provider registry.
 *
 * <p>Addons create their own {@code DeferredRegister<ShipEquipmentProvider>} against
 * {@link #REGISTRY_KEY}. Providers are consulted in priority order (highest {@link
 * ShipEquipmentProvider#priority()} first, then registry ID ascending). The registry is frozen
 * during normal Forge startup, is not saved or synchronized, and does not allow overrides.</p>
 */
public final class ShipEquipmentProviders {

    public static final ResourceKey<Registry<ShipEquipmentProvider>> REGISTRY_KEY =
            ResourceKey.createRegistryKey(
                    ResourceLocation.fromNamespaceAndPath(ShinColleApi.MOD_ID, "equipment_provider"));

    private ShipEquipmentProviders() {
    }
}
