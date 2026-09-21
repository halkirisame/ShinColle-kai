package com.lulan.shincolle.client.gui;

import com.lulan.shincolle.client.sound.ShipSoundChannel;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

/** Client-local sound tab with one generated row per {@link ShipSoundChannel}. */
public class GuiShipSoundSettings extends Screen {

    private static final int PANEL_WIDTH = 240;
    private static final int HEADER_HEIGHT = 22;
    private static final int ROW_HEIGHT = 38;
    private static final int FOOTER_HEIGHT = 24;
    private static final int VOLUME_STEP = 10;

    private final Screen parent;
    private final List<RowWidgets> rowWidgets = new ArrayList<>();
    private int panelLeft;
    private int panelTop;
    private int panelHeight;

    public GuiShipSoundSettings(Screen parent) {
        super(Component.literal(tr("gui.shincolle_kai.sound.title", "Ship Sound Settings")));
        this.parent = parent;
    }

    private static String tr(String key, String fallback) {
        String localized = I18n.get(key);
        return localized.equals(key) ? fallback : localized;
    }

    @Override
    protected void init() {
        ShipSoundChannel[] channels = ShipSoundChannel.values();
        this.panelHeight = HEADER_HEIGHT + channels.length * ROW_HEIGHT + FOOTER_HEIGHT;
        this.panelLeft = (this.width - PANEL_WIDTH) / 2;
        this.panelTop = (this.height - panelHeight) / 2;
        this.rowWidgets.clear();

        this.addRenderableWidget(Button.builder(Component.literal("AI"), button -> onClose())
                .bounds(panelLeft + 5, panelTop + 3, 34, 16)
                .build());
        Button soundTab = this.addRenderableWidget(Button.builder(
                        Component.literal(tr("gui.shincolle_kai.sound.tab", "Sound")), button -> {
                        })
                .bounds(panelLeft + 41, panelTop + 3, 58, 16)
                .build());
        soundTab.active = false;
        this.addRenderableWidget(Button.builder(Component.literal("X"), button -> onClose())
                .bounds(panelLeft + PANEL_WIDTH - 19, panelTop + 3, 14, 14)
                .build());

        for (int index = 0; index < channels.length; index++) {
            addChannelRow(channels[index], panelTop + HEADER_HEIGHT + index * ROW_HEIGHT);
        }
    }

    private void addChannelRow(ShipSoundChannel channel, int y) {
        Button toggle = this.addRenderableWidget(Button.builder(toggleText(channel), button -> {
            channel.setEnabled(!channel.enabled());
            button.setMessage(toggleText(channel));
        }).bounds(panelLeft + 104, y + 7, 44, 22).build());

        Button decrease = this.addRenderableWidget(Button.builder(Component.literal("<"), button -> {
            adjustVolume(channel, -VOLUME_STEP);
        }).bounds(panelLeft + 154, y + 7, 18, 22).build());

        Button increase = this.addRenderableWidget(Button.builder(Component.literal(">"), button -> {
            adjustVolume(channel, VOLUME_STEP);
        }).bounds(panelLeft + 214, y + 7, 18, 22).build());

        rowWidgets.add(new RowWidgets(channel, toggle, decrease, increase));
    }

    private static Component toggleText(ShipSoundChannel channel) {
        return Component.literal(channel.enabled() ? "ON" : "OFF");
    }

    private void adjustVolume(ShipSoundChannel channel, int delta) {
        channel.setVolumePercent(Mth.clamp(channel.volumePercent() + delta, 0, 100));
        updateVolumeButtons();
    }

    private void updateVolumeButtons() {
        for (RowWidgets row : rowWidgets) {
            int percent = row.channel().volumePercent();
            row.decrease().active = percent > 0;
            row.increase().active = percent < 100;
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        graphics.fill(panelLeft, panelTop, panelLeft + PANEL_WIDTH, panelTop + panelHeight, 0xE0202020);
        graphics.fill(panelLeft, panelTop, panelLeft + PANEL_WIDTH, panelTop + HEADER_HEIGHT, 0xE0303030);

        ShipSoundChannel[] channels = ShipSoundChannel.values();
        for (int index = 0; index < channels.length; index++) {
            ShipSoundChannel channel = channels[index];
            int y = panelTop + HEADER_HEIGHT + index * ROW_HEIGHT;
            graphics.fill(panelLeft + 6, y + 2, panelLeft + PANEL_WIDTH - 6, y + ROW_HEIGHT - 2,
                    index % 2 == 0 ? 0xD05E5A4E : 0xD06C6657);
            graphics.drawString(this.font, tr(channel.translationKey(), channel.fallback()),
                    panelLeft + 12, y + 15, 0xF0F0F0, false);
            String percent = channel.volumePercent() + "%";
            graphics.drawCenteredString(this.font, percent, panelLeft + 193, y + 14, 0xFFFF55);
        }

        graphics.drawCenteredString(this.font,
                tr("gui.shincolle_kai.sound.local", "Applies only to this client"),
                panelLeft + PANEL_WIDTH / 2, panelTop + panelHeight - 16, 0xA0A0A0);
        updateVolumeButtons();
        super.render(graphics, mouseX, mouseY, partialTick);
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

    private record RowWidgets(ShipSoundChannel channel, Button toggle, Button decrease, Button increase) {
    }
}
