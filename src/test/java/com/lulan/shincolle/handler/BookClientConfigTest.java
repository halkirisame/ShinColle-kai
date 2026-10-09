package com.lulan.shincolle.handler;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BookClientConfigTest {

    private static final int[] PAGE_LIMITS = {13, 28, 6, 19, 26, 19, 4};

    @TempDir
    Path directory;

    @Test
    void bookmarkSurvivesClosingAndReloadingTheConfigFile() {
        Path path = directory.resolve("book.toml");
        try {
            try (CommentedFileConfig config = CommentedFileConfig.builder(path).sync().build()) {
                config.load();
                BookClientConfig.CLIENT_SPEC.setConfig(config);
                assertEquals(new BookClientConfig.Bookmark(0, 0), BookClientConfig.read(PAGE_LIMITS));
                BookClientConfig.save(4, 17);
            }
            try (CommentedFileConfig config = CommentedFileConfig.builder(path).sync().build()) {
                config.load();
                BookClientConfig.CLIENT_SPEC.setConfig(config);
                assertEquals(new BookClientConfig.Bookmark(4, 17), BookClientConfig.read(PAGE_LIMITS));
                BookClientConfig.save(1, 0);
            }
            try (CommentedFileConfig config = CommentedFileConfig.builder(path).sync().build()) {
                config.load();
                BookClientConfig.CLIENT_SPEC.setConfig(config);
                assertEquals(new BookClientConfig.Bookmark(1, 0), BookClientConfig.read(PAGE_LIMITS));
            }
        } finally {
            BookClientConfig.CLIENT_SPEC.setConfig(null);
        }
    }

    @Test
    void editedBookmarkIsCorrectedAndPageIsBoundedByItsChapter() {
        try (CommentedFileConfig config = CommentedFileConfig.builder(directory.resolve("edited.toml"))
                .sync().build()) {
            config.load();
            config.set("bookmark.chapter", -1);
            config.set("bookmark.page", -9);
            BookClientConfig.CLIENT_SPEC.setConfig(config);
            assertEquals(new BookClientConfig.Bookmark(0, 0), BookClientConfig.read(PAGE_LIMITS));
            BookClientConfig.save(2, Integer.MAX_VALUE);
            assertEquals(new BookClientConfig.Bookmark(2, 6), BookClientConfig.read(PAGE_LIMITS));
        } finally {
            BookClientConfig.CLIENT_SPEC.setConfig(null);
        }
    }
}
