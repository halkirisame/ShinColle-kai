package com.lulan.shincolle.ai.registry;

import com.lulan.shincolle.init.ModShipBehaviors;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.IForgeRegistry;

import java.util.function.Supplier;

/** Queries on the internal trait registry; no frozen registry is mutated by a lookup. */
public final class ShipTraitRegistries {
    private ShipTraitRegistries() {
    }

    public static boolean isSelectableTrait(ResourceLocation id) {
        return isSelectableTrait(ModShipBehaviors.TRAIT_REGISTRY, id);
    }

    /** Shared production/test boundary, including the not-yet-created registry case. */
    public static boolean isSelectableTrait(Supplier<IForgeRegistry<ShipTraitType>> registry, ResourceLocation id) {
        return ShipBehaviorRegistries.isSelectable(registry, id);
    }
}
