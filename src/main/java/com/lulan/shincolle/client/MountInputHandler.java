package com.lulan.shincolle.client;

import com.lulan.shincolle.entity.BasicEntityMount;
import com.lulan.shincolle.network.C2SInputPacket;
import com.lulan.shincolle.network.ModNetworking;
import com.lulan.shincolle.reference.Reference;
import com.lulan.shincolle.utility.EntityHelper;
import com.lulan.shincolle.utility.LogHelper;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
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
    private static boolean guiKeyWasDown = false;

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

        // Track the GUI key edge unconditionally so a stale "held" state
        // from before mounting (or while a screen is open) can't cause a
        // false press once riding starts.
        boolean guiKeyDown = onMount && mc.options.keyInventory.isDown();
        boolean guiKeyPressedThisTick = guiKeyDown && !guiKeyWasDown;
        guiKeyWasDown = guiKeyDown;

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
