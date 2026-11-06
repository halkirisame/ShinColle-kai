package com.lulan.shincolle.client.gui;

import com.lulan.shincolle.reference.Enums;
import com.lulan.shincolle.reference.Reference;
import com.lulan.shincolle.reference.Values;
import com.lulan.shincolle.utility.BookTitleHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.Arrays;
import java.util.List;

/**
 * Draw book text content for the admiral's desk book tab.
 * <p>
 * Book range: left:15,32 ~ 115,172 right:135,32 ~ 235,172
 * Title center: left:64 right:185 y:38
 * Text: left:13,48 right:135,48 width:100
 * <p>
 * For recipe picture material positions:
 * (3,-3) (23,-3) (43,-3)
 * (3,17) (23,17) (43,17) (81,17)
 * (3,37) (23,37) (43,37)
 */
public class GuiBook {

    public static final int[] PageLimit = new int[]{14, 28, 6, 19, 26, 19, 4};
    private static final ResourceLocation BOOK_PIC_01 = new ResourceLocation(Reference.MOD_ID,
            "textures/gui/book/bookpic01.png");
    public static int PageLeftCurrent = 0;
    public static int PageRightCurrent = 0;
    public static int PageWidth = 135;
    public static int Page0LX = 13;
    public static int Page0RX = 133;
    public static int Page0Y = 48;
    public static int PageTLX = 13;
    public static int PageTRX = 162;
    public static int PageTY = 58;
    private static GuiGraphics currentGraphics;
    private static Font font;
    private static int numChap;
    private static int numPage;
    /**
     * Tick counter for animated page cycling
     */
    private static int tickCounter = 0;

    public GuiBook() {
    }

    /**
     * Draw book content. Called from GuiDesk when in book mode.
     *
     * @param graphics     the GuiGraphics context
     * @param fontRenderer the font renderer
     * @param chap         chapter number
     * @param page         page number
     * @param mouseX       mouse X relative to GUI left
     * @param mouseY       mouse Y relative to GUI top
     * @param rightOffset  extra space reserved below the return link on the right page
     */
    @SuppressWarnings("rawtypes")
    public static void drawBookContent(GuiGraphics graphics, Font fontRenderer, int chap, int page,
                                       int mouseX, int mouseY, int rightOffset) {
        int index = getIndexID(chap, page);
        List cont = Values.BookList.get(index);

        currentGraphics = graphics;
        font = fontRenderer;
        numChap = chap;
        numPage = page;
        tickCounter++;

        if (cont == null) {
            cont = Arrays.asList(new int[]{0, 0, 0, 0},
                    new int[]{0, 1, 0, 0});
        }

        drawBookContent(cont, mouseX, mouseY, rightOffset);
    }

    /**
     * Draw book body text with legacy line-break handling.
     *
     * @param graphics     the GuiGraphics context
     * @param fontRenderer the font renderer
     * @param str          body text
     * @param x            x position
     * @param y            y position
     */
    public static void drawBookString(GuiGraphics graphics, Font fontRenderer, String str, int x, int y) {
        currentGraphics = graphics;
        font = fontRenderer;
        drawStringWithSpecialSymbol(str, x, y);
    }

    @SuppressWarnings("rawtypes")
    private static void drawBookContent(List cont, int mouseX, int mouseY, int rightOffset) {
        if (cont == null)
            return;

        int leftRand = 0;
        int rightRand = 1;

        // Page++ every ~128 ticks
        if ((tickCounter & 127) == 0) {
            PageLeftCurrent += 2;
            PageRightCurrent += 2;
        }

        for (Object obj : cont) {
            int[] getc = (int[]) obj;

            if (getc != null) {
                switch (getc[0]) {
                    case 0: // text
                        drawBookText(getc[1], getc[2], getc[3], rightOffset);
                        break;
                    case 1: // picture
                        if (PageLeftCurrent > leftRand)
                            PageLeftCurrent = 0;
                        if (PageRightCurrent > rightRand)
                            PageRightCurrent = 1;

                        if (getc[1] == PageLeftCurrent || getc[1] == PageRightCurrent)
                            drawBookPic(getc, rightOffset);
                        break;
                    case 2: // icon
                        if (PageLeftCurrent > leftRand)
                            PageLeftCurrent = 0;
                        if (PageRightCurrent > rightRand)
                            PageRightCurrent = 1;

                        if (getc[1] == PageLeftCurrent || getc[1] == PageRightCurrent)
                            drawBookIcon(getc[1], getc[2], getc[3] + ((getc[1] & 1) == 1 ? rightOffset : 0), getc[4]);
                        break;
                    case 3: // page setting
                        leftRand = getc[1];
                        rightRand = getc[2];
                        break;
                    case 4: // page link
                        drawLink(currentGraphics, font, bookLink(numChap, numPage, getc, rightOffset), mouseX, mouseY);
                        break;
                }
            }
        }
    }

