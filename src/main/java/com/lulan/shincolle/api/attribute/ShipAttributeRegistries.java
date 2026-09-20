package com.lulan.shincolle.api.attribute;

import com.lulan.shincolle.api.ShinColleApi;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

/**
 * Stable public identifiers for the extensible ship-attribute registry.
 *
 * <p>Addons may create their own {@code DeferredRegister<ShipAttributeType>}
 * against {@link #REGISTRY_KEY}. Registry contents are frozen during normal
 * Forge startup and are not a datapack-reload extension point.</p>
 */
public final class ShipAttributeRegistries {

    public static final ResourceLocation REGISTRY_ID =
            ResourceLocation.fromNamespaceAndPath(ShinColleApi.MOD_ID, "ship_attribute");
    public static final ResourceKey<Registry<ShipAttributeType>> REGISTRY_KEY =
            ResourceKey.createRegistryKey(REGISTRY_ID);

    private ShipAttributeRegistries() {
    }
}
