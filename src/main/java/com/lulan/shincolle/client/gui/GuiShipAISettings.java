package com.lulan.shincolle.client.gui;

import com.lulan.shincolle.reference.Reference;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.reference.ID;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

/**
 * Standalone window holding every AI setting for a ship.
 *
 * <p>Replaces the old 66x77px strip on the right of {@link GuiShipInventory},
 * which spread 14 toggles, 5 sliders and three separate grids across 8 tab
 * pages of 11x11px hit boxes with no tooltips. Everything now lives in one
 * scrollable list with full-width rows and a description on hover.
 *
 * <p>Follows {@link GuiShipEquipDetail}: a plain {@link Screen} drawn entirely
 * with {@code fill()} so it needs no new texture, returning to its parent on
 * close. The one texture it does use is the shared inventory sheet, purely to
 * reuse the familiar check/cross toggle sprite.
 */
public class GuiShipAISettings extends Screen {

    private static final ResourceLocation TEXTURE = new ResourceLocation(Reference.MOD_ID,
            "textures/gui/guishipinventory.png");

    private static final int PANEL_WIDTH = 230;
    private static final int PANEL_HEIGHT = 200;
    private static final int LIST_PADDING = 8;
    private static final int HEADER_HEIGHT = 20;
    private static final int FOOTER_HEIGHT = 6;

    private static final int ROW_H = 14;
    private static final int SECTION_H = 16;
    private static final int SLIDER_H = 26;
    private static final int GRID_ROW_H = 13;
    private static final int CELL = 11;

    /** Widened track; drag positions still normalise to the legacy 0..42 range. */
    private static final int TRACK_W = 168;
    private static final int SLIDER_MAX_POS = 42;

    // ========== Row model ==========

    private sealed interface Row permits Section, Toggle, Slider, TaskPick, MaskBit, SideGrid {
        int height(BasicEntityShip ship);
    }

    /** Non-interactive heading. */
    private record Section(String key, String fallback) implements Row {
        public int height(BasicEntityShip ship) {
            return SECTION_H;
        }
    }

    /**
     * A boolean {@link ID.F} flag.
     *
     * @param condFlag  capability gate, or -1 when always available. Unlike the
     *                  old strip - which hid gated rows but kept their slot,
     *                  leaving unexplained gaps - a gated-off row still renders,
     *                  greyed, and says why in its tooltip.
     */
    private record Toggle(int flagId, int buttonId, int condFlag,
                          String key, String fallback,
                          String descKey, String descFallback,
                          String lockKey, String lockFallback) implements Row {
        public int height(BasicEntityShip ship) {
            return ROW_H;
        }

        boolean available(BasicEntityShip ship) {
            return condFlag < 0 || ship.getStateFlag(condFlag);
        }
    }

    /** One of the five legacy slider bars, addressed by its original bar index. */
    private record Slider(int barIndex, String key, String fallback,
                          String descKey, String descFallback) implements Row {
        public int height(BasicEntityShip ship) {
            return SLIDER_H;
        }
    }

    /** The four work tasks as a radio group; clicking the active one clears it. */
    private record TaskPick(String key, String fallback,
                            String descKey, String descFallback) implements Row {
        public int height(BasicEntityShip ship) {
            return ROW_H + 18;
        }
    }

    /** A single bit of {@link ID.M#TaskSide}. */
    private record MaskBit(int bit, String key, String fallback,
                           String descKey, String descFallback) implements Row {
        public int height(BasicEntityShip ship) {
            return ROW_H;
        }
    }

    /** Six direction cells of one TaskSide group (0=input, 1=output, 2=fuel). */
    private record SideGrid(int group, String key, String fallback,
                            String descKey, String descFallback) implements Row {
        public int height(BasicEntityShip ship) {
            return ROW_H + GRID_ROW_H;
        }
    }

    /** Column order of every TaskSide group, matching InventoryHelper's bit layout. */
    private static final String[] SIDE_LETTERS = {"D", "U", "N", "S", "W", "E"};
    private static final String[] SIDE_NAMES = {"Down", "Up", "North", "South", "West", "East"};

