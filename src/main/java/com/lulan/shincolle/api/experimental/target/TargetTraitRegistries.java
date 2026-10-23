package com.lulan.shincolle.api.experimental.target;

import com.lulan.shincolle.api.ShinColleApi;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

/**
 * Experimental key of the target-trait registry ({@code shincolle_kai:target_trait}). The registry
 * is not saved or synchronized and does not allow overrides.
 */
public final class TargetTraitRegistries {

    public static final ResourceKey<Registry<TargetTrait>> REGISTRY_KEY =
            ResourceKey.createRegistryKey(
                    ResourceLocation.fromNamespaceAndPath(ShinColleApi.MOD_ID, "target_trait"));

    private TargetTraitRegistries() {
    }
}
