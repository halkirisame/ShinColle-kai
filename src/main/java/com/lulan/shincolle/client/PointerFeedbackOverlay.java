package com.lulan.shincolle.client;

import com.lulan.shincolle.reference.Reference;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.util.FastColor;
import net.minecraft.util.FormattedCharSequence;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/** Draws pointer system-overlay messages away from the hotbar and inventory preview. */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PointerFeedbackOverlay {
    private static final String PREFIX = "chat.shincolle_kai.pointer.";
    private static final int SIDE_MARGIN = 4;
    private static final int OFFHAND_SLOT_WIDTH = 29;
    private static final int BOTTOM_MARGIN = 2;
    private static final int MAX_LINES = 4;
    private static final int MIN_WIDTH = 120;
    private static final int DURATION = 60;
    private static final int FADE_TICKS = 20;
    private static final int LINE_STEP = 9;

    private static Component message;
    private static boolean command;
    private static int remainingTicks;
    private static int lastWidth = -1;
    private static List<Line> lines = List.of();
    private static boolean centered;

    private PointerFeedbackOverlay() { }

    @SubscribeEvent
    public static void onSystemMessage(ClientChatReceivedEvent.System event) {
        if (!event.isOverlay() || !(event.getMessage().getContents() instanceof TranslatableContents translated)
                || !translated.getKey().startsWith(PREFIX)) {
            return;
        }
        event.setCanceled(true);
        message = event.getMessage();
        command = translated.getKey().startsWith(PREFIX + "command.");
        remainingTicks = DURATION;
        lastWidth = -1;
        Minecraft mc = Minecraft.getInstance();
        mc.getNarrator().say(message);
        if (mc.level != null) reflow(mc, mc.getWindow().getGuiScaledWidth());
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.screen != null) {
            message = null;
            lines = List.of();
            remainingTicks = 0;
        } else if (remainingTicks > 0) {
            remainingTicks--;
        }
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (message == null || remainingTicks <= 0 || mc.level == null || mc.screen != null
                || mc.options.hideGui) return;

        GuiGraphics graphics = event.getGuiGraphics();
        int width = graphics.guiWidth();
        if (width != lastWidth) reflow(mc, width);
        int alpha = Math.min(255, (int)((remainingTicks - event.getPartialTick()) * 255F / FADE_TICKS));
        if (alpha <= 8) return;

        int bottomY = centered ? graphics.guiHeight() - 68 + mc.font.lineHeight - 4
                : graphics.guiHeight() - BOTTOM_MARGIN;
        int color = (command ? ChatFormatting.RED.getColor() : 0xFFFFFF) | (alpha << 24);
        int background = mc.options.getBackgroundColor(0F);
        for (int i = 0; i < lines.size(); i++) {
            Line line = lines.get(i);
            int x = centered ? (width - line.width()) / 2 : SIDE_MARGIN;
            int y = bottomY - mc.font.lineHeight - (lines.size() - 1 - i) * LINE_STEP;
            if (background != 0) {
                graphics.fill(x - 2, y - 2, x + line.width() + 2, y + mc.font.lineHeight + 2,
                        FastColor.ARGB32.multiply(background, 0xFFFFFF | (alpha << 24)));
            }
            graphics.drawString(mc.font, line.text(), x, y, color, true);
        }
    }

    private static void reflow(Minecraft mc, int guiWidth) {
        int maxWidth = leftMaxWidth(guiWidth);
        centered = shouldCenter(guiWidth);
        if (centered) maxWidth = Math.max(1, guiWidth - 2 * SIDE_MARGIN);
        Font font = mc.font;
        List<FormattedCharSequence> wrapped = font.split(message, maxWidth);
        List<Line> result = new ArrayList<>();
        int count = Math.min(MAX_LINES, wrapped.size());
        for (int i = 0; i < count; i++) {
            FormattedCharSequence text = wrapped.get(i);
            if (i == MAX_LINES - 1 && wrapped.size() > MAX_LINES) {
                StringBuilder plain = new StringBuilder();
                text.accept((index, style, codePoint) -> {
                    plain.appendCodePoint(codePoint);
                    return true;
                });
                String shortened = font.plainSubstrByWidth(plain.toString(),
                        Math.max(0, maxWidth - font.width("…"))) + "…";
                text = FormattedCharSequence.forward(shortened, Style.EMPTY);
            }
            result.add(new Line(text, font.width(text)));
        }
        lines = List.copyOf(result);
        lastWidth = guiWidth;
    }

    static int leftMaxWidth(int guiWidth) {
        return guiWidth / 2 - 91 - OFFHAND_SLOT_WIDTH - 2 * SIDE_MARGIN;
    }

    static boolean shouldCenter(int guiWidth) {
        return leftMaxWidth(guiWidth) < MIN_WIDTH;
    }

    private record Line(FormattedCharSequence text, int width) { }
}