    private static final List<Row> ROWS = List.of(
            new Section("gui.shincolle_kai.aisec.attack", "Attack"),
            new Toggle(ID.F.UseMelee, ID.B.ShipInv_Melee, -1,
                    "gui.shincolle_kai.canmelee", "Melee Attack",
                    "gui.shincolle_kai.aidesc.melee", "Attack by ramming the target. Costs no ammo.",
                    "", ""),
            new Toggle(ID.F.UseAmmoLight, ID.B.ShipInv_AmmoLight, ID.F.AtkType_Light,
                    "gui.shincolle_kai.canlightattack", "Light Ammo",
                    "gui.shincolle_kai.aidesc.atklight", "Cannon fire using Grudge Ammo.",
                    "gui.shincolle_kai.ailock.atklight", "This ship has no light cannon."),
            new Toggle(ID.F.UseAmmoHeavy, ID.B.ShipInv_AmmoHeavy, ID.F.AtkType_Heavy,
                    "gui.shincolle_kai.canheavyattack", "Heavy Ammo",
                    "gui.shincolle_kai.aidesc.atkheavy", "Missile attack using Heavy Grudge Ammo.",
                    "gui.shincolle_kai.ailock.atkheavy", "This ship has no heavy cannon."),
            new Toggle(ID.F.UseAirLight, ID.B.ShipInv_AirLight, ID.F.AtkType_AirLight,
                    "gui.shincolle_kai.canairlightattack", "Light Aircraft",
                    "gui.shincolle_kai.aidesc.atkairlight", "Launch light aircraft, spending Grudge Ammo.",
                    "gui.shincolle_kai.ailock.aircraft", "This ship cannot carry aircraft."),
            new Toggle(ID.F.UseAirHeavy, ID.B.ShipInv_AirHeavy, ID.F.AtkType_AirHeavy,
                    "gui.shincolle_kai.canairheavyattack", "Heavy Aircraft",
                    "gui.shincolle_kai.aidesc.atkairheavy", "Launch heavy aircraft, spending Heavy Grudge Ammo.",
                    "gui.shincolle_kai.ailock.aircraft", "This ship cannot carry aircraft."),
            new Toggle(ID.F.UseRingEffect, ID.B.ShipInv_AuraEffect, ID.F.HaveRingEffect,
                    "gui.shincolle_kai.auraeffect", "Special Ability",
                    "gui.shincolle_kai.aidesc.aura", "Use this ship's class ability and its wedding ring aura.",
                    "gui.shincolle_kai.ailock.aura", "Requires a wedding ring."),

            new Section("gui.shincolle_kai.aisec.target", "Targeting"),
            new Toggle(ID.F.PassiveAI, ID.B.ShipInv_TarAI, -1,
                    "gui.shincolle_kai.targetAI", "Passive",
                    "gui.shincolle_kai.aidesc.passive",
                    "Only strike back after being attacked, instead of seeking targets out.",
                    "", ""),
            new Toggle(ID.F.OnSightChase, ID.B.ShipInv_OnSightAI, -1,
                    "gui.shincolle_kai.onsightAI", "Line of Sight Only",
                    "gui.shincolle_kai.aidesc.onsight",
                    "Drop the target the moment line of sight breaks, rather than chasing it.",
                    "", ""),
            new Toggle(ID.F.PVPFirst, ID.B.ShipInv_PVPAI, -1,
                    "gui.shincolle_kai.ai.pvp", "PVP Mode",
                    "gui.shincolle_kai.aidesc.pvp", "Prefer other players' ships as targets.",
                    "", ""),
            new Toggle(ID.F.AntiAir, ID.B.ShipInv_AAAI, -1,
                    "gui.shincolle_kai.ai.aa", "Anti-Air",
                    "gui.shincolle_kai.aidesc.aa", "Prioritise aircraft and flying mobs.",
                    "", ""),
            new Toggle(ID.F.AntiSS, ID.B.ShipInv_ASMAI, -1,
                    "gui.shincolle_kai.ai.asm", "Anti-Submarine",
                    "gui.shincolle_kai.aidesc.asm", "Prioritise submarines and other invisible targets.",
                    "", ""),
            new Toggle(ID.F.TimeKeeper, ID.B.ShipInv_TIMEKEEPAI, -1,
                    "gui.shincolle_kai.ai.timekeeper", "Timekeeper",
                    "gui.shincolle_kai.aidesc.timekeeper", "Announce the time on the hour.",
                    "", ""),

            new Section("gui.shincolle_kai.aisec.move", "Movement"),
            new Slider(0, "gui.shincolle_kai.followmin", "Follow (Min)",
                    "gui.shincolle_kai.aidesc.followmin",
                    "Stop following once the ship is closer to you than this."),
            new Slider(1, "gui.shincolle_kai.followmax", "Follow (Max)",
                    "gui.shincolle_kai.aidesc.followmax",
                    "Start following once the ship is further from you than this."),
            new Slider(2, "gui.shincolle_kai.fleehp", "Flee HP%",
                    "gui.shincolle_kai.aidesc.fleehp",
                    "Below this share of max HP the ship breaks off and runs back to you."),
            new Slider(3, "gui.shincolle_kai.ai.wpstay", "Waypoint Stay",
                    "gui.shincolle_kai.aidesc.wpstay", "How long to wait at each waypoint."),

            new Section("gui.shincolle_kai.aisec.supply", "Supply"),
            new Toggle(ID.F.PickItem, ID.B.ShipInv_PickitemAI, ID.F.CanPickItem,
                    "gui.shincolle_kai.ai.pickitem", "Pick Up Items",
                    "gui.shincolle_kai.aidesc.pickitem",
                    "Collect nearby dropped items. Also loosens the follow distance while hunting.",
                    "gui.shincolle_kai.ailock.pickitem", "This ship cannot pick up items."),
            new Toggle(ID.F.AutoPump, ID.B.ShipInv_AutoPump, -1,
                    "gui.shincolle_kai.autopump", "Auto Pump",
                    "gui.shincolle_kai.aidesc.autopump", "Automatically move fluids into the ship's tank.",
                    "", ""),
            new Slider(4, "gui.shincolle_kai.autocombatration", "Auto Combat Ration",
                    "gui.shincolle_kai.aidesc.autocr",
                    "Eat a combat ration once morale drops to this level. Off disables it."),


            new Section("gui.shincolle_kai.aisec.task", "Work Task"),
            new TaskPick("gui.shincolle_kai.aisec.taskpick", "Task",
                    "gui.shincolle_kai.aidesc.taskpick",
                    "Pick a job for this ship. Clicking the active job again clears it."),
            new MaskBit(18, "gui.shincolle_kai.crane.usemeta", "Match Metadata",
                    "gui.shincolle_kai.aidesc.usemeta",
                    "When crafting, require item metadata to match as well."),
            new MaskBit(20, "gui.shincolle_kai.crane.usenbt", "Match NBT",
                    "gui.shincolle_kai.aidesc.usenbt",
                    "When crafting, require NBT tags to match as well."),
            new SideGrid(0, "gui.shincolle_kai.ai.inputside", "Input Sides",
                    "gui.shincolle_kai.aidesc.inputside",
                    "Which faces of the target block the ship inserts into."),
            new SideGrid(1, "gui.shincolle_kai.ai.outputside", "Output Sides",
                    "gui.shincolle_kai.aidesc.outputside",
                    "Which faces of the target block the ship pulls results from."),
            new SideGrid(2, "gui.shincolle_kai.ai.fuelside", "Fuel Sides",
                    "gui.shincolle_kai.aidesc.fuelside",
                    "Which faces of the target block the ship refuels through.")
    );

