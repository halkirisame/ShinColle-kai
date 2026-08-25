package com.lulan.shincolle.client.gui;

import com.lulan.shincolle.api.equipment.ResolvedShipEquipment;
import com.lulan.shincolle.api.equipment.ShipEquipmentResolver;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.equip.curios.ShipCuriosIntegration;
import com.lulan.shincolle.item.ShipAttributeTooltipFormatter;
import com.lulan.shincolle.item.ShipAttackEffectTooltipFormatter;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

import java.util.ArrayList;
import java.util.List;

/**
 * Standalone scrollable window listing resolved attributes on the ship's
 * third-party (Curios-slot) equipment.
 *
 * <p>Opened from a button on {@link GuiShipInventory} - that screen's own
 * info-page tabs are too narrow (~58px) for a variable-length trait list, so
 * this gets its own window instead of squeezing traits into a fourth tab.
 */
public class GuiShipEquipDetail extends Screen {

    private static final int PANEL_WIDTH = 210;
    private static final int PANEL_HEIGHT = 190;
    private static final int LINE_HEIGHT = 11;
    private static final int LIST_PADDING = 8;
    private static final int HEADER_HEIGHT = 20;
    private static final int FOOTER_HEIGHT = 6;

    /** One rendered line. {@code detail} is non-null only for optional detail text shown while Ctrl is held. */
    private record LineEntry(Component text, Component detail) {
        static LineEntry plain(Component text) {
            return new LineEntry(text, null);
        }
    }

    private final Screen parent;
    private final BasicEntityShip ship;
    private List<LineEntry> lines = List.of();
    private int scrollOffset = 0;
    private int panelLeft;
    private int panelTop;
    private int listTop;
    private int listBottom;

    public GuiShipEquipDetail(Screen parent, BasicEntityShip ship) {
        super(Component.literal(tr("gui.shincolle_kai.equip.detailtitle", "Equipment Traits")));
        this.parent = parent;
        this.ship = ship;
    }

    @Override
    protected void init() {
        this.panelLeft = (this.width - PANEL_WIDTH) / 2;
        this.panelTop = (this.height - PANEL_HEIGHT) / 2;
        this.listTop = panelTop + HEADER_HEIGHT;
        this.listBottom = panelTop + PANEL_HEIGHT - FOOTER_HEIGHT;

        this.lines = collectLines();
        this.scrollOffset = 0;

        this.addRenderableWidget(Button.builder(Component.literal("X"), b -> onClose())
                .bounds(panelLeft + PANEL_WIDTH - 18, panelTop + 3, 14, 14)
                .build());
    }

    private List<LineEntry> collectLines() {
        List<LineEntry> result = new ArrayList<>();
        if (!ModList.get().isLoaded("curios")) {
            result.add(LineEntry.plain(Component.literal(tr("gui.shincolle_kai.equip.nocurios", "Curios not installed"))));
            return result;
        }

        List<ItemStack> equipped = ShipCuriosIntegration.getEquippedStacks(ship);
        boolean any = false;
        for (ItemStack stack : equipped) {
            any = true;
            result.add(LineEntry.plain(stack.getHoverName().copy().withStyle(ChatFormatting.YELLOW)));
            var resolved = ShipEquipmentResolver.resolveClient(stack);
            if (resolved.isEmpty()) {
                result.add(LineEntry.plain(Component.literal("  "
                        + tr("gui.shincolle_kai.equip.unresolved", "No resolved equipment data"))
                        .withStyle(ChatFormatting.DARK_GRAY)));
                continue;
            }
            appendResolvedLines(result, resolved.get());
        }
        if (!any) {
            result.add(LineEntry.plain(Component.literal(tr("gui.shincolle_kai.equip.none", "No traits"))));
        }
        return result;
    }

