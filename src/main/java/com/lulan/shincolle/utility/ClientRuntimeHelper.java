package com.lulan.shincolle.utility;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;

import java.lang.reflect.Method;

/**
 * Runtime-safe bridge for optional client-only API access.
 * Uses reflection so common-side classes can stay dedicated-server safe.
 */
public final class ClientRuntimeHelper {

    private static final String CLIENT_ACCESS_CLASS = "com.lulan.shincolle.client.ClientRuntimeAccess";

    private ClientRuntimeHelper() {
    }

    public static Player getClientPlayer() {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return null;
        }

        try {
            Object playerObj = invokeClientAccess("getPlayer");
            return playerObj instanceof Player player ? player : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static Entity getClientCameraEntity() {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return null;
        }

        try {
            Object camera = invokeClientAccess("getCameraEntity");
            return camera instanceof Entity entity ? entity : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static float getClientFrameTime(float fallback) {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return fallback;
        }

        try {
            Object value = invokeClientAccess("getFrameTime");
            return value instanceof Float f ? f : fallback;
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    public static boolean isControlDown() {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return false;
        }

        try {
            Object result = invokeClientAccess("isControlDown");
            return result instanceof Boolean b && b;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static Object invokeClientAccess(String methodName) throws ReflectiveOperationException {
        Class<?> accessClass = Class.forName(CLIENT_ACCESS_CLASS);
        Method method = accessClass.getMethod(methodName);
        return method.invoke(null);
    }
}
