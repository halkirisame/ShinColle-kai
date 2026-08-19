package com.lulan.shincolle.client.gui;

import com.lulan.shincolle.client.gui.inventory.ContainerSmallShipyard;
import com.lulan.shincolle.network.C2SGUIInputPacket;
import com.lulan.shincolle.network.ModNetworking;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.tileentity.TileEntitySmallShipyard;
import com.lulan.shincolle.utility.GuiHelper;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/**
 * GUI screen for the small shipyard block.
 * Renders title, fuel bar, build progress, and build type buttons.
 */
public class GuiSmallShipyard extends AbstractContainerScreen<ContainerSmallShipyard> {

    private static final int BASE_GUI_WIDTH = 176;
    private static final int MATERIAL_PANEL_X = 180;
    private static final int MATERIAL_ROW_Y = 20;
    private static final int MATERIAL_ROW_HEIGHT = 18;
    private static final int MATERIAL_CONTROL_Y = 99;
    private static final int[] MATERIAL_DELTAS = {-10, -1, 1, 10};
    private static final String[] MATERIAL_LABELS = {"G", "A", "M", "P"};
    private static final ResourceLocation TEXTURE = new ResourceLocation("shincolle",
            "textures/gui/guismallshipyard.png");

    public GuiSmallShipyard(ContainerSmallShipyard menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
        this.imageWidth = 296;
        this.imageHeight = 164;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        // Render background texture
        graphics.blit(TEXTURE, this.leftPos, this.topPos, 0, 0, BASE_GUI_WIDTH, this.imageHeight);

        graphics.fill(this.leftPos + 176, this.topPos, this.leftPos + this.imageWidth,
                this.topPos + 123, 0xD0202020);
        int selected = Math.max(0, Math.min(this.menu.getSelectMat(), 3));
        graphics.fill(this.leftPos + MATERIAL_PANEL_X - 2,
                this.topPos + MATERIAL_ROW_Y + selected * MATERIAL_ROW_HEIGHT - 2,
                this.leftPos + this.imageWidth - 4,
                this.topPos + MATERIAL_ROW_Y + selected * MATERIAL_ROW_HEIGHT + 10,
                0x804F6A8A);

        // Fuel bar: vertical bar from bottom up, using texture at U=176
        int fuelPercent = this.menu.getFuelPercent(); // 0-1000
        int maxBarHeight = 31;
        int scaleBar = fuelPercent * maxBarHeight / 1000;
        if (scaleBar > 0) {
            graphics.blit(TEXTURE, this.leftPos + 10, this.topPos + 48 - scaleBar,
                    176, 47 - scaleBar, 12, scaleBar);
        }

        // Build type selection overlays
        int buildType = this.menu.getBuildType();
        if (buildType > 0) {
            // Ship button highlight (type 1 or 3)
            if (buildType == 1 || buildType == 3) {
                if (buildType == 3) {
                    // Loop animation: 6-frame cycle
                    int frame = (int) ((System.currentTimeMillis() / 200) % 6);
                    graphics.blit(TEXTURE, this.leftPos + 123, this.topPos + 17,
                            176, 65 + frame * 18, 18, 18);
                } else {
                    graphics.blit(TEXTURE, this.leftPos + 123, this.topPos + 17,
                            176, 47, 18, 18);
                }
            }

            // Equip button highlight (type 2 or 4)
            if (buildType == 2 || buildType == 4) {
                if (buildType == 4) {
                    int frame = (int) ((System.currentTimeMillis() / 200) % 6);
                    graphics.blit(TEXTURE, this.leftPos + 143, this.topPos + 17,
                            176, 65 + frame * 18, 18, 18);
                } else {
                    graphics.blit(TEXTURE, this.leftPos + 143, this.topPos + 17,
                            176, 47, 18, 18);
                }
            }
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        // Draw block name centered at top
        String blockName = Component.translatable("container.shincolle.small_shipyard").getString();
        graphics.drawString(this.font, blockName,
                this.imageWidth / 2 - this.font.width(blockName) / 2, 6, 0x404040, false);

        int buildType = this.menu.getBuildType();
        int buildTime = this.menu.getBuildTimeSeconds();

        if (buildType > 0 && buildTime > 0) {
            // Building in progress: show remaining time
            String timeStr = String.format("%d:%02d", buildTime / 60, buildTime % 60);
            graphics.drawString(this.font, timeStr,
                    71 - this.font.width(timeStr) / 2, 51, 0x404040, false);
        } else if (buildType > 0) {
            // Build type set but no materials/fuel yet
            String noMat = Component.translatable("gui.shincolle.nomaterial").getString();
            if (noMat.equals("gui.shincolle.nomaterial"))
                noMat = "No material";
            graphics.drawString(this.font, noMat,
                    80 - this.font.width(noMat) / 2, 67, 0xFF4433, false);
        }

        graphics.drawString(this.font, "Use / Stock", MATERIAL_PANEL_X, 6, 0xFFFFFF, false);
        for (int i = 0; i < 4; i++) {
            String material = MATERIAL_LABELS[i] + "  " + this.menu.getMatBuild(i)
                    + " / " + this.menu.getMatStock(i);
            graphics.drawString(this.font, material, MATERIAL_PANEL_X,
                    MATERIAL_ROW_Y + i * MATERIAL_ROW_HEIGHT, 0xFFFFFF, false);
        }
        String[] controls = {"-10", "-1", "+1", "+10"};
        for (int i = 0; i < controls.length; i++) {
            int x = MATERIAL_PANEL_X + i * 28;
            graphics.fill(x, MATERIAL_CONTROL_Y, x + 24, MATERIAL_CONTROL_Y + 12, 0xFF444444);
            graphics.drawString(this.font, controls[i], x + 3, MATERIAL_CONTROL_Y + 2, 0xFFFFFF, false);
        }

        // Player inventory label
        graphics.drawString(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, 0x404040,
                false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int xClick = (int) mouseX - this.leftPos;
            int yClick = (int) mouseY - this.topPos;

            int btn = GuiHelper.getButton(ID.Gui.SMALLSHIPYARD, 0, xClick, yClick);

            if (btn >= 0) {
                TileEntitySmallShipyard tile = this.menu.getTile();
                if (tile != null) {
                    int buildType = this.menu.getBuildType();
                    int newType = 0;

                    if (btn == 0) {
                        // Ship button: cycle NONE -> SHIP -> SHIP_LOOP -> NONE
                        newType = switch (buildType) {
                            case 0 -> 1; // NONE -> SHIP
                            case 1 -> 3; // SHIP -> SHIP_LOOP
                            case 3 -> 0; // SHIP_LOOP -> NONE
                            default -> 1; // any other -> SHIP
                        };
                    } else if (btn == 1) {
                        // Equip button: cycle NONE -> EQUIP -> EQUIP_LOOP -> NONE
                        newType = switch (buildType) {
                            case 0 -> 2; // NONE -> EQUIP
                            case 2 -> 4; // EQUIP -> EQUIP_LOOP
                            case 4 -> 0; // EQUIP_LOOP -> NONE
                            default -> 2; // any other -> EQUIP
                        };
                    }

                    BlockPos pos = tile.getBlockPos();
                    ModNetworking.sendToServer(new C2SGUIInputPacket(
                            C2SGUIInputPacket.TileBtn,
                            new int[]{0, pos.getX(), pos.getY(), pos.getZ(),
                                    ID.B.Shipyard_Type, newType}));
                }
                return true;
            }

            if (xClick >= MATERIAL_PANEL_X && xClick <= this.imageWidth - 4) {
                for (int material = 0; material < 4; material++) {
                    int rowTop = MATERIAL_ROW_Y + material * MATERIAL_ROW_HEIGHT - 2;
                    if (yClick >= rowTop && yClick <= rowTop + 13) {
                        TileEntitySmallShipyard tile = this.menu.getTile();
                        if (tile != null) {
                            sendTileButton(tile.getBlockPos(), ID.B.Shipyard_SelectMat, material);
                        }
                        return true;
                    }
                }
                if (yClick >= MATERIAL_CONTROL_Y && yClick <= MATERIAL_CONTROL_Y + 12) {
                    for (int control = 0; control < MATERIAL_DELTAS.length; control++) {
                        int controlX = MATERIAL_PANEL_X + control * 28;
                        if (xClick >= controlX && xClick <= controlX + 24) {
                            TileEntitySmallShipyard tile = this.menu.getTile();
                            if (tile != null) {
                                sendTileButton(tile.getBlockPos(), ID.B.Shipyard_INCDEC,
                                        MATERIAL_DELTAS[control]);
                            }
                            return true;
                        }
                    }
                }
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void sendTileButton(BlockPos pos, int buttonId, int value) {
        ModNetworking.sendToServer(new C2SGUIInputPacket(
                C2SGUIInputPacket.TileBtn,
                new int[]{0, pos.getX(), pos.getY(), pos.getZ(), buttonId, value}));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        this.renderTooltip(graphics, mouseX, mouseY);

        // Fuel tooltip on hover
        int localX = mouseX - this.leftPos;
        int localY = mouseY - this.topPos;
        if (localX >= 10 && localX <= 22 && localY >= 18 && localY <= 49) {
            int fuelPct = this.menu.getFuelPercent();
            String fuelText = String.format("Fuel: %.1f%%", fuelPct / 10.0);
            graphics.renderTooltip(this.font, Component.literal(fuelText), mouseX, mouseY);
        }
    }
}