    private static void appendResolvedLines(List<LineEntry> result, ResolvedShipEquipment resolved) {
        List<Component> attributes = new ArrayList<>();
        ShipAttributeTooltipFormatter.append(resolved.attributes(), attributes);
        for (Component attribute : attributes) {
            result.add(LineEntry.plain(Component.literal("  ").append(attribute)));
        }
        List<Component> attackEffects = new ArrayList<>();
        ShipAttackEffectTooltipFormatter.append(resolved.attackEffects(), attackEffects);
        for (Component attackEffect : attackEffects) {
            result.add(LineEntry.plain(Component.literal("  ").append(attackEffect)));
        }
        if (attributes.isEmpty()) {
            result.add(LineEntry.plain(Component.literal("  "
                    + tr("gui.shincolle_kai.equip.noattributes", "No attributes"))
                    .withStyle(ChatFormatting.DARK_GRAY)));
        }
        resolved.definitionId().ifPresent(id -> result.add(LineEntry.plain(Component.literal("  JSON: " + id)
                .withStyle(ChatFormatting.DARK_GRAY))));
        resolved.providerId().ifPresent(id -> result.add(LineEntry.plain(Component.literal("  Provider: " + id)
                .withStyle(ChatFormatting.DARK_GRAY))));
    }

    private static String tr(String key, String fallback) {
        String localized = I18n.get(key);
        return localized.equals(key) ? fallback : localized;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);

        graphics.fill(panelLeft, panelTop, panelLeft + PANEL_WIDTH, panelTop + PANEL_HEIGHT, 0xE0202020);
        graphics.fill(panelLeft, panelTop, panelLeft + PANEL_WIDTH, panelTop + HEADER_HEIGHT - 2, 0xE0303030);
        graphics.drawString(this.font, this.title, panelLeft + 6, panelTop + 6, 0xFFFFFF, false);

        graphics.enableScissor(panelLeft + LIST_PADDING, listTop,
                panelLeft + PANEL_WIDTH - LIST_PADDING, listBottom);
        int y = listTop - scrollOffset;
        LineEntry hovered = null;
        for (LineEntry line : lines) {
            if (y + LINE_HEIGHT >= listTop && y <= listBottom) {
                graphics.drawString(this.font, line.text(), panelLeft + LIST_PADDING, y, 0xE0E0E0, false);
                if (mouseY >= y && mouseY < y + LINE_HEIGHT && line.detail() != null) {
                    hovered = line;
                }
            }
            y += LINE_HEIGHT;
        }
        graphics.disableScissor();

        int contentHeight = lines.size() * LINE_HEIGHT;
        int viewHeight = listBottom - listTop;
        if (contentHeight > viewHeight) {
            int barX = panelLeft + PANEL_WIDTH - 6;
            graphics.fill(barX, listTop, barX + 4, listBottom, 0x40FFFFFF);
            int maxScroll = contentHeight - viewHeight;
            int barHeight = Math.max(10, viewHeight * viewHeight / contentHeight);
            int barY = listTop + scrollOffset * (viewHeight - barHeight) / Math.max(1, maxScroll);
            graphics.fill(barX, barY, barX + 4, barY + barHeight, 0xFFAAAAAA);
        }

        super.render(graphics, mouseX, mouseY, partialTick);

        // Ctrl+hover a modifier line to see its full (level-aware) description,
        // rendered last so it draws on top of everything else.
        if (hovered != null && hasControlDown()) {
            graphics.renderTooltip(this.font, this.font.split(hovered.detail(), 200), mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int contentHeight = lines.size() * LINE_HEIGHT;
        int viewHeight = listBottom - listTop;
        int maxScroll = Math.max(0, contentHeight - viewHeight);
        scrollOffset = Mth.clamp(scrollOffset - (int) (delta * LINE_HEIGHT * 3), 0, maxScroll);
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
        // Screen only treats Escape as a close key by default; the inventory
        // key (E) needs its own check to go back to GuiShipInventory instead
        // of falling through to whatever vanilla does with it.
        if (this.minecraft != null && this.minecraft.options.keyInventory.matches(keyCode, scanCode)) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
