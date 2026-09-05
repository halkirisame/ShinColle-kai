package com.lulan.shincolle.client.gui;

import com.lulan.shincolle.reference.Reference;
import com.lulan.shincolle.api.attribute.CoreShipAttributes;
import com.lulan.shincolle.api.attribute.ShipAttributeLayer;
import com.lulan.shincolle.api.attribute.ShipAttributeLayout;
import com.lulan.shincolle.api.attribute.ShipAttributeValues;
import com.lulan.shincolle.client.gui.inventory.ContainerShipInventory;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.entity.BasicEntityShipCV;
import com.lulan.shincolle.equip.ShipEquipSlots;
import com.lulan.shincolle.item.ShipAttributeTooltipFormatter;
import com.lulan.shincolle.network.C2SGUIInputPacket;
import com.lulan.shincolle.network.ModNetworking;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Values;
import com.lulan.shincolle.reference.unitclass.Attrs;
import com.lulan.shincolle.reference.unitclass.AttrsAdv;
import com.lulan.shincolle.utility.BuffHelper;
import com.lulan.shincolle.utility.GuiHelper;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * GUI screen for the ship entity inventory.
 * <p>
 * Features:
 * - Entity preview rendering in the top-right area
 * - Info page tabs (3 pages): kills/exp, ATK/DEF stats, marriage/formation
 * - 8-page AI settings system with toggle buttons and slider bars
 * - Morale icon indicator
 * - C2S packet sync for all AI controls
 */
public class GuiShipInventory extends AbstractContainerScreen<ContainerShipInventory> {

    private static final ResourceLocation TEXTURE = new ResourceLocation(Reference.MOD_ID,
            "textures/gui/guishipinventory.png");
    private static final ResourceLocation TEXTURE_ICON0 = new ResourceLocation(Reference.MOD_ID,
            "textures/gui/guinameicon0.png");
    private static final ResourceLocation TEXTURE_ICON1 = new ResourceLocation(Reference.MOD_ID,
            "textures/gui/guinameicon1.png");
    private static final ResourceLocation TEXTURE_ICON2 = new ResourceLocation(Reference.MOD_ID,
            "textures/gui/guinameicon2.png");

    // ========== State ==========
    /**
     * Current info page: 0=Kills/EXP, 1=ATK/DEF/SPD, 2=Marriage/Formation
     */
    private int infoPage = 0;
    /**
     * Attribute display on info page: false=surface attack, true=air attack.
     */
    private boolean showAirAttack = false;

    // ========== Constructor ==========

    public GuiShipInventory(ContainerShipInventory menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
        this.imageWidth = 256;
        this.imageHeight = 214;
    }

    // ========== Background Rendering ==========

    private static String getMoraleDisplayName(int morale) {
        if (morale > ID.Morale.L_Excited) {
            return tr("gui.shincolle_kai.morale0", "Excited");
        }
        if (morale > ID.Morale.L_Happy) {
            return tr("gui.shincolle_kai.morale1", "Happy");
        }
        if (morale > ID.Morale.L_Normal) {
            return tr("gui.shincolle_kai.morale2", "Normal");
        }
        if (morale > ID.Morale.L_Tired) {
            return tr("gui.shincolle_kai.morale3", "Tired");
        }
        return tr("gui.shincolle_kai.morale4", "Exhausted");
    }

    private static int getMoraleDisplayColor(int morale) {
        if (morale > ID.Morale.L_Excited) {
            return 0xFF5500;
        }
        if (morale > ID.Morale.L_Happy) {
            return 0xFFFF00;
        }
        if (morale > ID.Morale.L_Normal) {
            return 0x00FF00;
        }
        if (morale > ID.Morale.L_Tired) {
            return 0xAAAAAA;
        }
        return 0xFF0000;
    }

    /**
     * Get display name for a formation type ID
     */
    private static String getFormationName(int formatType) {
        return switch (formatType) {
            case 1 -> tr("gui.shincolle_kai.formation.format1", "Line Ahead");
            case 2 -> tr("gui.shincolle_kai.formation.format2", "Double Line");
            case 3 -> tr("gui.shincolle_kai.formation.format3", "Diamond");
            case 4 -> tr("gui.shincolle_kai.formation.format4", "Echelon");
            case 5 -> tr("gui.shincolle_kai.formation.format5", "Line Abreast");
            default -> tr("gui.shincolle_kai.formation.format0", "None");
        };
    }

    /**
     * Get short display name for a ship type ID
     */
    private static String getShipTypeName(int shipType) {
        return switch (shipType) {
            case ID.ShipType.DESTROYER -> "DD";
            case ID.ShipType.LIGHT_CRUISER -> "CL";
            case ID.ShipType.HEAVY_CRUISER -> "CA";
            case ID.ShipType.TORPEDO_CRUISER -> "CLT";
            case ID.ShipType.LIGHT_CARRIER -> "CVL";
            case ID.ShipType.STANDARD_CARRIER -> "CV";
            case ID.ShipType.BATTLESHIP -> "BB";
            case ID.ShipType.TRANSPORT -> "AP";
            case ID.ShipType.SUBMARINE -> "SS";
            case ID.ShipType.DEMON -> "Demon";
            case ID.ShipType.HIME -> "Hime";
            default -> "??";
        };
    }

