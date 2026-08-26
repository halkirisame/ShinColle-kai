package com.lulan.shincolle.client;

import com.lulan.shincolle.capability.CapaTeitoku;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.init.ModItems;
import com.lulan.shincolle.item.PointerItem;
import com.lulan.shincolle.network.C2SGUIInputPacket;
import com.lulan.shincolle.network.ModNetworking;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;
import com.lulan.shincolle.utility.ParticleHelper;
import com.lulan.shincolle.utility.TeamHelper;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Client-only pointer input bridge.
 * <p>
 * Restores legacy key behavior:
 * - Sprint + hotbar 1-9: switch selected team (without changing selected hotbar slot)
 * - Player list key (TAB by default) on main-hand pointer: toggle caress mode (0-2 <-> 3-5)
 * <p>
 * Adds the mode switch itself:
 * - Shift + mouse wheel: step through single / group / formation in both directions
 * - Shift held: draw the three modes at the top left with the current one marked
 * <p>
 * Also draws where your ships have been told to go. The destination already lives on the
 * ship ({@code ID.M.GuardX/Y/Z}) and is already synced ({@code S2CEntitySyncPacket.syncGuard}),
 * so this reads what the client has rather than introducing state of its own.
 */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class PointerInputHandler {

    /** Panel inset from the top-left corner, in GUI pixels. */
    private static final int PanelX = 6;
    private static final int PanelY = 6;
    /** Line height for the three-row carousel. */
    private static final int LineHeight = 11;
    /** Left padding for mode names, leaving room for the marker on the current row. */
    private static final int TextIndent = 12;

    private static final int ColorCurrent = 0xFFFFFF55;
    private static final int ColorOther = 0xFF9A9A9A;
    private static final int ColorMarker = 0xFFFFFF55;
    private static final int ColorBackdrop = 0x90000000;

    private PointerInputHandler() {
    }

    /**
     * Shift + wheel steps the pointer mode instead of the hotbar.
     * <p>
     * Scrolling up moves to the previous mode, matching the vanilla hotbar where scrolling
     * up lowers the selected index. The event is cancelled so the hotbar does not move at
     * the same time.
     */
    @SubscribeEvent
    public static void onMouseScrolling(InputEvent.MouseScrollingEvent event) {
        double delta = event.getScrollDelta();
        if (delta == 0D) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        ItemStack pointer = getActivePointer(mc);
        if (pointer.isEmpty()) {
            return;
        }

        LocalPlayer player = mc.player;
        if (player == null) {
            return;
        }
        if (mc.options.keySprint.isDown() || player.isSprinting()) {
            return;
        }

        int direction = delta > 0D ? -1 : 1;
        int mode = PointerItem.cycleMode(PointerItem.getMode(pointer), direction);
        PointerItem.setMode(pointer, mode);

        ModNetworking.sendToServer(new C2SGUIInputPacket(
                C2SGUIInputPacket.SyncPlayerItem,
                new int[]{player.getId(), 0, mode}));

        event.setCanceled(true);
    }

    /** Draw the mode carousel while shift is held with a pointer in hand. */
    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) {
            return;
        }

        ItemStack pointer = getActivePointer(mc);
        if (pointer.isEmpty()) {
            return;
        }

        renderModePanel(event.getGuiGraphics(), mc, PointerItem.getMode(pointer));
    }

    /**
     * The three rows always read [previous, current, next] with the marker fixed on the
     * middle row, so the list rotates under a stationary cursor rather than the cursor
     * travelling down a fixed list. That keeps the next mode in either direction visible.
     */
    private static void renderModePanel(GuiGraphics graphics, Minecraft mc, int mode) {
        Component[] rows = new Component[]{
            modeName(PointerItem.cycleMode(mode, -1)),
            modeName(mode),
            modeName(PointerItem.cycleMode(mode, 1)),
        };

        int widest = 0;
        for (Component row : rows) {
            widest = Math.max(widest, mc.font.width(row));
        }

        graphics.fill(PanelX - 3, PanelY - 3,
                PanelX + TextIndent + widest + 3, PanelY + LineHeight * rows.length,
                ColorBackdrop);

        for (int i = 0; i < rows.length; i++) {
            boolean current = i == 1;
            int y = PanelY + i * LineHeight;
            if (current) {
                graphics.drawString(mc.font, ">", PanelX + 2, y, ColorMarker, false);
            }
            graphics.drawString(mc.font, rows[i], PanelX + TextIndent, y,
                    current ? ColorCurrent : ColorOther, false);
        }
    }

    private static Component modeName(int mode) {
        return Component.translatable("gui." + Reference.MOD_ID + ".pointer"
                + PointerItem.baseMode(mode));
    }

    /**
     * The pointer this input applies to, or empty when the mode controls should stay out of
     * the way. Shared by the wheel handler and the overlay so that what is shown and what
     * responds cannot drift apart.
     */
    private static ItemStack getActivePointer(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.screen != null) {
            return ItemStack.EMPTY;
        }
        if (!player.isShiftKeyDown() || mc.options.keySprint.isDown() || player.isSprinting()) {
            return ItemStack.EMPTY;
        }
        return getPointerInUse(player);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.screen != null) {
            return;
        }

        ItemStack pointerInUse = getPointerInUse(player);
        if (pointerInUse.isEmpty()) {
            return;
        }

        renderDestinationMarkers(mc, player, pointerInUse);

        boolean sprintDown = mc.options.keySprint.isDown();

        if (sprintDown) {
            handleSprintTeamSwitch(player, mc);
            return;
        }

        ItemStack mainHand = player.getMainHandItem();
        if (!mainHand.isEmpty() && mainHand.getItem() == ModItems.POINTER.get()) {
            while (mc.options.keyPlayerList.consumeClick()) {
                int mode = PointerItem.toggleCaressMode(PointerItem.getMode(mainHand));
                PointerItem.setMode(mainHand, mode);

                ModNetworking.sendToServer(new C2SGUIInputPacket(
                        C2SGUIInputPacket.SyncPlayerItem,
                        new int[]{player.getId(), 0, mode}));
            }
        }
    }


    /** Ticks between destination marker refreshes. Upstream drew its waypoint arrow every 8. */
    private static final int MarkerIntervalTicks = 8;
    /** Ticks between drawing the line from a ship to its destination. Upstream used 16. */
    private static final int TrailIntervalTicks = 16;
    /** How far from the player to look for owned ships, in blocks. */
    private static final double MarkerSearchRange = 64D;
    /** Spacing of the trail particles, in blocks. */
    private static final double TrailSpacing = 2D;
    /** Cap on trail particles per ship, so a distant order does not flood the screen. */
    private static final int TrailMaxSteps = 48;

    /** Own counter: goal/entity tick counts are parity-gated and would skip (2-3). */
    private static int markerTick;

    /**
     * Draw where each of your ships has been ordered to go.
     * <p>
     * Only while a pointer is held and not in caress mode, so it stays out of the way the
     * rest of the time. Caress is checked on the raw mode rather than {@link
     * PointerItem#baseMode}, which would strip the band and let the markers show there too.
     */
    private static void renderDestinationMarkers(Minecraft mc, LocalPlayer player, ItemStack pointer) {
        if (mc.level == null || mc.options.hideGui) {
            return;
        }
        if (PointerItem.getMode(pointer) > PointerItem.MODE_FORMATION) {
            return;
        }

        markerTick++;
        boolean drawMarker = markerTick % MarkerIntervalTicks == 0;
        boolean drawTrail = markerTick % TrailIntervalTicks == 0;
        if (!drawMarker && !drawTrail) {
            return;
        }

        AABB search = player.getBoundingBox().inflate(MarkerSearchRange);
        for (BasicEntityShip ship : mc.level.getEntitiesOfClass(BasicEntityShip.class, search)) {
            if (!ship.isAlive() || !TeamHelper.checkSameOwner(player, ship)) {
                continue;
            }

            if (!ship.hasGuardDestination() || !ship.isGuardedInCurrentDimension()) {
                continue;
            }

            int destY = ship.getStateMinor(ID.M.GuardY);
            double destX = ship.getStateMinor(ID.M.GuardX) + 0.5D;
            double destZ = ship.getStateMinor(ID.M.GuardZ) + 0.5D;

            if (drawMarker) {
                ParticleHelper.spawnAttackParticleAt(mc.level, destX, destY + 0.5D, destZ, 0D, 1D, 0D, 2);
            }
            if (drawTrail) {
                drawTrail(mc, ship, destX, destY + 0.5D, destZ);
            }
        }
    }

    private static void drawTrail(Minecraft mc, BasicEntityShip ship,
                                  double destX, double destY, double destZ) {
        double dx = destX - ship.getX();
        double dy = destY - (ship.getY() + ship.getBbHeight() * 0.5D);
        double dz = destZ - ship.getZ();
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (distance < TrailSpacing) {
            return;
        }

        int steps = Math.min((int) (distance / TrailSpacing), TrailMaxSteps);
        for (int i = 1; i <= steps; i++) {
            double t = (double) i / (steps + 1);
            ParticleHelper.spawnAttackParticleAt(mc.level,
                    ship.getX() + dx * t,
                    ship.getY() + ship.getBbHeight() * 0.5D + dy * t,
                    ship.getZ() + dz * t,
                    0D, 0D, 0D, 29);
        }
    }

    private static void handleSprintTeamSwitch(LocalPlayer player, Minecraft mc) {
        int originalSlot = player.getInventory().selected;
        boolean consumed = false;

        for (int i = 0; i < mc.options.keyHotbarSlots.length; i++) {
            while (mc.options.keyHotbarSlots[i].consumeClick()) {
                consumed = true;
                if (i < CapaTeitoku.TEAM_NUM) {
                    ModNetworking.sendToServer(new C2SGUIInputPacket(
                            C2SGUIInputPacket.SetSelect,
                            new int[]{player.getId(), 0, i}));
                }
            }
        }

        if (consumed) {
            // [PORT] 1.10.2 -> 1.20.1: keep pointer in hand while using sprint+hotbar team
            // shortcuts.
            player.getInventory().selected = originalSlot;
        }
    }

    private static ItemStack getPointerInUse(LocalPlayer player) {
        ItemStack main = player.getMainHandItem();
        if (!main.isEmpty() && main.getItem() == ModItems.POINTER.get()) {
            return main;
        }

        ItemStack off = player.getOffhandItem();
        if (!off.isEmpty() && off.getItem() == ModItems.POINTER.get()) {
            return off;
        }

        return ItemStack.EMPTY;
    }
}
