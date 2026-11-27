package com.lulan.shincolle.client;

import com.lulan.shincolle.reference.Reference;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.GameShuttingDownEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWKeyCallback;
import org.lwjgl.glfw.GLFWKeyCallbackI;
import org.lwjgl.glfw.GLFWWindowFocusCallback;

import java.util.HashSet;
import java.util.Set;
import java.util.function.IntPredicate;

/** Physical sprint input for the default entity-guard modifier, without toggle state. */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SprintKeyInput {
    private static final Set<Integer> HELD_SCAN_CODES = new HashSet<>();
    private static long observedWindow;
    private static GLFWKeyCallbackI previousKeyCallback;
    private static GLFWKeyCallback keyCallback;
    private static GLFWWindowFocusCallback previousFocusCallback;
    private static GLFWWindowFocusCallback focusCallback;

    private SprintKeyInput() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START || keyCallback != null) {
            return;
        }
        // Client ticks begin after vanilla has installed its keyboard callbacks.
        // Registration/setup events can precede KeyboardHandler.setup.
        observedWindow = Minecraft.getInstance().getWindow().getWindow();
        keyCallback = GLFWKeyCallback.create(SprintKeyInput::onKey);
        previousKeyCallback = GLFW.glfwSetKeyCallback(observedWindow, keyCallback);
        focusCallback = GLFWWindowFocusCallback.create((handle, focused) -> {
            onFocusChanged(focused);
            if (previousFocusCallback != null) {
                previousFocusCallback.invoke(handle, focused);
            }
        });
        previousFocusCallback = GLFW.glfwSetWindowFocusCallback(observedWindow, focusCallback);
    }

    static void onKey(long window, int key, int scanCode, int action, int modifiers) {
        // Observe before vanilla: screens and special hotkeys can consume input
        // before either Forge's screen events or InputEvent.Key is fired.
        if (action == GLFW.GLFW_PRESS) {
            HELD_SCAN_CODES.add(scanCode);
        } else if (action == GLFW.GLFW_RELEASE) {
            HELD_SCAN_CODES.remove(scanCode);
        }
        if (previousKeyCallback != null) {
            previousKeyCallback.invoke(window, key, scanCode, action, modifiers);
        }
    }

    static void onFocusChanged(boolean focused) {
        if (!focused) {
            HELD_SCAN_CODES.clear();
        }
    }

    @SubscribeEvent
    public static void onShutdown(GameShuttingDownEvent event) {
        if (keyCallback != null) {
            GLFWKeyCallback current = GLFW.glfwSetKeyCallback(observedWindow, previousKeyCallback);
            if (current != null && current.address() == keyCallback.address()) {
                keyCallback.free();
                keyCallback = null;
                previousKeyCallback = null;
            } else {
                GLFW.glfwSetKeyCallback(observedWindow, current);
            }
        }
        if (focusCallback == null) {
            return;
        }
        GLFWWindowFocusCallback current = GLFW.glfwSetWindowFocusCallback(observedWindow, previousFocusCallback);
        if (current != null && current.address() == focusCallback.address()) {
            focusCallback.free();
            focusCallback = null;
            previousFocusCallback = null;
        } else {
            // A later owner may have chained our callback. Keep its callback and
            // the chain alive until GLFW frees the active callback at window close.
            GLFW.glfwSetWindowFocusCallback(observedWindow, current);
        }
    }

    static boolean isDown(KeyMapping mapping) {
        return isDown(mapping,
                key -> InputConstants.isKeyDown(Minecraft.getInstance().getWindow().getWindow(), key),
                button -> GLFW.glfwGetMouseButton(Minecraft.getInstance().getWindow().getWindow(), button)
                        == GLFW.GLFW_PRESS);
    }

    static boolean isDown(KeyMapping mapping, IntPredicate keyDown, IntPredicate mouseDown) {
        if (mapping.isUnbound() || !mapping.isConflictContextAndModifierActive()) {
            return false;
        }
        InputConstants.Key key = mapping.getKey();
        return switch (key.getType()) {
            case KEYSYM -> keyDown.test(key.getValue());
            case MOUSE -> mouseDown.test(key.getValue());
            // GLFW cannot poll unknown key symbols or synthesize their focus-loss
            // releases; the focus callback clears them independently of client ticks.
            case SCANCODE -> HELD_SCAN_CODES.contains(key.getValue());
        };
    }
}
