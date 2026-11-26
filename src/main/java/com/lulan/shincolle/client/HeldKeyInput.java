package com.lulan.shincolle.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/** Reads held custom bindings without consuming click events. */
final class HeldKeyInput {
    private HeldKeyInput() {
    }

    static boolean isDown(KeyMapping mapping) {
        if (mapping.isUnbound() || !mapping.isConflictContextAndModifierActive()) {
            return false;
        }
        InputConstants.Key key = mapping.getKey();
        long window = Minecraft.getInstance().getWindow().getWindow();
        return switch (key.getType()) {
            case KEYSYM -> InputConstants.isKeyDown(window, key.getValue());
            case MOUSE -> GLFW.glfwGetMouseButton(window, key.getValue()) == GLFW.GLFW_PRESS;
            // Unknown key symbols are tracked by the vanilla keyboard callback.
            case SCANCODE -> mapping.isDown();
        };
    }
}
