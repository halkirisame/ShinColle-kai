package com.lulan.shincolle.client.gui;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.Optional;

/** Navigation memory belonging to one open book screen; never serialized. */
final class BookNavigation {
    private static final int HISTORY_LIMIT = 16;
    private final Deque<Page> history = new ArrayDeque<>();
    private final int[] chapterPages;

    record Page(int chapter, int page) {
    }

    BookNavigation(int chapterCount) {
        chapterPages = new int[chapterCount];
    }

    void visit(int chapter, int page) {
        chapterPages[chapter] = page;
    }

    int pageForChapter(int currentChapter, int targetChapter) {
        return currentChapter == targetChapter ? 0 : chapterPages[targetChapter];
    }

    void rememberLinkSource(int chapter, int page) {
        history.addLast(new Page(chapter, page));
        if (history.size() > HISTORY_LIMIT)
            history.removeFirst();
    }

    Optional<Page> previousPage() {
        return Optional.ofNullable(history.peekLast());
    }

    Optional<Page> back() {
        return Optional.ofNullable(history.pollLast());
    }

    boolean canGoBack() {
        return !history.isEmpty();
    }

    void clear() {
        history.clear();
        Arrays.fill(chapterPages, 0);
    }
}
