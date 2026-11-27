package com.lulan.shincolle.client.gui;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lulan.shincolle.reference.Values;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BookLinksTest {
    private static final Path LANG = Path.of("src/main/resources/assets/shincolle_kai/lang");

    @Test
    void everyLinkTargetsAnExistingChapterAndPage() {
        int count = 0;
        for (var page : bookRows().entrySet()) {
            for (int[] row : page.getValue()) {
                if (row[0] != 4)
                    continue;
                assertEquals(7, row.length, "Link on page " + page.getKey());
                int side = row[1];
                int chapter = row[4];
                int targetPage = row[5];
                assertTrue(side == 0 || side == 1);
                assertTrue(chapter >= 0 && chapter < GuiBook.PageLimit.length, "Chapter on page " + page.getKey());
                assertTrue(targetPage >= 0 && targetPage <= GuiBook.PageLimit[chapter], "Target on page " + page.getKey());
                count++;
            }
        }
        assertTrue(count > 0, "BookList must contain page links");
    }

    @Test
    void everyLinkHasEnglishAndJapaneseTranslations() throws IOException {
        for (String language : List.of("en_us", "ja_jp", "zh_cn", "zh_tw")) {
            JsonObject lang = JsonParser.parseString(Files.readString(LANG.resolve(language + ".json"))).getAsJsonObject();
            for (var page : bookRows().entrySet()) {
                for (int[] row : page.getValue()) {
                    if (row[0] != 4)
                        continue;
                    String key = "gui.shincolle_kai.book.chap" + page.getKey() / 1000
                            + ".link" + page.getKey() % 1000 + "_" + row[6];
                    assertTrue(lang.has(key), language + ": " + key);
                    String text = lang.get(key).getAsString();
                    assertFalse(text.isBlank(), key);
                    assertFalse(text.startsWith("» "), "The renderer supplies the prefix: " + key);
                    assertFalse(text.contains("<br>"), "Links are single-line: " + key);
                }
            }
        }
    }

    @Test
    void tutorialLinksMatchTheChapterDestinations() {
        Map<String, String> actual = new HashMap<>();
        for (var page : bookRows().entrySet()) {
            for (int[] row : page.getValue()) {
                if (row[0] == 4) {
                    String key = page.getKey() + "_" + row[6];
                    assertFalse(actual.containsKey(key), "Duplicate link index: " + key);
                    actual.put(key, row[4] + ":" + row[5]);
                }
            }
        }
        assertEquals(Map.ofEntries(
                Map.entry("1_0", "3:11"), Map.entry("2_0", "1:0"), Map.entry("2_1", "1:1"),
                Map.entry("2_2", "1:3"), Map.entry("2_3", "1:2"),
                Map.entry("3_0", "2:0"), Map.entry("4_0", "2:1"), Map.entry("4_1", "2:5"),
                Map.entry("5_0", "2:3"), Map.entry("6_0", "1:4"), Map.entry("7_0", "1:11"),
                Map.entry("14_0", "1:0"), Map.entry("14_1", "2:0"), Map.entry("14_2", "3:0"),
                Map.entry("14_3", "4:0"), Map.entry("14_4", "5:0"), Map.entry("14_5", "6:0")), actual);
    }

    @Test
    void firstConstructionPageUsesTheExistingRecipePictureAndTenIcons() {
        var rows = bookRows();
        List<String> expected = rows.get(2000).stream()
                .filter(row -> (row[0] == 1 || row[0] == 2) && row[1] == 0)
                .map(Arrays::toString).toList();
        List<String> actual = rows.get(3).stream()
                .filter(row -> row[0] == 1 || row[0] == 2)
                .map(Arrays::toString).toList();
        assertEquals(11, expected.size());
        assertEquals(expected, actual);
    }

    private static Map<Integer, List<int[]>> bookRows() {
        Map<Integer, List<int[]>> result = new HashMap<>();
        Values.BookList.forEach((page, rows) -> result.put(page, ((List<?>) rows).stream().map(int[].class::cast).toList()));
        assertFalse(result.isEmpty(), "BookList is empty");
        return result;
    }
}
