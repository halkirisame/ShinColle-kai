package com.lulan.shincolle.client;

import com.lulan.shincolle.reference.Reference;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.client.settings.KeyModifier;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ShipItemPeekKeyMappings {
    private static final KeyMapping PEEK_SHIP_ITEMS = new KeyMapping("key.shincolle_kai.peek_ship_items",
            KeyConflictContext.IN_GAME, KeyModifier.NONE, InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_LEFT_SHIFT, "key.categories.shincolle_kai.general");

    private ShipItemPeekKeyMappings() {
    }

    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event) {
        event.register(PEEK_SHIP_ITEMS);
    }

    static boolean isDown(Minecraft mc) {
        return PEEK_SHIP_ITEMS.isDefault() ? mc.player.isShiftKeyDown() : HeldKeyInput.isDown(PEEK_SHIP_ITEMS);
    }
}
