package com.lulan.shincolle.client.gui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BookNavigationTest {
    @Test
    void linkHistoryReturnsToTheSourceInReverseOrder() {
        BookNavigation navigation = new BookNavigation(7);
        assertFalse(navigation.canGoBack());
        assertTrue(navigation.previousPage().isEmpty());
        navigation.rememberLinkSource(0, 3);
        navigation.visit(2, 0);
        navigation.rememberLinkSource(2, 0);
        navigation.visit(1, 4);
        assertEquals(new BookNavigation.Page(2, 0), navigation.previousPage().orElseThrow());
        assertEquals(new BookNavigation.Page(2, 0), navigation.back().orElseThrow());
        assertEquals(new BookNavigation.Page(0, 3), navigation.back().orElseThrow());
        assertFalse(navigation.canGoBack());
        assertTrue(navigation.back().isEmpty());
    }

    @Test
    void seventeenthLinkDropsTheOldestSourceOnly() {
        BookNavigation navigation = new BookNavigation(7);
        for (int page = 0; page < 17; page++)
            navigation.rememberLinkSource(1, page);
        for (int page = 16; page > 0; page--)
            assertEquals(new BookNavigation.Page(1, page), navigation.back().orElseThrow());
        assertFalse(navigation.canGoBack());
    }

    @Test
    void pageTurnsAndChapterTabsKeepTheReturnHistory() {
        BookNavigation navigation = new BookNavigation(7);
        navigation.rememberLinkSource(0, 5);
        navigation.visit(2, 3);
        navigation.visit(2, 4);
        navigation.visit(1, navigation.pageForChapter(2, 1));
        assertEquals(new BookNavigation.Page(0, 5), navigation.back().orElseThrow());
    }

    @Test
    void anotherChapterRemembersItsLastPageButTheCurrentTabStartsAtZero() {
        BookNavigation navigation = new BookNavigation(7);
        navigation.visit(0, 3);
        navigation.visit(2, 5);
        assertEquals(3, navigation.pageForChapter(2, 0));
        assertEquals(5, navigation.pageForChapter(0, 2));
        assertEquals(0, navigation.pageForChapter(2, 2));
        assertEquals(0, navigation.pageForChapter(2, 6));
        navigation.visit(2, navigation.pageForChapter(2, 2));
        assertEquals(0, navigation.pageForChapter(0, 2));
    }

    @Test
    void visitsFromLinksAndBackReplaceTheRememberedChapterPage() {
        BookNavigation navigation = new BookNavigation(7);
        navigation.visit(0, 7);
        navigation.rememberLinkSource(0, 7);
        navigation.visit(1, 11);
        navigation.visit(0, 1);
        BookNavigation.Page back = navigation.back().orElseThrow();
        navigation.visit(back.chapter(), back.page());
        assertEquals(7, navigation.pageForChapter(1, 0));
        assertEquals(11, navigation.pageForChapter(0, 1));
    }

    @Test
    void closingClearsBothMemoriesAndScreensDoNotShareState() {
        BookNavigation first = new BookNavigation(7);
        first.visit(4, 12);
        first.rememberLinkSource(0, 14);
        BookNavigation second = new BookNavigation(7);
        assertFalse(second.canGoBack());
        assertEquals(0, second.pageForChapter(0, 4));
        first.clear();
        assertFalse(first.canGoBack());
        for (int chapter = 0; chapter < 7; chapter++)
            assertEquals(0, first.pageForChapter((chapter + 1) % 7, chapter));
    }
}
