package com.lulan.shincolle.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/**
 * Direct client API access kept behind {@code ClientRuntimeHelper}'s
 * distribution guard. Calls in this class are remapped with the rest of the
 * mod, unlike reflective lookups of Minecraft member names.
 */
public final class ClientRuntimeAccess {

    private ClientRuntimeAccess() {
    }

    public static Player getPlayer() {
        return Minecraft.getInstance().player;
    }

    public static Entity getCameraEntity() {
        return Minecraft.getInstance().getCameraEntity();
    }

    public static float getFrameTime() {
        return Minecraft.getInstance().getFrameTime();
    }

    public static boolean isControlDown() {
        return Screen.hasControlDown();
    }
}
