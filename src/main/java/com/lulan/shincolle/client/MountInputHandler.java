package com.lulan.shincolle.client;

import com.lulan.shincolle.entity.BasicEntityMount;
import com.lulan.shincolle.network.C2SInputPacket;
import com.lulan.shincolle.network.ModNetworking;
import com.lulan.shincolle.reference.Reference;
import com.lulan.shincolle.utility.EntityHelper;
import com.lulan.shincolle.utility.LogHelper;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.event.InputEvent;
import org.lwjgl.glfw.GLFW;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Client-only mount movement key relay.
 * Restores legacy behavior: poll vanilla movement keys while riding a
 * BasicEntityMount and forward them to the server every tick they are held.
 */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class MountInputHandler {

    private static int actionCooldown = 0;
    private static final MountGuiKeyState GUI_KEY = new MountGuiKeyState();
    private static boolean scanCodeGuiKeyDown;

    private MountInputHandler() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        boolean onMount = player != null && mc.level != null && mc.screen == null
                && player.getVehicle() instanceof BasicEntityMount;

        InputConstants.Key key = mc.options.keyInventory.getKey();
        long window = mc.getWindow().getWindow();
        boolean guiKeyDown = switch (key.getType()) {
            case KEYSYM -> key.getValue() != GLFW.GLFW_KEY_UNKNOWN
                    && InputConstants.isKeyDown(window, key.getValue());
            case MOUSE -> GLFW.glfwGetMouseButton(window, key.getValue()) == GLFW.GLFW_PRESS;
            case SCANCODE -> scanCodeGuiKeyDown;
        };
        boolean guiKeyPressedThisTick = GUI_KEY.sample(guiKeyDown, onMount && mc.isWindowActive(),
                mc.options.keyInventory.isDown());

        if (!onMount) {
            actionCooldown = 0;
            return;
        }

        BasicEntityMount mount = (BasicEntityMount) player.getVehicle();

        if (guiKeyPressedThisTick) {
            ModNetworking.sendToServer(new C2SInputPacket(C2SInputPacket.MountGUI));
            return;
        }

        if (actionCooldown > 0) {
            actionCooldown--;
            return;
        }

        int newKeys = 0;
        if (mc.options.keyUp.isDown()) newKeys |= 1;
        if (mc.options.keyDown.isDown()) newKeys |= 2;
        if (mc.options.keyLeft.isDown()) newKeys |= 4;
        if (mc.options.keyRight.isDown()) newKeys |= 8;
        if (mc.options.keyJump.isDown() && (mount.onGround() || EntityHelper.checkEntityIsInLiquid(mount))) {
            newKeys |= 16;
        }

        if (newKeys > 0) {
            actionCooldown = 2;
            // Update the client immediately for smooth prediction; server-only updates stutter.
            mount.keyPressed = newKeys;
            mount.keyTick = 10;
            LogHelper.diag("DIAG: mount key send keys=" + Integer.toBinaryString(newKeys)
                    + " mount=" + mount + " vehicle=" + player.getVehicle());
            ModNetworking.sendToServer(new C2SInputPacket(C2SInputPacket.MountMove, newKeys));
        }
    }

    @SubscribeEvent
    public static void onScreenKeyPressed(ScreenEvent.KeyPressed.Pre event) {
        if (Minecraft.getInstance().options.keyInventory.matches(event.getKeyCode(), event.getScanCode())) {
            GUI_KEY.pressedInScreen();
            scanCodeGuiKeyDown = true;
        }
    }

    @SubscribeEvent
    public static void onScreenKeyReleased(ScreenEvent.KeyReleased.Pre event) {
        if (Minecraft.getInstance().options.keyInventory.matches(event.getKeyCode(), event.getScanCode())) {
            scanCodeGuiKeyDown = false;
        }
    }

    @SubscribeEvent
    public static void onScreenMousePressed(ScreenEvent.MouseButtonPressed.Pre event) {
        if (Minecraft.getInstance().options.keyInventory.matchesMouse(event.getButton())) {
            GUI_KEY.pressedInScreen();
        }
    }

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        if (Minecraft.getInstance().options.keyInventory.matches(event.getKey(), event.getScanCode())) {
            scanCodeGuiKeyDown = event.getAction() != GLFW.GLFW_RELEASE;
        }
    }

    /**
     * Prevent vanilla's own inventory-key handling from opening the survival
     * inventory screen while riding a mount; the ship GUI opens via the
     * server-driven MountGUI packet instead (see onClientTick above).
     */
    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event) {
        if (!(event.getNewScreen() instanceof InventoryScreen)) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.getVehicle() instanceof BasicEntityMount) {
            event.setNewScreen(null);
        }
    }
}
