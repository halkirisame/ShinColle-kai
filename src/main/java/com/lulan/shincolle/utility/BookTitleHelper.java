package com.lulan.shincolle.utility;

import java.util.function.Predicate;

/**
 * Pure title-key logic for the admiral's desk book, factored out of the client-only
 * {@code GuiBook} so it can be exercised from a server-side {@code @GameTest}.
 * <p>
 * {@code GuiBook} lives under {@code client.gui} and references client-only types
 * (e.g. {@code Font}, {@code GuiGraphics}); loading it from a dedicated-server
 * GameTest run can fail. This class has no client dependency, so it is safe to call
 * from either side.
 */
public final class BookTitleHelper {

    private BookTitleHelper() {
    }

    /**
     * Resolve the lang key for a book page's title.
     * <p>
     * Chapter 0 has one title per page when a {@code chap0.title<page>} key exists,
     * falling back to the chapter-wide {@code chap0.title} otherwise. Every other
     * chapter always uses the per-page key {@code chap<chap>.title<page>}.
     *
     * @param chap   chapter number
     * @param page   page number
     * @param hasKey predicate reporting whether a lang key has a translation
     *               (e.g. {@code I18n::exists} on the client)
     * @return the lang key to translate for this page's title
     */
    public static String titleKey(int chap, int page, Predicate<String> hasKey) {
        String base = "gui.shincolle_kai.book.chap" + chap + ".title";
        if (chap == 0) {
            String perPage = base + page;
            return hasKey.test(perPage) ? perPage : base;
        }
        return base + page;
    }
}