    // ========== State ==========

    private final Screen parent;
    private final BasicEntityShip ship;
    private int scrollOffset = 0;
    private int panelLeft;
    private int panelTop;
    private int listTop;
    private int listBottom;

    /** Bar index currently being dragged, or -1. */
    private int draggingSlider = -1;
    private int dragPos = 0;

    public GuiShipAISettings(Screen parent, BasicEntityShip ship) {
        super(Component.literal(tr("gui.shincolle_kai.aisettings.title", "Settings")));
        this.parent = parent;
        this.ship = ship;
    }

    private static String tr(String key, String fallback) {
        String localized = I18n.get(key);
        return localized.equals(key) ? fallback : localized;
    }

    @Override
    protected void init() {
        this.panelLeft = (this.width - PANEL_WIDTH) / 2;
        this.panelTop = (this.height - PANEL_HEIGHT) / 2;
        this.listTop = panelTop + HEADER_HEIGHT;
        this.listBottom = panelTop + PANEL_HEIGHT - FOOTER_HEIGHT;
        this.scrollOffset = 0;

        this.addRenderableWidget(Button.builder(Component.literal("X"), b -> onClose())
                .bounds(panelLeft + PANEL_WIDTH - 18, panelTop + 3, 14, 14)
                .build());
    }

    // ========== Geometry ==========

