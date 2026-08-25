package com.lulan.shincolle.client;

import java.util.ArrayList;
import java.util.List;

import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.network.C2SInputPacket;
import com.lulan.shincolle.network.ModNetworking;
import com.lulan.shincolle.reference.Reference;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Shows what a ship is carrying while the player sneaks and looks at it.
 * <p>
 * A ship's inventory lives on the server unless its screen is open, so this asks
 * for the list and draws the reply. The request repeats slowly while the player
 * keeps looking, which is enough to notice items being added or taken.
 * <p>
 * Equipment, third-party (Curios) equipment, and cargo are drawn as three
 * separate grids stacked vertically with a gap between them, rather than one
 * mixed grid, so it's obvious at a glance which category an item belongs to.
 */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ShipItemPeekHandler {

    /** How often to re-ask while the player holds the pose. */
    private static final int RequestIntervalTicks = 10;
    /** Drop the cache once the reply is this old, so stale contents never linger. */
    private static final long CacheLifetimeMs = 3000L;

    private static final int IconSize = 18;
    private static final int Columns = 9;
    private static final int Padding = 4;
    /** Vertical gap between the equip/Curios/cargo sections. */
    private static final int SectionGap = 5;

    private static int cachedEntityId = -1;
    private static List<ItemStack> cachedEquip = new ArrayList<>();
    private static List<ItemStack> cachedCurios = new ArrayList<>();
    private static List<ItemStack> cachedCargo = new ArrayList<>();
    private static long cachedAtMs;
    private static int requestCooldown;

    private ShipItemPeekHandler() {
    }

    /** Called from the network thread's work queue when the reply arrives. */
    public static void acceptItemList(int entityId, List<ItemStack> equip, List<ItemStack> curios,
                                       List<ItemStack> cargo) {
        cachedEntityId = entityId;
        cachedEquip = equip;
        cachedCurios = curios;
        cachedCargo = cargo;
        cachedAtMs = System.currentTimeMillis();
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        if (requestCooldown > 0) {
            requestCooldown--;
        }

        BasicEntityShip ship = getPeekTarget();
        if (ship == null) {
            // Looking away clears it immediately; a stale panel is worse than none.
            cachedEntityId = -1;
            return;
        }

        if (requestCooldown <= 0) {
            requestCooldown = RequestIntervalTicks;
            ModNetworking.sendToServer(
                    new C2SInputPacket(C2SInputPacket.Request_ShipItemList, ship.getId()));
        }
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        BasicEntityShip ship = getPeekTarget();
        if (ship == null || ship.getId() != cachedEntityId) {
            return;
        }

        if (System.currentTimeMillis() - cachedAtMs > CacheLifetimeMs) {
            return;
        }

        renderPanel(event.getGuiGraphics(), ship);
    }

    /**
     * The ship the player is sneaking at, or null when the overlay should not show.
     */
    private static BasicEntityShip getPeekTarget() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.screen != null) {
            return null;
        }

        if (!mc.player.isShiftKeyDown() || mc.options.hideGui) {
            return null;
        }

        if (!(mc.hitResult instanceof EntityHitResult hit)) {
            return null;
        }

        Entity entity = hit.getEntity();
        return entity instanceof BasicEntityShip ship && ship.isAlive() ? ship : null;
    }

    private static int sectionHeight(List<ItemStack> stacks) {
        if (stacks.isEmpty()) {
            return 0;
        }
        int rows = (stacks.size() + Columns - 1) / Columns;
        return rows * IconSize;
    }

    private static int sectionCols(List<ItemStack> stacks) {
        return Math.min(stacks.size(), Columns);
    }

    private static void renderPanel(GuiGraphics graphics, BasicEntityShip ship) {
        Minecraft mc = Minecraft.getInstance();
        Component title = ship.getDisplayName();

        boolean allEmpty = cachedEquip.isEmpty() && cachedCurios.isEmpty() && cachedCargo.isEmpty();

        int maxCols = Math.max(sectionCols(cachedEquip),
                Math.max(sectionCols(cachedCurios), sectionCols(cachedCargo)));
        int gridWidth = Math.max(maxCols * IconSize, mc.font.width(title));
        int titleHeight = mc.font.lineHeight + Padding;

        int bodyHeight;
        if (allEmpty) {
            bodyHeight = mc.font.lineHeight;
        } else {
            bodyHeight = sectionHeight(cachedEquip) + sectionHeight(cachedCurios) + sectionHeight(cachedCargo);
            int sections = (cachedEquip.isEmpty() ? 0 : 1) + (cachedCurios.isEmpty() ? 0 : 1)
                    + (cachedCargo.isEmpty() ? 0 : 1);
            if (sections > 1) {
                bodyHeight += SectionGap * (sections - 1);
            }
        }

        int panelWidth = gridWidth + Padding * 2;
        int panelHeight = titleHeight + Math.max(bodyHeight, mc.font.lineHeight) + Padding * 2;

        // Sits just below the crosshair, where the player is already looking -
        // but pulled up short of the hotbar if three sections make the panel
        // taller than the space between the crosshair and the hotbar allows.
        int left = (graphics.guiWidth() - panelWidth) / 2;
        int top = graphics.guiHeight() / 2 + 16;
        int hotbarTop = graphics.guiHeight() - 24;
        if (top + panelHeight > hotbarTop) {
            top = hotbarTop - panelHeight;
        }

        graphics.fill(left, top, left + panelWidth, top + panelHeight, 0xC0100010);
        graphics.renderOutline(left, top, panelWidth, panelHeight, 0x50FFFFFF);

        graphics.drawString(mc.font, title, left + Padding, top + Padding, 0xFFFFFF, true);

        int y = top + Padding + titleHeight;
        if (allEmpty) {
            graphics.drawString(mc.font, Component.translatable("gui.shincolle_kai.peek.empty"),
                    left + Padding, y, 0xA0A0A0, true);
            return;
        }

        y = drawSection(graphics, mc, cachedEquip, left + Padding, y);
        y = drawSection(graphics, mc, cachedCurios, left + Padding, y);
        drawSection(graphics, mc, cachedCargo, left + Padding, y);
    }

    /** Draws one section's grid and returns the y to start the next section at (gap included). */
    private static int drawSection(GuiGraphics graphics, Minecraft mc, List<ItemStack> stacks, int x, int y) {
        if (stacks.isEmpty()) {
            return y;
        }
        for (int i = 0; i < stacks.size(); i++) {
            int ix = x + (i % Columns) * IconSize;
            int iy = y + (i / Columns) * IconSize;
            ItemStack stack = stacks.get(i);
            graphics.renderItem(stack, ix, iy);
            graphics.renderItemDecorations(mc.font, stack, ix, iy);
        }
        return y + sectionHeight(stacks) + SectionGap;
    }
}
