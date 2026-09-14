package com.lulan.shincolle.client;

import com.lulan.shincolle.reference.Reference;
import com.lulan.shincolle.utility.PointerInputModifiers;
import com.lulan.shincolle.utility.PointerInputModifiers.Action;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.client.settings.KeyModifier;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

import java.util.EnumMap;
import java.util.Map;

/** Held input gates; the existing click/scroll handlers remain the action triggers. */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class PointerKeyMappings {
    public static final String CATEGORY = "key.categories.shincolle_kai.pointer";
    private static final Map<Action, KeyMapping> MAPPINGS = new EnumMap<>(Action.class);

    static {
        add(Action.FORMATION_GUI, "formation_gui", GLFW.GLFW_KEY_LEFT_CONTROL);
        add(Action.GUARD_POSITION, "guard_position", GLFW.GLFW_KEY_LEFT_SHIFT);
        add(Action.GUARD_ENTITY, "guard_entity", GLFW.GLFW_KEY_LEFT_CONTROL);
        add(Action.TEAM_MANAGEMENT, "team_management", GLFW.GLFW_KEY_LEFT_SHIFT);
        add(Action.CYCLE_MODE, "cycle_mode", GLFW.GLFW_KEY_LEFT_SHIFT);
    }

    private PointerKeyMappings() {
    }

    private static void add(Action action, String name, int key) {
        MAPPINGS.put(action, new KeyMapping("key.shincolle_kai.pointer." + name,
                KeyConflictContext.IN_GAME, KeyModifier.NONE, InputConstants.Type.KEYSYM, key, CATEGORY));
    }

    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event) {
        MAPPINGS.values().forEach(event::register);
        PointerInputModifiers.installClientReader(PointerKeyMappings::isDown);
    }

    private static boolean isDown(Action action, Player player) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != player || mc.screen != null || !mc.isWindowActive()) {
            return false;
        }
        KeyMapping mapping = MAPPINGS.get(action);
        if (mapping.isDefault()) {
            // Preserve toggle sneak, remapped vanilla sneak/sprint, double-tap sprint,
            // both control keys, and the macOS command-key convention until customized.
            return action == Action.FORMATION_GUI ? Screen.hasControlDown()
                    : PointerInputModifiers.legacyState(action, player);
        }
        if (mapping.isUnbound() || !mapping.isConflictContextAndModifierActive()) {
            return false;
        }
        InputConstants.Key key = mapping.getKey();
        long window = mc.getWindow().getWindow();
        return switch (key.getType()) {
            case KEYSYM -> InputConstants.isKeyDown(window, key.getValue());
            case MOUSE -> GLFW.glfwGetMouseButton(window, key.getValue()) == GLFW.GLFW_PRESS;
            // Unknown key symbols are tracked by the vanilla keyboard callback.
            case SCANCODE -> mapping.isDown();
        };
    }
}
