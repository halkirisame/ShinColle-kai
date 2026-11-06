package com.lulan.shincolle.client.gui;

import com.lulan.shincolle.reference.Reference;
import com.lulan.shincolle.client.gui.inventory.ContainerRecipePaper;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/** Client presentation of the server's synchronized recipe editor. */
public class GuiRecipePaper extends AbstractContainerScreen<ContainerRecipePaper> {
    private static final ResourceLocation TEXTURE = new ResourceLocation(Reference.MOD_ID,
            "textures/gui/guirecipepaper.png");

    public GuiRecipePaper(ContainerRecipePaper menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, this.leftPos, this.topPos, 0, 0, this.imageWidth, this.imageHeight);
        int arrowX = this.leftPos + 90;
        int arrowY = this.topPos + 35;
        graphics.fill(arrowX, arrowY + 3, arrowX + 25, arrowY + 5, 0xFF888888);
        graphics.fill(arrowX + 22, arrowY + 1, arrowX + 25, arrowY + 7, 0xFF888888);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(this.font, this.title, this.imageWidth / 2 - this.font.width(this.title) / 2, 6, 0x404040, false);
        Component result = Component.translatable("gui.shincolle_kai.recipepaper.preview");
        graphics.drawString(this.font, result, 133 - this.font.width(result) / 2, 55, 0x888888, false);

        boolean hasPattern = false;
        for (int i = 0; i < ContainerRecipePaper.CRAFTING_SLOT_COUNT; i++) {
            if (!this.menu.getRecipeInv().getItem(i).isEmpty()) {
                hasPattern = true;
                break;
            }
        }
        String status = menu.hasInvalidRecipe() ? "invalid" : !hasPattern ? "hint"
                : menu.getRecipeInv().getItem(ContainerRecipePaper.RESULT_SLOT).isEmpty() ? "unmatched" : "configured";
        Component text = Component.translatable("gui.shincolle_kai.recipepaper." + status);
        // Fit longer translations inside the existing screen without covering the inventory.
        float scale = Math.min(1F, 168F / Math.max(1, this.font.width(text)));
        graphics.pose().pushPose();
        graphics.pose().translate(this.imageWidth / 2F, 73, 0);
        graphics.pose().scale(scale, scale, 1F);
        graphics.drawString(this.font, text, -this.font.width(text) / 2, 0,
                menu.hasInvalidRecipe() ? 0xAA0000 : 0x666666, false);
        graphics.pose().popPose();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        this.renderTooltip(graphics, mouseX, mouseY);
    }
}