    /**
     * Draw book text (title + page text).
     *
     * @param pageSide 0=left, 1=right
     * @param offX     x offset
     * @param offY     y offset
     */
    private static void drawBookText(int pageSide, int offX, int offY, int rightOffset) {
        drawTitleText();
        currentGraphics.pose().pushPose();
        currentGraphics.pose().translate(0, pageSide > 0 ? rightOffset : 0, 0);
        drawPageText(pageSide, offX, offY);
        currentGraphics.pose().popPose();
    }

    /**
     * Resolve the lang key for the current page's title.
     * <p>
     * Chapter 0 has a title per page ({@code chap0.title<page>}) when one is
     * translated, falling back to the chapter-wide {@code chap0.title} otherwise.
     * Every other chapter always uses the per-page key {@code chap<chap>.title<page>}.
     *
     * @param chap chapter number
     * @param page page number
     * @return the lang key to translate for this page's title
     */
    static String titleKey(int chap, int page) {
        return BookTitleHelper.titleKey(chap, page, I18n::exists);
    }

    /**
     * Draw title text centered at top of page.
     */
    private static void drawTitleText() {
        String str = Component.translatable(titleKey(numChap, numPage)).getString();

        int strlen = (int) (font.width(str) * 0.5F);

        // Draw title with underline formatting, scaled to 0.8
        String formattedStr = ChatFormatting.UNDERLINE + str;

        var pose = currentGraphics.pose();
        pose.pushPose();
        pose.scale(0.8F, 0.8F, 0.8F);
        currentGraphics.drawString(font, formattedStr, 82 - strlen, 40,
                Enums.EnumColors.RED_DARK.getValue(), false);
        pose.popPose();
    }

    /**
     * Draw page text body.
     */
    private static void drawPageText(int pageSide, int offX, int offY) {
        int picY = PageTY + offY - 4;
        int picX = PageTLX;
        if (pageSide > 0)
            picX = PageTRX;
        picX += offX;

        String key = "gui.shincolle_kai.book.chap" + numChap + ".text" + numPage + "d" + pageSide;
        String str = Component.translatable(key).getString();

        // Skip if translation key doesn't exist (getString returns the key itself)
        if (str.equals(key))
            return;

        drawStringWithSpecialSymbol(str, picX, picY);
    }

    /**
     * Draw string with newline support.
     * Splits on &lt;br&gt; / &lt;BR&gt; / &lt;br/&gt; / &lt;BR/&gt; tags
     * and draws each line using word-wrap within PageWidth.
     */
    private static void drawStringWithSpecialSymbol(String str, int x, int y) {
        // Split on <br>, <BR>, <br/>, <BR/> tags (matching original
        // CalcHelper.stringConvNewlineToArray)
        String[] strArray = str.split("<[Bb][Rr]\\s*/?>");

        var pose = currentGraphics.pose();
        pose.pushPose();
        pose.scale(0.8F, 0.8F, 0.8F);

        int newY = y;
        for (String s : strArray) {
            // drawWordWrap handles text wrapping within the given width
            currentGraphics.drawWordWrap(font, Component.literal(s), x, newY, PageWidth,
                    0x000000);
            // Estimate line height based on wrapped text
            int lineCount = Math.max(1, font.split(Component.literal(s), PageWidth).size());
            newY += lineCount * font.lineHeight;
        }

        pose.popPose();
    }

    /**
     * Draw book picture.
     *
     * @param parms 0:type, 1:pageSide, 2:posX, 3:posY, 4:picID, 5:picU, 6:picV,
     *              7:sizeX, 8:sizeY
     */
    private static void drawBookPic(int[] parms, int rightOffset) {
        if (parms == null || parms.length != 9)
            return;

        int pageSide = parms[1];
        int posX = parms[2];
        int posY = parms[3];
        int picID = parms[4];
        int picU = parms[5];
        int picV = parms[6];
        int sizeX = parms[7];
        int sizeY = parms[8];

        int picY = Page0Y + posY + ((pageSide & 1) == 1 ? rightOffset : 0);
        int picX = Page0LX;
        if ((pageSide & 1) == 1)
            picX = Page0RX;
        picX += posX;

        // Set picture texture
        ResourceLocation tex;
        tex = BOOK_PIC_01;

        // Draw picture using GuiGraphics blit
        currentGraphics.blit(tex, picX, picY, picU, picV, sizeX, sizeY);
    }

    /**
     * Draw book item icon.
     *
     * @param pageSide 0=left, 1=right, 2=left random, 3=right random
     * @param offX     x offset
     * @param offY     y offset
     * @param iconID   icon ID from ID.Icon
     */
    private static void drawBookIcon(int pageSide, int offX, int offY, int iconID) {
        int picY = Page0Y + offY;
        int picX = Page0LX;
        if ((pageSide & 1) == 1)
            picX = Page0RX;
        picX += offX;

        drawItemIcon(getItemStackForIcon(iconID), picX, picY);
    }

    /**
     * Draw an item icon at the specified position.
     */
    private static void drawItemIcon(ItemStack item, int x, int y) {
        if (item != null && !item.isEmpty()) {
            currentGraphics.renderItem(item, x, y);
        }
    }

    public record BookIcon(ItemStack stack, int x, int y) {
    }