    private static String tr(String key, String fallback) {
        String localized = I18n.get(key);
        return localized.equals(key) ? fallback : localized;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, this.leftPos, this.topPos, 0, 0, this.imageWidth, this.imageHeight);

        BasicEntityShip ship = this.menu.getShip();
        if (ship != null) {
            renderInventoryPageIndicators(graphics, ship);
            renderInfoPageTabIndicator(graphics);
            renderEntityPreview(graphics, mouseX, mouseY, ship);
            renderMoraleIcon(graphics, ship);
            renderShipIdentityIcons(graphics, ship);
            renderAISettingsButton(graphics);
            renderAppearanceToggles(graphics, ship);
            renderInventoryTaskOverlay(graphics, ship);
            if (ModList.get().isLoaded("curios")) {
                renderEquipPanel(graphics);
                renderEquipDetailButton(graphics);
            }
        }
    }

    // ========== Third-Party Equipment Panel Rendering ==========

    private static final ResourceLocation EQUIP_PANEL_TEXTURE = new ResourceLocation(Reference.MOD_ID,
            "textures/gui/ship_equip_panel.png");
    private static final int EQUIP_PANEL_SHEET = 26;
    private static final int EQUIP_PANEL_CAP = 4;
    private static final int EQUIP_PANEL_ROW = 18;
    /** Horizontal inset of the slot well within the panel. */
    private static final int EQUIP_PANEL_INSET = 4;
    /** Height of the "view traits" button drawn just below the equip panel. */
    private static final int DETAIL_BUTTON_HEIGHT = 12;

    /**
     * Draws the backing panel for the third-party equipment slots appended by
     * {@link ContainerShipInventory}. The texture is a single 26x26 sheet split
     * into three bands - top cap, a repeatable row holding one slot well,
     * bottom cap - so whatever slot count the config asks for renders as one
     * continuous panel rather than a stack of separate frames.
     */
    private void renderEquipPanel(GuiGraphics graphics) {
        int count = ShipEquipSlots.slotCount();
        if (count <= 0) {
            return;
        }

        int x = this.leftPos + ShipEquipSlots.slotX() - EQUIP_PANEL_INSET;
        int y = this.topPos + ShipEquipSlots.slotY(0) - EQUIP_PANEL_CAP;

        // top cap
        graphics.blit(EQUIP_PANEL_TEXTURE, x, y, 0, 0,
                ShipEquipSlots.PANEL_WIDTH, EQUIP_PANEL_CAP, EQUIP_PANEL_SHEET, EQUIP_PANEL_SHEET);

        // one repeatable row per slot, butted together
        for (int i = 0; i < count; i++) {
            graphics.blit(EQUIP_PANEL_TEXTURE, x, y + EQUIP_PANEL_CAP + i * EQUIP_PANEL_ROW, 0, EQUIP_PANEL_CAP,
                    ShipEquipSlots.PANEL_WIDTH, EQUIP_PANEL_ROW, EQUIP_PANEL_SHEET, EQUIP_PANEL_SHEET);
        }

        // bottom cap
        graphics.blit(EQUIP_PANEL_TEXTURE, x, y + EQUIP_PANEL_CAP + count * EQUIP_PANEL_ROW, 0,
                EQUIP_PANEL_SHEET - EQUIP_PANEL_CAP,
                ShipEquipSlots.PANEL_WIDTH, EQUIP_PANEL_CAP, EQUIP_PANEL_SHEET, EQUIP_PANEL_SHEET);
    }

    /**
     * Bounds (relative to the GUI's own top-left, matching {@code relX}/{@code
     * relY} in {@link #mouseClicked}) of the small button below the equip
     * panel that opens {@link GuiShipEquipDetail}. Public and static so
     * {@code com.lulan.shincolle.compat.jei.ShinColleJeiPlugin} can carve out
     * the same rectangle as a JEI exclusion zone without the two ever
     * drifting apart.
     */
    public static int[] equipDetailButtonRelBounds() {
        int count = ShipEquipSlots.slotCount();
        int x = ShipEquipSlots.slotX() - EQUIP_PANEL_INSET;
        int panelBottom = ShipEquipSlots.slotY(0) + count * EQUIP_PANEL_ROW + EQUIP_PANEL_CAP;
        return new int[]{x, panelBottom + 2, ShipEquipSlots.PANEL_WIDTH, DETAIL_BUTTON_HEIGHT};
    }

    private void renderEquipDetailButton(GuiGraphics graphics) {
        if (ShipEquipSlots.slotCount() <= 0) {
            return;
        }
        int[] b = equipDetailButtonRelBounds();
        int x = this.leftPos + b[0];
        int y = this.topPos + b[1];
        graphics.fill(x, y, x + b[2], y + b[3], 0xFF8B8B8B);
        graphics.fill(x + 1, y + 1, x + b[2] - 1, y + b[3] - 1, 0xFFA6A6A6);
        graphics.drawCenteredString(this.font, "...", x + b[2] / 2, y + 2, 0x404040);
    }

    /**
     * Hit box of the "AI settings" button, relative to {@link #leftPos}/{@link #topPos}.
     *
     * <p>Sits in the region the old 8-page AI strip used to occupy. Public and
     * static so the JEI plugin can carve the same rect out of its overlay, the
     * way it already does for the equipment-detail button.
     */
    public static int[] aiSettingsButtonRelBounds() {
        return new int[]{172, 131, 82, 16};
    }

    /**
     * Draw the "AI settings" button, covering the strip artwork baked into the
     * background texture behind it.
     */
    private void renderAISettingsButton(GuiGraphics graphics) {
        int[] b = aiSettingsButtonRelBounds();
        int x = this.leftPos + b[0];
        int y = this.topPos + b[1];

        // The background sheet still has the old tab gutter and row grooves
        // drawn in here, so blank the whole former strip before drawing.
        // Player inventory starts at x=8 with nine 18px columns, so it ends at
        // x=170. Blanking must not start left of that or it paints over the last
        // inventory column.
        graphics.fill(this.leftPos + 171, this.topPos + 128,
                this.leftPos + 256, this.topPos + 212, 0xFFC6C6C6);

        graphics.fill(x, y, x + b[2], y + b[3], 0xFF8B8B8B);
        graphics.fill(x + 1, y + 1, x + b[2] - 1, y + b[3] - 1, 0xFFA6A6A6);
        // Not drawCenteredString: that always draws a shadow, which on dark
        // text over a light button reads as doubled, smeared glyphs.
        String label = tr("gui.shincolle_kai.aisettings.title", "Settings");
        graphics.drawString(this.font, label,
                x + (b[2] - this.font.width(label)) / 2, y + 4, 0x404040, false);
    }

    // ========== Appearance Toggles ==========

    /** Toggle sprite size on {@link #TEXTURE}: ON at (0,214), OFF at (11,214). */
    private static final int APPEAR_CELL = 11;
    /** Toggles per row; a ship with more wraps onto further rows. */
    private static final int APPEAR_COLS = 4;
    /** Vertical pitch between grid rows. */
    private static final int APPEAR_ROW_H = 12;
    /**
     * Left edge of the appearance strip, flush with the settings button above it
     * (x=172). Must stay right of x=170, where the player inventory's last
     * column ends.
     */
    private static final int APPEAR_X = 172;
    private static final int APPEAR_GRID_Y = 151;

    /**
     * Number of model-part toggles this ship exposes, capped at the 8 that fit on
     * one row. Every registered ship reports 0..8, so the cap never truncates.
     */
    private static int appearanceCellCount(BasicEntityShip ship) {
        return Mth.clamp(ship.getStateMinor(ID.M.NumState), 0, 8);
    }

    /**
     * Draw the model-part toggles and the held-item toggle, in the strip the old
     * 8-page AI gutter used to occupy. These live here rather than in
     * {@link GuiShipAISettings} because the grid also picks a ship's rensouhou
     * summon type, which is a combat setting rather than decoration.
     */
    private void renderAppearanceToggles(GuiGraphics graphics, BasicEntityShip ship) {
        int count = appearanceCellCount(ship);
        int state = ship.getStateEmotion(ID.S.State);
        for (int i = 0; i < count; i++) {
            drawToggleSprite(graphics,
                    this.leftPos + APPEAR_X + (i % APPEAR_COLS) * APPEAR_CELL,
                    this.topPos + APPEAR_GRID_Y + (i / APPEAR_COLS) * APPEAR_ROW_H,
                    (state & (1 << i)) != 0);
        }

        int heldY = this.topPos + appearanceHeldRelY(ship);
        drawToggleSprite(graphics, this.leftPos + APPEAR_X, heldY,
                ship.getStateFlag(ID.F.ShowHeldItem));
        // Keep the label inside the 256px panel whatever the locale: 8 CJK glyphs
        // already reach the edge from here.
        String heldLabel = tr("gui.shincolle_kai.showhelditem", "Show Held Item");
        int labelX = Math.max(APPEAR_X + APPEAR_CELL + 1,
                Math.min(APPEAR_X + APPEAR_CELL + 2, this.imageWidth - this.font.width(heldLabel) - 2));
        graphics.drawString(this.font, heldLabel,
                this.leftPos + labelX, heldY + 2, 0x404040, false);
    }

    /** Rows the grid occupies for this ship; 0 when it has no model parts. */
    private static int appearanceRowCount(BasicEntityShip ship) {
        return (appearanceCellCount(ship) + APPEAR_COLS - 1) / APPEAR_COLS;
    }

    /** Y of the held-item row, pushed below however many grid rows are drawn. */
    private static int appearanceHeldRelY(BasicEntityShip ship) {
        return APPEAR_GRID_Y + appearanceRowCount(ship) * APPEAR_ROW_H + 2;
    }

    private void drawToggleSprite(GuiGraphics graphics, int x, int y, boolean on) {
        graphics.blit(TEXTURE, x, y, on ? 0 : 11, 214, APPEAR_CELL, APPEAR_CELL);
    }

    /**
     * Handle clicks on the appearance strip. Mirrors the state edit the settings
     * screen used to perform, including the same button ids, so the server side
     * is unchanged.
     */
    private boolean handleAppearanceClick(BasicEntityShip ship, int relX, int relY) {
        if (relX < APPEAR_X) {
            return false;
        }

        int rows = appearanceRowCount(ship);
        int row = (relY - APPEAR_GRID_Y) / APPEAR_ROW_H;
        if (relX >= APPEAR_X && relY >= APPEAR_GRID_Y && row >= 0 && row < rows
                && (relY - APPEAR_GRID_Y) % APPEAR_ROW_H < APPEAR_CELL) {
            int col = (relX - APPEAR_X) / APPEAR_CELL;
            if (col >= 0 && col < APPEAR_COLS) {
                int idx = row * APPEAR_COLS + col;
                if (idx < appearanceCellCount(ship)) {
                    int state = ship.getStateEmotion(ID.S.State) ^ (1 << idx);
                    ship.setStateEmotion(ID.S.State, state, false);
                    sendShipButton(ship, ID.B.ShipInv_ModelState01 + idx, state);
                    return true;
                }
            }
            return false;
        }

        int heldY = appearanceHeldRelY(ship);
        if (relY >= heldY && relY < heldY + APPEAR_CELL
                && relX < APPEAR_X + APPEAR_CELL) {
            int newValue = ship.getStateFlag(ID.F.ShowHeldItem) ? 0 : 1;
            ship.setStateFlagI(ID.F.ShowHeldItem, newValue);
            sendShipButton(ship, ID.B.ShipInv_ShowHeld, newValue);
            return true;
        }
        return false;
    }

    // ========== AI Page Tab Rendering ==========

    /**
     * Render inventory page lock overlays and current page indicator (left tabs).
     */
    private void renderInventoryPageIndicators(GuiGraphics graphics, BasicEntityShip ship) {
        int maxPage = ship.getInventoryPageSize();

        if (maxPage <= 1) {
            graphics.blit(TEXTURE, this.leftPos + 62, this.topPos + 90, 80, 214, 6, 34);
        }
        if (maxPage <= 0) {
            graphics.blit(TEXTURE, this.leftPos + 62, this.topPos + 54, 80, 214, 6, 34);
        }

        int invPage = this.menu.getInventoryPage();
        int tabY = this.topPos + 18 + Mth.clamp(invPage, 0, 2) * 36;
        graphics.blit(TEXTURE, this.leftPos + 62, tabY, 74, 214, 6, 34);
    }

    // ========== AI Page Content Background Rendering ==========

    /**
     * Render legacy task icon/slot overlay in the inventory panel (page 0 only).
     * Keeps 1.10.2 behavior where active task is shown near lower-left inventory
     * area.
     */
    private void renderInventoryTaskOverlay(GuiGraphics graphics, BasicEntityShip ship) {
        if (this.menu.getInventoryPage() != 0) {
            return;
        }

        int task = ship.getStateMinor(ID.M.Task);
        if (task < 1 || task > 4) {
            return;
        }

        graphics.blit(TEXTURE, this.leftPos + 25, this.topPos + 107, 33, 225, 18, 18);
        graphics.blit(TEXTURE, this.leftPos + 26, this.topPos + 109, 151 + (task - 1) * 16, 236, 18, 18);

        // Crafting task uses dedicated 3x3 task slots (slot 12..20 in ship inventory).
        if (task == 4) {
            for (int i = 0; i < 9; i++) {
                ItemStack stack = ship.getCapaShipInventory().getStackInSlot(i + 12);
                int u = stack.isEmpty() ? 33 : 51;
                int x = this.leftPos + 7 + (i % 3) * 18;
                int y = this.topPos + 53 + (i / 3) * 18;
                graphics.blit(TEXTURE, x, y, u, 225, 18, 18);
            }
        }
    }

    /**
     * Render the selected info page tab indicator
     */
    private void renderInfoPageTabIndicator(GuiGraphics graphics) {
        int tabY = this.topPos + 18 + infoPage * 36;
        graphics.blit(TEXTURE, this.leftPos + 135, tabY, 74, 214, 6, 34);
    }

    /**
     * Render entity preview in the top-right area
     */
    private void renderEntityPreview(GuiGraphics graphics, int mouseX, int mouseY, BasicEntityShip ship) {
        // [PORT] 1.10.2 -> 1.20.1: keep legacy modelPos offsets so model anchor remains
        // consistent.
        float[] modelPos = ship.getModelPos();
        int offsetX = (modelPos != null && modelPos.length > 0) ? (int) modelPos[0] : 0;
        int offsetY = (modelPos != null && modelPos.length > 1) ? (int) modelPos[1] : 0;
        int scale = (modelPos != null && modelPos.length > 3 && modelPos[3] > 0)
                ? (int) modelPos[3]
                : 50;
        int renderX = this.leftPos + 218 + offsetX;
        int renderY = this.topPos + 100 + offsetY;
        float lookX = (float) (this.leftPos + 215 - mouseX);
        float lookY = (float) (this.topPos + 60 - mouseY);
        InventoryScreen.renderEntityInInventoryFollowsMouse(
                graphics,
                renderX,
                renderY,
                scale,
                lookX,
                lookY,
                ship);
    }

    /**
     * Render morale icon (11x11 sprite)
     */
    private void renderMoraleIcon(GuiGraphics graphics, BasicEntityShip ship) {
        int morale = ship.getMorale();
        int moraleIdx = BuffHelper.getMoraleLevel(morale);
        graphics.blit(TEXTURE, this.leftPos + 239, this.topPos + 18,
                moraleIdx * 11, 240, 11, 11);
    }

    /**
     * Render legacy ship type/name icons in model panel area.
     */
    private void renderShipIdentityIcons(GuiGraphics graphics, BasicEntityShip ship) {
        int[] typeIcon = Values.ShipTypeIconMap.get(ship.getShipType());
        if (typeIcon != null && typeIcon.length >= 2) {
            if (ship.getStateMinor(ID.M.ShipLevel) > 99) {
                graphics.blit(TEXTURE_ICON0, this.leftPos + 165, this.topPos + 18, 0, 0, 40, 42);
                graphics.blit(TEXTURE_ICON0, this.leftPos + 167, this.topPos + 22, typeIcon[0], typeIcon[1], 28, 28);
            } else {
                graphics.blit(TEXTURE_ICON0, this.leftPos + 165, this.topPos + 18, 0, 43, 30, 30);
                graphics.blit(TEXTURE_ICON0, this.leftPos + 165, this.topPos + 18, typeIcon[0], typeIcon[1], 28, 28);
            }
        }

        int[] nameIcon = Values.ShipNameIconMap.get(ship.getShipClass());
        if (nameIcon == null || nameIcon.length < 3) {
            return;
        }

        ResourceLocation nameTexture = nameIcon[0] < 100 ? TEXTURE_ICON1 : TEXTURE_ICON2;
        int offY = 0;
        if (nameIcon[0] < 100) {
            if (nameIcon[0] == 4) {
                offY = -10;
            }
        } else {
            offY = 10;
        }
        graphics.blit(nameTexture, this.leftPos + 176, this.topPos + 63 + offY, nameIcon[1], nameIcon[2], 11, 59);
    }

    // ========== Label/Text Rendering ==========

    // ========== Info Page Text Rendering ==========

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        BasicEntityShip ship = this.menu.getShip();
        if (ship == null) {
            graphics.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, 0x404040, false);
            graphics.drawString(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY,
                    0x404040, false);
            return;
        }

        // Ship Name
        String shipName = ship.hasCustomName() ? Objects.requireNonNull(ship.getCustomName()).getString() : ship.getName().getString();
        graphics.drawString(this.font, shipName, 8, 6, 0x000000, false);

        // Level (right-aligned, gold for 150+)
        int level = ship.getStateMinor(ID.M.ShipLevel);
        String levelStr = "Lv." + level;
        int levelColor = level >= 150 ? 0xFFD700 : 0xFFFFFF;
        graphics.drawString(this.font, levelStr, this.imageWidth - 6 - this.font.width(levelStr), 6, levelColor, true);

        // HP Text
        renderHPText(graphics, ship);

        // Info Page Content
        switch (infoPage) {
            case 0:
                renderInfoPage0(graphics, ship);
                break;
            case 1:
                renderInfoPage1(graphics, ship);
                break;
            case 2:
                renderInfoPage2(graphics, ship);
                break;
        }

        // Player inventory title (vanilla位置を維持)
        graphics.drawString(this.font, this.playerInventoryTitle, this.inventoryLabelX, 121, 0x404040, false);
    }

    /**
     * Render HP text label and value
     */
    private void renderHPText(GuiGraphics graphics, BasicEntityShip ship) {
        int hpCurrent = Mth.ceil(ship.getHealth());
        int hpMax = Mth.ceil(ship.getMaxHealth());
        // original: "HP" label right-aligned at x=145, current HP at x=147, "/max" after
        graphics.drawString(this.font, "HP", 145 - this.font.width("HP"), 6, 0x00FFFF, true);

        float hpRatio = hpMax > 0 ? (float) hpCurrent / (float) hpMax : 1.0f;
        int hpColor = GuiHelper.getBonusPointColor(ship.getAttrs().getAttrsBonus(ID.AttrsBase.HP));
        String curStr = String.valueOf(hpCurrent);
        String maxStr = "/" + hpMax;
        int curColor = hpCurrent < hpMax ? GuiHelper.getDarkerColor(hpColor, 0.8F) : hpColor;
        graphics.drawString(this.font, curStr, 147, 6, curColor, true);
        graphics.drawString(this.font, maxStr, 148 + this.font.width(curStr), 6, hpColor, true);
    }

    // ========== AI Page Label Rendering ==========

    /**
     * Page 0: Kills, EXP, Ammo, Grudge, Morale
     */
    private void renderInfoPage0(GuiGraphics graphics, BasicEntityShip ship) {
        int textX = 75;
        int textY = 20;
        int lc = 0x404040;
        int vc = 0xFFFFFF;

        drawStatLine(graphics, textX, textY, tr("gui.shincolle_kai.kills", "Kills") + ":",
                String.valueOf(ship.getStateMinor(ID.M.Kills)), lc, vc);
        textY += 21;
        drawStatLine(graphics, textX, textY, tr("gui.shincolle_kai.exp", "EXP") + ":",
                ship.getStateMinor(ID.M.ExpCurrent) + "/" + ship.getStateMinor(ID.M.ExpNext), lc, vc);
        textY += 21;
        drawStatLine(graphics, textX, textY, tr("gui.shincolle_kai.ammolight", "Ammo(L)") + ":",
                String.valueOf(ship.getStateMinor(ID.M.NumAmmoLight)), lc, vc);
        textY += 21;
        drawStatLine(graphics, textX, textY, tr("gui.shincolle_kai.ammoheavy", "Ammo(H)") + ":",
                String.valueOf(ship.getStateMinor(ID.M.NumAmmoHeavy)), lc, vc);
        textY += 21;
        drawStatLine(graphics, textX, textY, tr("gui.shincolle_kai.grudge", "Grudge") + ":",
                String.valueOf(ship.getStateMinor(ID.M.NumGrudge)), lc, vc);
    }

    /**
     * Page 1: ATK, DEF, SPD, MOV, HIT
     */
    private void renderInfoPage1(GuiGraphics graphics, BasicEntityShip ship) {
        Attrs attrs = ship.getAttrs();
        if (attrs == null)
            return;
        ShipAttributeValues buffed = ship.shipAttributes(ShipAttributeLayer.BUFFED);

        int textX = 75;
        int textY = 20;
        int lc = 0x404040;
        int vc = 0xFFFFFF;

        String atkKey = showAirAttack ? "gui.shincolle_kai.firepower2" : "gui.shincolle_kai.firepower1";
        float atkVal = showAirAttack ? buffed.get(CoreShipAttributes.ATK_AL)
                : buffed.get(CoreShipAttributes.ATK_L);
        float torpedoVal = showAirAttack ? buffed.get(CoreShipAttributes.ATK_AH)
                : buffed.get(CoreShipAttributes.ATK_H);
        String atkText = String.format("%.1f / %.1f", atkVal, torpedoVal);

        drawStatLine(graphics, textX, textY, I18n.get(atkKey),
                atkText, lc, GuiHelper.getBonusPointColor(attrs.getAttrsBonus(ID.AttrsBase.ATK)),
                VALUE_ROW_OFFSET);
        textY += 21;
        drawStatLine(graphics, textX, textY, I18n.get("gui.shincolle_kai.armor"),
                String.format("%.1f%%", buffed.get(CoreShipAttributes.DEF) * 100F), lc,
                GuiHelper.getBonusPointColor(attrs.getAttrsBonus(ID.AttrsBase.DEF)));
        textY += 21;
        drawStatLine(graphics, textX, textY, I18n.get("gui.shincolle_kai.attackspeed"),
                String.format("%.2f", buffed.get(CoreShipAttributes.SPD)), lc,
                GuiHelper.getBonusPointColor(attrs.getAttrsBonus(ID.AttrsBase.SPD)));
        textY += 21;
        drawStatLine(graphics, textX, textY, I18n.get("gui.shincolle_kai.movespeed"),
                String.format("%.2f", buffed.get(CoreShipAttributes.MOV)), lc,
                GuiHelper.getBonusPointColor(attrs.getAttrsBonus(ID.AttrsBase.MOV)));
        textY += 21;
        drawStatLine(graphics, textX, textY, I18n.get("gui.shincolle_kai.range"),
                String.format("%.1f", buffed.get(CoreShipAttributes.HIT)), lc,
                GuiHelper.getBonusPointColor(attrs.getAttrsBonus(ID.AttrsBase.HIT)));
    }

    /**
     * Page 2: Marriage, Ring, Formation, Ship type, UID
     */
    private void renderInfoPage2(GuiGraphics graphics, BasicEntityShip ship) {
        int textX = 75;
        int textY = 20;
        int lc = 0x404040;
        int vc = 0xFFFFFF;

        boolean isMarried = ship.getStateFlag(ID.F.IsMarried);
        drawStatLine(graphics, textX, textY, tr("gui.shincolle_kai.marriage", "Marriage") + ":",
                isMarried ? tr("gui.shincolle_kai.married", "Married") : tr("gui.shincolle_kai.unmarried", "Unmarried"),
                lc, 0xFFFF00);
        textY += 21;
        drawStatLine(graphics, textX, textY, tr("gui.shincolle_kai.formation.formation", "Formation") + ":",
                getFormationName(ship.getStateMinor(ID.M.FormatType)), lc, vc);

        if (ship instanceof BasicEntityShipCV cvShip) {
            textY += 42;
            drawStatLine(graphics, textX, textY, tr("gui.shincolle_kai.airplanelight", "Light Aircraft") + ":",
                    String.valueOf(cvShip.getNumAircraftLight()), lc, 0xFFFF00);
            textY += 21;
            drawStatLine(graphics, textX, textY, tr("gui.shincolle_kai.airplaneheavy", "Heavy Aircraft") + ":",
                    String.valueOf(cvShip.getNumAircraftHeavy()), lc, 0xFFFF00);
        }
    }

    /**
     * Draw label at textX, value right-aligned at x=133
     */
    /** One text row, used to drop a value below its label when they would collide. */
    private static final int VALUE_ROW_OFFSET = 9;

    private void drawStatLine(GuiGraphics graphics, int textX, int textY,
                              String label, String value, int labelColor, int valueColor) {
        drawStatLine(graphics, textX, textY, label, value, labelColor, valueColor, 0);
    }

    private void drawStatLine(GuiGraphics graphics, int textX, int textY,
                              String label, String value, int labelColor, int valueColor,
                              int valueYOffset) {
        graphics.drawString(this.font, label, textX, textY, labelColor, false);
        graphics.drawString(this.font, value, 133 - this.font.width(value), textY + valueYOffset,
                valueColor, true);
    }

    // ========== Input Handling ==========

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int relX = (int) mouseX - this.leftPos;
            int relY = (int) mouseY - this.topPos;

            // Info page tab clicks (x=133-142)
            if (relX >= 133 && relX <= 142) {
                if (relY >= 18 && relY <= 52) {
                    infoPage = 0;
                    return true;
                } else if (relY >= 53 && relY <= 88) {
                    infoPage = 1;
                    return true;
                } else if (relY >= 89 && relY <= 125) {
                    infoPage = 2;
                    return true;
                }
            }

            // Equipment traits detail button, below the third-party equip panel
            if (ModList.get().isLoaded("curios") && ShipEquipSlots.slotCount() > 0) {
                int[] b = equipDetailButtonRelBounds();
                if (relX >= b[0] && relX < b[0] + b[2] && relY >= b[1] && relY < b[1] + b[3]) {
                    BasicEntityShip clickedShip = this.menu.getShip();
                    if (clickedShip != null) {
                        this.minecraft.setScreen(new GuiShipEquipDetail(this, clickedShip));
                    }
                    return true;
                }
            }

            // AI settings button - opens the standalone settings window
            int[] ai = aiSettingsButtonRelBounds();
            if (relX >= ai[0] && relX < ai[0] + ai[2] && relY >= ai[1] && relY < ai[1] + ai[3]) {
                BasicEntityShip clickedShip = this.menu.getShip();
                if (clickedShip != null) {
                    this.minecraft.setScreen(new GuiShipAISettings(this, clickedShip));
                }
                return true;
            }

            BasicEntityShip ship = this.menu.getShip();
            if (ship != null) {
                if (handleAppearanceClick(ship, relX, relY))
                    return true;
                if (handleInfoPageClick(relX, relY))
                    return true;
                if (handleInventoryPageClick(ship, relX, relY))
                    return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    /**
     * Handle clicks inside info-page controls.
     */
    private boolean handleInfoPageClick(int relX, int relY) {
        // [PORT] 1.10.2 -> 1.20.1: keep the attack/air view toggle on attribute page.
        if (infoPage == 1 && relX >= 73 && relX <= 132 && relY >= 18 && relY <= 40) {
            showAirAttack = !showAirAttack;
            return true;
        }
        return false;
    }

    /**
     * Handle inventory page tab clicks (left column) and sync to server.
     */
    private boolean handleInventoryPageClick(BasicEntityShip ship, int relX, int relY) {
        if (relX < 61 || relX > 70) {
            return false;
        }

        int page = -1;
        if (relY >= 18 && relY <= 52) {
            page = 0;
        } else if (relY >= 53 && relY <= 88) {
            page = 1;
        } else if (relY >= 89 && relY <= 125) {
            page = 2;
        }

        if (page < 0) {
            return false;
        }

        int maxPage = ship.getInventoryPageSize();
        if (page > maxPage) {
            return false;
        }

        // [PORT] 1.10.2 -> 1.20.1: keep local menu page in sync for slot mapping.
        this.menu.setInventoryPage(page);
        sendShipButton(ship, ID.B.ShipInv_InvPage, page);
        return true;
    }

    // ========== Slider Drag Handling ==========

    // ========== Main Render ==========

    // ========== Slider Conversion Methods ==========

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        this.renderTooltip(graphics, mouseX, mouseY);

        BasicEntityShip ship = this.menu.getShip();
        if (ship != null && isHoveringMoraleIcon(mouseX, mouseY)) {
            renderMoraleTooltip(graphics, ship, mouseX, mouseY);
        } else if (ship != null && isHoveringAttributePanel(mouseX, mouseY)) {
            renderCustomAttributeTooltip(graphics, ship, mouseX, mouseY);
        }
    }

    private boolean isHoveringAttributePanel(int mouseX, int mouseY) {
        int relX = mouseX - this.leftPos;
        int relY = mouseY - this.topPos;
        return this.infoPage == 1 && relX >= 73 && relX <= 133 && relY >= 18 && relY <= 125;
    }

    private void renderCustomAttributeTooltip(GuiGraphics graphics, BasicEntityShip ship,
                                              int mouseX, int mouseY) {
        List<Component> lines = new ArrayList<>();
        ShipAttributeTooltipFormatter.appendFinalCustom(
                ship.shipAttributes(ShipAttributeLayer.BUFFED), ShipAttributeLayout.current(), lines);
        if (lines.isEmpty()) {
            return;
        }
        lines.add(0, Component.translatable("gui.shincolle_kai.additional_attributes"));
        graphics.renderComponentTooltip(this.font, lines, mouseX, mouseY);
    }

    /**
     * Morale icon hover region near the top-right model panel.
     */
    private boolean isHoveringMoraleIcon(int mouseX, int mouseY) {
        int x0 = this.leftPos + 238;
        int y0 = this.topPos + 17;
        return mouseX >= x0 && mouseX < x0 + 13 && mouseY >= y0 && mouseY < y0 + 13;
    }

    /**
     * Render morale tooltip with current morale and morale-derived attribute
     * modifiers.
     */
    private void renderMoraleTooltip(GuiGraphics graphics, BasicEntityShip ship, int mouseX, int mouseY) {
        List<Component> lines = new ArrayList<>();
        int morale = ship.getMorale();
        lines.add(Component.literal(getMoraleDisplayName(morale) + " (" + morale + ")"));

        Attrs attrs = ship.getAttrs();
        if (attrs instanceof AttrsAdv adv) {
            ShipAttributeValues moraleValues = adv.shipAttributes(ShipAttributeLayer.MORALE);
            lines.add(Component.literal(tr("gui.shincolle_kai.firepower1", "Firepower") + ": x "
                    + String.format("%.0f", moraleValues.get(CoreShipAttributes.ATK_L) * 100F)
                    + "% / "
                    + String.format("%.0f", moraleValues.get(CoreShipAttributes.ATK_H) * 100F) + "%"));
            lines.add(Component.literal(tr("gui.shincolle_kai.airfirepower", "Air Firepower") + ": x "
                    + String.format("%.0f", moraleValues.get(CoreShipAttributes.ATK_AL) * 100F)
                    + "% / "
                    + String.format("%.0f", moraleValues.get(CoreShipAttributes.ATK_AH) * 100F) + "%"));
            lines.add(Component.literal(tr("gui.shincolle_kai.attackspeed", "Attack Speed") + ": x "
                    + String.format("%.0f", moraleValues.get(CoreShipAttributes.SPD) * 100F) + "%"));
            lines.add(Component.literal(tr("gui.shincolle_kai.range", "Range") + ": + "
                    + String.format("%.1f", moraleValues.get(CoreShipAttributes.HIT))));
            lines.add(Component.literal(tr("gui.shincolle_kai.critical", "Critical") + ": x "
                    + String.format("%.0f", moraleValues.get(CoreShipAttributes.CRI) * 100F) + "%"));
            lines.add(Component.literal("DHIT: x "
                    + String.format("%.0f", moraleValues.get(CoreShipAttributes.DHIT) * 100F) + "%"));
            lines.add(Component.literal("THIT: x "
                    + String.format("%.0f", moraleValues.get(CoreShipAttributes.THIT) * 100F) + "%"));
            lines.add(Component.literal(tr("gui.shincolle_kai.missrate", "Miss") + ": x "
                    + String.format("%.0f", moraleValues.get(CoreShipAttributes.MISS) * 100F) + "%"));
            lines.add(Component.literal("AA: x "
                    + String.format("%.0f", moraleValues.get(CoreShipAttributes.AA) * 100F) + "%"));
            lines.add(Component.literal("ASM: x "
                    + String.format("%.0f", moraleValues.get(CoreShipAttributes.ASM) * 100F) + "%"));
            lines.add(Component.literal(tr("gui.shincolle_kai.armor", "Armor") + ": + "
                    + String.format("%.0f", moraleValues.get(CoreShipAttributes.DEF) * 100F) + "%"));
            lines.add(Component.literal(tr("gui.shincolle_kai.dodge", "Dodge") + ": + "
                    + String.format("%.0f", moraleValues.get(CoreShipAttributes.DODGE) * 100F) + "%"));
            lines.add(Component.literal("XP: + "
                    + String.format("%.0f", moraleValues.get(CoreShipAttributes.XP) * 100F) + "%"));
            lines.add(Component.literal(tr("gui.shincolle_kai.grudge", "Grudge") + ": + "
                    + String.format("%.0f", moraleValues.get(CoreShipAttributes.GRUDGE) * 100F) + "%"));
            lines.add(Component.literal(tr("gui.shincolle_kai.ammo", "Ammo") + ": + "
                    + String.format("%.0f", moraleValues.get(CoreShipAttributes.AMMO) * 100F) + "%"));
            lines.add(Component.literal("HPRES: + "
                    + String.format("%.0f", moraleValues.get(CoreShipAttributes.HPRES) * 100F) + "%"));
            lines.add(Component.literal("KB: + "
                    + String.format("%.0f", moraleValues.get(CoreShipAttributes.KB) * 100F) + "%"));
            lines.add(Component.literal(tr("gui.shincolle_kai.movespeed", "Move Speed") + ": + "
                    + String.format("%.2f", moraleValues.get(CoreShipAttributes.MOV))));
        }

        graphics.renderComponentTooltip(this.font, lines, mouseX, mouseY);
    }

    // ========== Packet Sending ==========

    // ========== Utility Methods ==========

    /**
     * Send a ship GUI button packet to the server.
     * Format: type=ShipBtn, values={entityId, 0, buttonId, value}
     *
     * <p>Static and package-visible so {@link GuiShipAISettings} - which owns
     * every AI setting since the right-hand strip was removed - sends through
     * the exact same path the strip used to.
     */
    static void sendShipButton(BasicEntityShip ship, int buttonId, int value) {
        ModNetworking.sendToServer(new C2SGUIInputPacket(
                C2SGUIInputPacket.ShipBtn,
                new int[]{ship.getId(), 0, buttonId, value}));
    }
}
