package com.lulan.shincolle.client.visual;

import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.Mob;

import java.lang.reflect.Field;

/** Bootstraps the vanilla registries before the first Mob class is initialised, then builds the test entity. */
public final class PostureTestShips {
    private static boolean ready;

    private PostureTestShips() { }

    public static synchronized Mob create(boolean noFuel, boolean sitting, boolean riding, boolean sprinting) {
        try {
            if (!ready) {
                SharedConstants.tryDetectVersion();
                Field bootstrapped = Bootstrap.class.getDeclaredField("isBootstrapped");
                bootstrapped.setAccessible(true);
                if (!bootstrapped.getBoolean(null)) {
                    bootstrapped.setBoolean(null, true);
                    BuiltInRegistries.bootStrap();
                }
                ready = true;
            }
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
        return PostureTestShip.build(noFuel, sitting, riding, sprinting);
    }
}