    public record BookLink(int targetChap, int targetPage, String text, int x, int y) {
        boolean contains(Font fontRenderer, int mouseX, int mouseY) {
            return mouseX >= x && mouseX < x + fontRenderer.width(text) * 0.8F
                    && mouseY >= y && mouseY < y + fontRenderer.lineHeight * 0.8F;
        }
    }

    private static BookLink bookLink(int chap, int page, int[] entry, int rightOffset) {
        String key = "gui.shincolle_kai.book.chap" + chap + ".link" + page + "_" + entry[6];
        String text = "» " + Component.translatable(key).getString();
        int x = (entry[1] == 0 ? Page0LX : Page0RX) + entry[2];
        return new BookLink(entry[4], entry[5], text, x, Page0Y + entry[3] + (entry[1] == 1 ? rightOffset : 0));
    }

    static BookLink backLink(Font fontRenderer, int chapter, int page) {
        String text = "« " + Component.translatable("gui.shincolle_kai.book.back").getString();
        int x = 235 - (int) Math.ceil(fontRenderer.width(text) * 0.8F);
        return new BookLink(chapter, page, text, x, 32);
    }

    static void drawLink(GuiGraphics graphics, Font fontRenderer, BookLink link, int mouseX, int mouseY) {
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(link.x(), link.y(), 0);
        pose.scale(0.8F, 0.8F, 0.8F);
        int color = link.contains(fontRenderer, mouseX, mouseY) ? Enums.EnumColors.RED_DARK.getValue() : 0x0000AA;
        graphics.drawString(fontRenderer, ChatFormatting.UNDERLINE + link.text(), 0, 0, color, false);
        pose.popPose();
    }

    @SuppressWarnings("rawtypes")
    static int rightPageOffset(int chapter, int page, boolean hasBackLink) {
        if (!hasBackLink)
            return 0;
        List cont = Values.BookList.get(getIndexID(chapter, page));
        int offset = 0;
        if (cont != null) {
            for (Object obj : cont) {
                int[] entry = (int[]) obj;
                if ((entry[0] == 1 || entry[0] == 2) && (entry[1] & 1) == 1)
                    offset = Math.max(offset, 40 - (Page0Y + entry[3]));
            }
        }
        return offset;
    }

    @SuppressWarnings("rawtypes")
    public static BookLink getHoveredLink(Font fontRenderer, int chap, int page, int mouseX, int mouseY, int rightOffset) {
        List cont = Values.BookList.get(getIndexID(chap, page));
        if (cont == null)
            return null;

        for (Object obj : cont) {
            int[] entry = (int[]) obj;
            if (entry != null && entry[0] == 4) {
                BookLink link = bookLink(chap, page, entry, rightOffset);
                if (link.contains(fontRenderer, mouseX, mouseY))
                    return link;
            }
        }
        return null;
    }

    @SuppressWarnings("rawtypes")
    public static BookIcon getHoveredIcon(int chap, int page, int mouseX, int mouseY, int rightOffset) {
        int index = getIndexID(chap, page);
        List cont = Values.BookList.get(index);
        if (cont == null)
            return null;

        for (Object obj : cont) {
            int[] getc = (int[]) obj;
            if (getc != null && getc[0] == 2 && getc.length >= 5) {
                int pageSide = getc[1];
                int offX = getc[2];
                int offY = getc[3];
                int iconID = getc[4];

                int picY = Page0Y + offY + ((pageSide & 1) == 1 ? rightOffset : 0);
                int picX = Page0LX;
                if ((pageSide & 1) == 1)
                    picX = Page0RX;
                picX += offX;

                if (mouseX >= picX && mouseX < picX + 16 && mouseY >= picY && mouseY < picY + 16) {
                    return new BookIcon(getItemStackForIcon(iconID), picX, picY);
                }
            }
        }
        return null;
    }

    /**
     * Get the ItemStack being hovered over, if any.
     * Checks all icon entries on the current page against mouse position.
     *
     * @param chap   chapter number
     * @param page   page number
     * @param mouseX mouse X relative to GUI left
     * @param mouseY mouse Y relative to GUI top
     * @param rightOffset extra space reserved below the return link on the right page
     * @return the hovered ItemStack, or null if not hovering over an icon
     */
    public static ItemStack getHoveredItem(int chap, int page, int mouseX, int mouseY, int rightOffset) {
        BookIcon icon = getHoveredIcon(chap, page, mouseX, mouseY, rightOffset);
        return icon == null ? null : icon.stack();
    }

    /**
     * Reset page cycling state. Call when switching tabs or reopening GUI.
     */
    public static void resetPageCycling() {
        tickCounter = 0;
        PageLeftCurrent = 0;
        PageRightCurrent = 0;
    }

    /**
     * Get max page number for a chapter.
     */
    public static int getMaxPageNumber(int chap) {
        if (chap < PageLimit.length)
            return PageLimit[chap];
        return 0;
    }

    /**
     * Get index ID for Values.BookList lookup.
     */
    public static int getIndexID(int ch, int pg) {
        return ch * 1000 + pg;
    }

    /**
     * Get ItemStack for icon rendering.
     */
    public static ItemStack getItemStackForIcon(int itemID) {
        return Values.getItemIconMap().get((short) itemID);
    }
}
