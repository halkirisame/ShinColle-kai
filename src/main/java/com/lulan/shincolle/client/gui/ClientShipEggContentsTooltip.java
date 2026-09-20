package com.lulan.shincolle.client.gui;

import java.util.List;

import com.lulan.shincolle.item.ShipEggContents;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.world.item.ItemStack;

/**
 * Draws a saved ship egg's contents as a grid of item icons, the way a bundle
 * shows what is inside it. Equipment gets its own row, separated from cargo by
 * a divider, so a full ship's inventory reads as two groups rather than one
 * undifferentiated block.
 * <p>
 * The grid wraps at {@link #Columns} rather than assuming any fixed slot count
 * for either section - this fork is meant to interoperate with other mods that
 * may add slots to a ship's inventory, so neither the equipment row nor the
 * cargo grid can assume today's slot counts stay fixed.
 */
public class ClientShipEggContentsTooltip implements ClientTooltipComponent {

    private static final int SlotSize = 18;
    private static final int Columns = 9;
    private static final int DividerGap = 4;

    private final List<ItemStack> equipment;
    private final List<ItemStack> cargo;
    private final boolean hasEquipment;

    public ClientShipEggContentsTooltip(ShipEggContentsTooltip tooltip) {
        ShipEggContents contents = tooltip.contents();
        this.equipment = contents.equipment();
        this.cargo = contents.cargo();
        this.hasEquipment = this.equipment.stream().anyMatch(s -> !s.isEmpty());
    }

    private static int rows(int count) {
        return count == 0 ? 0 : (count + Columns - 1) / Columns;
    }

    private int equipRows() {
        return this.hasEquipment ? rows(this.equipment.size()) : 0;
    }

    private int cargoRows() {
        return rows(this.cargo.size());
    }

    private boolean hasDivider() {
        return this.hasEquipment && !this.cargo.isEmpty();
    }

    @Override
    public int getHeight() {
        int height = this.equipRows() * SlotSize + this.cargoRows() * SlotSize;
        if (this.hasDivider()) {
            height += DividerGap;
        }
        return height + 2;
    }

    @Override
    public int getWidth(Font font) {
        int widestRow = Math.max(
                Math.min(this.equipment.size(), Columns),
                Math.min(this.cargo.size(), Columns));
        return widestRow * SlotSize;
    }

    @Override
    public void renderImage(Font font, int x, int y, GuiGraphics graphics) {
        int rowY = y;
        if (this.hasEquipment) {
            rowY = renderGrid(font, graphics, x, rowY, this.equipment);
        }
        if (this.hasDivider()) {
            int width = Math.min(Math.max(this.equipment.size(), this.cargo.size()), Columns) * SlotSize;
            graphics.fill(x, rowY + DividerGap / 2, x + width, rowY + DividerGap / 2 + 1, 0x50FFFFFF);
            rowY += DividerGap;
        }
        if (!this.cargo.isEmpty()) {
            renderGrid(font, graphics, x, rowY, this.cargo);
        }
    }

    /**
     * Draws one section as a wrapping grid, skipping empty slots so a sparsely
     * filled equipment row does not leave a wall of blank frames.
     *
     * @return the y coordinate just past the drawn rows.
     */
    private static int renderGrid(Font font, GuiGraphics graphics, int x, int y, List<ItemStack> stacks) {
        int drawn = 0;
        for (ItemStack stack : stacks) {
            if (stack.isEmpty()) {
                continue;
            }
            int slotX = x + (drawn % Columns) * SlotSize;
            int slotY = y + (drawn / Columns) * SlotSize;

            // A frame per slot, so the icons read as an inventory rather than as
            // loose pictures floating in the tooltip.
            graphics.fill(slotX, slotY, slotX + SlotSize - 1, slotY + SlotSize - 1, 0x60000000);
            graphics.renderOutline(slotX, slotY, SlotSize - 1, SlotSize - 1, 0x40FFFFFF);

            graphics.renderItem(stack, slotX + 1, slotY + 1, drawn);
            graphics.renderItemDecorations(font, stack, slotX + 1, slotY + 1);
            drawn++;
        }
        return y + rows(drawn) * SlotSize;
    }
}