    private int contentHeight() {
        int total = 0;
        for (Row row : ROWS) {
            total += row.height(ship);
        }
        return total;
    }

    private int rowLeft() {
        return panelLeft + LIST_PADDING;
    }

    private int rowWidth() {
        return PANEL_WIDTH - LIST_PADDING * 2 - 6;
    }

    private int trackLeft() {
        return rowLeft() + 4;
    }

    // ========== Rendering ==========

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);

        graphics.fill(panelLeft, panelTop, panelLeft + PANEL_WIDTH, panelTop + PANEL_HEIGHT, 0xE0202020);
        graphics.fill(panelLeft, panelTop, panelLeft + PANEL_WIDTH, panelTop + HEADER_HEIGHT - 2, 0xE0303030);
        graphics.drawString(this.font, this.title, panelLeft + 6, panelTop + 6, 0xFFFFFF, false);

        List<Component> tooltip = null;

        graphics.enableScissor(panelLeft, listTop, panelLeft + PANEL_WIDTH, listBottom);
        int y = listTop - scrollOffset;
        for (Row row : ROWS) {
            int h = row.height(ship);
            if (y + h >= listTop && y <= listBottom) {
                boolean hovered = !(row instanceof Section)
                        && mouseY >= y && mouseY < y + h
                        && mouseX >= rowLeft() && mouseX < rowLeft() + rowWidth()
                        && mouseY >= listTop && mouseY < listBottom;
                if (hovered) {
                    graphics.fill(rowLeft(), y, rowLeft() + rowWidth(), y + h, 0x30FFFFFF);
                }
                renderRow(graphics, row, y);
                if (hovered) {
                    tooltip = buildTooltip(row);
                }
            }
            y += h;
        }
        graphics.disableScissor();

        int viewHeight = listBottom - listTop;
        int total = contentHeight();
        if (total > viewHeight) {
            int barX = panelLeft + PANEL_WIDTH - 6;
            graphics.fill(barX, listTop, barX + 4, listBottom, 0x40FFFFFF);
            int maxScroll = total - viewHeight;
            int barHeight = Math.max(10, viewHeight * viewHeight / total);
            int barY = listTop + scrollOffset * (viewHeight - barHeight) / Math.max(1, maxScroll);
            graphics.fill(barX, barY, barX + 4, barY + barHeight, 0xFFAAAAAA);
        }

        super.render(graphics, mouseX, mouseY, partialTick);

        if (tooltip != null) {
            graphics.renderComponentTooltip(this.font, tooltip, mouseX, mouseY);
        }
    }

    private void renderRow(GuiGraphics graphics, Row row, int y) {
        int x = rowLeft();
        int w = rowWidth();

        if (row instanceof Section s) {
            graphics.drawString(this.font, tr(s.key(), s.fallback()), x, y + 5, 0xFFD24A, false);
            graphics.fill(x, y + SECTION_H - 2, x + w, y + SECTION_H - 1, 0x40FFFFFF);
        } else if (row instanceof Toggle t) {
            boolean available = t.available(ship);
            boolean on = ship.getStateFlag(t.flagId());
            graphics.drawString(this.font, tr(t.key(), t.fallback()), x + 2, y + 3,
                    available ? 0xE0E0E0 : 0x808080, false);
            drawToggleSprite(graphics, x + w - CELL - 2, y + 1, on);
            if (!available) {
                graphics.fill(x, y, x + w, y + ROW_H, 0x80000000);
            }
        } else if (row instanceof Slider sl) {
            int barIndex = sl.barIndex();
            boolean dragging = draggingSlider == barIndex;
            int pos = dragging ? dragPos : stateToBarPos(barIndex, getSliderState(barIndex));
            int value = dragging ? barPosToState(barIndex, dragPos) : getSliderState(barIndex);

            graphics.drawString(this.font, tr(sl.key(), sl.fallback()), x + 2, y + 2, 0xE0E0E0, false);
            String valueText = barIndex == 4 ? getMoraleLevelName(value)
                    : barIndex == 3 ? value + "s"
                    : String.valueOf(value);
            graphics.drawString(this.font, valueText,
                    x + w - 2 - this.font.width(valueText), y + 2,
                    dragging ? 0xFF6666 : 0xFFFF55, false);

            int tx = trackLeft();
            int ty = y + 16;
            graphics.fill(tx, ty, tx + TRACK_W, ty + 2, 0x60FFFFFF);
            int hx = tx + pos * TRACK_W / SLIDER_MAX_POS;
            graphics.fill(hx - 2, ty - 4, hx + 3, ty + 6, dragging ? 0xFFFF6666 : 0xFFCCCCCC);
        } else if (row instanceof MaskBit mb) {
            boolean on = (ship.getStateMinor(ID.M.TaskSide) & (1 << mb.bit())) != 0;
            graphics.drawString(this.font, tr(mb.key(), mb.fallback()), x + 2, y + 3, 0xE0E0E0, false);
            drawToggleSprite(graphics, x + w - CELL - 2, y + 1, on);
        } else if (row instanceof TaskPick tp) {
            graphics.drawString(this.font, tr(tp.key(), tp.fallback()), x + 2, y + 2, 0xE0E0E0, false);
            int task = ship.getStateMinor(ID.M.Task);
            int ix = x + 2;
            int iy = y + ROW_H;
            graphics.blit(TEXTURE, ix, iy, 87, 214, 64, 16);
            graphics.blit(TEXTURE, ix, iy + 2, 151, 237, 64, 16);
            if (task >= 1 && task <= 4) {
                graphics.blit(TEXTURE, ix + (task - 1) * 16, iy, 87 + (task - 1) * 16, 230, 16, 16);
            }
        } else if (row instanceof SideGrid sg) {
            graphics.drawString(this.font, tr(sg.key(), sg.fallback()), x + 2, y + 2, 0xE0E0E0, false);
            int mask = ship.getStateMinor(ID.M.TaskSide);
            for (int col = 0; col < 6; col++) {
                int cx = x + 2 + col * (CELL + 1);
                int cy = y + ROW_H;
                boolean on = (mask & (1 << (sg.group() * 6 + col))) != 0;
                drawToggleSprite(graphics, cx, cy, on);
                // Column identity was invisible in the old grid - label each cell.
                graphics.drawString(this.font, SIDE_LETTERS[col],
                        cx + 3, cy + 2, on ? 0x203020 : 0xD0D0D0, false);
            }

        }
    }

    private void drawToggleSprite(GuiGraphics graphics, int x, int y, boolean on) {
        graphics.blit(TEXTURE, x, y, on ? 0 : 11, 214, CELL, CELL);
    }

    private List<Component> buildTooltip(Row row) {
        List<Component> out = new ArrayList<>();
        String title;
        String desc;

        if (row instanceof Toggle t) {
            title = tr(t.key(), t.fallback());
            desc = tr(t.descKey(), t.descFallback());
            out.add(Component.literal(title).withStyle(ChatFormatting.WHITE));
            addWrapped(out, desc);
            if (!t.available(ship) && !t.lockKey().isEmpty()) {
                out.add(Component.literal(tr(t.lockKey(), t.lockFallback()))
                        .withStyle(ChatFormatting.RED));
            }
            return out;
        }
        if (row instanceof Slider s) {
            title = tr(s.key(), s.fallback());
            desc = tr(s.descKey(), s.descFallback());
        } else if (row instanceof MaskBit m) {
            title = tr(m.key(), m.fallback());
            desc = tr(m.descKey(), m.descFallback());
        } else if (row instanceof TaskPick t) {
            title = tr(t.key(), t.fallback());
            desc = tr(t.descKey(), t.descFallback());
        } else if (row instanceof SideGrid g) {
            title = tr(g.key(), g.fallback());
            desc = tr(g.descKey(), g.descFallback());

        } else {
            return out;
        }

        out.add(Component.literal(title).withStyle(ChatFormatting.WHITE));
        addWrapped(out, desc);

        if (row instanceof SideGrid) {
            StringBuilder legend = new StringBuilder();
            for (int i = 0; i < SIDE_LETTERS.length; i++) {
                if (i > 0) {
                    legend.append("  ");
                }
                legend.append(SIDE_LETTERS[i]).append('=').append(SIDE_NAMES[i]);
            }
            out.add(Component.literal(legend.toString()).withStyle(ChatFormatting.DARK_GRAY));
        }
        return out;
    }

    private void addWrapped(List<Component> out, String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        // splitLines (not font.split) because renderComponentTooltip wants
        // Components, and FormattedCharSequence can't be read back as text.
        for (FormattedText line : this.font.getSplitter().splitLines(text, 190, Style.EMPTY)) {
            out.add(Component.literal(line.getString()).withStyle(ChatFormatting.GRAY));
        }
    }

    // ========== Input ==========

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && mouseY >= listTop && mouseY < listBottom
                && mouseX >= rowLeft() && mouseX < rowLeft() + rowWidth()) {
            int y = listTop - scrollOffset;
            for (Row row : ROWS) {
                int h = row.height(ship);
                if (mouseY >= y && mouseY < y + h) {
                    if (handleRowClick(row, (int) mouseX, (int) mouseY, y)) {
                        return true;
                    }
                    break;
                }
                y += h;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean handleRowClick(Row row, int mouseX, int mouseY, int rowY) {
        int x = rowLeft();

        if (row instanceof Toggle t) {
            if (!t.available(ship)) {
                return true; // consume, but a gated setting can't be changed
            }
            int newValue = ship.getStateFlag(t.flagId()) ? 0 : 1;
            ship.setStateFlagI(t.flagId(), newValue);
            GuiShipInventory.sendShipButton(ship, t.buttonId(), newValue);
            return true;
        }
        if (row instanceof Slider s) {
            draggingSlider = s.barIndex();
            dragPos = posFromMouse(mouseX);
            return true;
        }
        if (row instanceof MaskBit mb) {
            int mask = ship.getStateMinor(ID.M.TaskSide) ^ (1 << mb.bit());
            ship.setStateMinor(ID.M.TaskSide, mask);
            GuiShipInventory.sendShipButton(ship, ID.B.ShipInv_TaskSide, mask);
            return true;
        }
        if (row instanceof TaskPick) {
            int iy = rowY + ROW_H;
            if (mouseY >= iy && mouseY < iy + 16) {
                int idx = (mouseX - (x + 2)) / 16;
                if (idx >= 0 && idx < 4) {
                    int current = ship.getStateMinor(ID.M.Task);
                    int newTask = current == idx + 1 ? 0 : idx + 1;
                    ship.setStateMinor(ID.M.Task, newTask);
                    GuiShipInventory.sendShipButton(ship, ID.B.ShipInv_Task, newTask);
                }
            }
            return true;
        }
        if (row instanceof SideGrid sg) {
            int cy = rowY + ROW_H;
            if (mouseY >= cy && mouseY < cy + CELL) {
                int col = (mouseX - (x + 2)) / (CELL + 1);
                if (col >= 0 && col < 6) {
                    int mask = ship.getStateMinor(ID.M.TaskSide) ^ (1 << (sg.group() * 6 + col));
                    ship.setStateMinor(ID.M.TaskSide, mask);
                    GuiShipInventory.sendShipButton(ship, ID.B.ShipInv_TaskSide, mask);
                }
            }
            return true;
        }
        return false;
    }

    private int posFromMouse(int mouseX) {
        return Mth.clamp((mouseX - trackLeft()) * SLIDER_MAX_POS / TRACK_W, 0, SLIDER_MAX_POS);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingSlider >= 0) {
            dragPos = posFromMouse((int) mouseX);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (draggingSlider >= 0) {
            int value = barPosToState(draggingSlider, dragPos);
            setSliderState(draggingSlider, value);
            GuiShipInventory.sendShipButton(ship, getSliderButtonId(draggingSlider), value);
            draggingSlider = -1;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int viewHeight = listBottom - listTop;
        int maxScroll = Math.max(0, contentHeight() - viewHeight);
        scrollOffset = Mth.clamp(scrollOffset - (int) (delta * ROW_H * 2), 0, maxScroll);
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.minecraft != null && this.minecraft.options.keyInventory.matches(keyCode, scanCode)) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ========== Slider value mapping (ported verbatim from the old strip) ==========

    private int stateToBarPos(int barIndex, int stateValue) {
        return switch (barIndex) {
            case 0 -> (int) ((stateValue - 1) / 30.0 * SLIDER_MAX_POS); // FollowMin: 1-31
            case 1 -> (int) ((stateValue - 2) / 30.0 * SLIDER_MAX_POS); // FollowMax: 2-32
            case 2 -> (int) (stateValue / 100.0 * SLIDER_MAX_POS); // FleeHP: 0-100
            case 3 -> (int) (stateValue / 16.0 * SLIDER_MAX_POS); // WpStay: 0-16
            case 4 -> Mth.clamp((stateValue - 1) * 14, 0, SLIDER_MAX_POS); // AutoCR: 1-4
            default -> 0;
        };
    }

    private int barPosToState(int barIndex, int pos) {
        pos = Mth.clamp(pos, 0, SLIDER_MAX_POS);
        return switch (barIndex) {
            case 0 -> Mth.clamp((int) (pos / (double) SLIDER_MAX_POS * 30 + 1), 1, 31);
            case 1 -> Mth.clamp((int) (pos / (double) SLIDER_MAX_POS * 30 + 2), 2, 32);
            case 2 -> Mth.clamp((int) (pos / (double) SLIDER_MAX_POS * 100), 0, 100);
            case 3 -> Mth.clamp((int) (pos / (double) SLIDER_MAX_POS * 16), 0, 16);
            case 4 -> Mth.clamp(pos / 14 + 1, 1, 4);
            default -> 0;
        };
    }

    private int getSliderState(int barIndex) {
        return switch (barIndex) {
            case 0 -> ship.getStateMinor(ID.M.FollowMin);
            case 1 -> ship.getStateMinor(ID.M.FollowMax);
            case 2 -> ship.getStateMinor(ID.M.FleeHP);
            case 3 -> ship.getStateMinor(ID.M.WpStay);
            case 4 -> ship.getStateMinor(ID.M.UseCombatRation);
            default -> 0;
        };
    }

    private void setSliderState(int barIndex, int value) {
        switch (barIndex) {
            case 0 -> ship.setStateMinor(ID.M.FollowMin, value);
            case 1 -> ship.setStateMinor(ID.M.FollowMax, value);
            case 2 -> ship.setStateMinor(ID.M.FleeHP, value);
            case 3 -> ship.setStateMinor(ID.M.WpStay, value);
            case 4 -> ship.setStateMinor(ID.M.UseCombatRation, value);
            default -> {
            }
        }
    }

    private int getSliderButtonId(int barIndex) {
        return switch (barIndex) {
            case 0 -> ID.B.ShipInv_FollowMin;
            case 1 -> ID.B.ShipInv_FollowMax;
            case 2 -> ID.B.ShipInv_FleeHP;
            case 3 -> ID.B.ShipInv_WpStay;
            case 4 -> ID.B.ShipInv_AutoCR;
            default -> -1;
        };
    }

    private static String getMoraleLevelName(int level) {
        return switch (level) {
            case 1 -> tr("gui.shincolle_kai.morale4", "Exhausted");
            case 2 -> tr("gui.shincolle_kai.morale3", "Tired");
            case 3 -> tr("gui.shincolle_kai.morale2", "Normal");
            case 4 -> tr("gui.shincolle_kai.morale1", "Happy");
            default -> tr("gui.shincolle_kai.general.off", "Off");
        };
    }
}
