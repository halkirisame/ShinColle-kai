package com.lulan.shincolle.ai.registry;

import net.minecraftforge.registries.RegistryBuilder;

/** The settings both internal registries are built with. */
public final class ShipBehaviorRegistryBuilders {

    private ShipBehaviorRegistryBuilders() {
    }

    public static <T> RegistryBuilder<T> internal() {
        return new RegistryBuilder<T>().disableSaving().disableSync().disableOverrides();
    }
}
