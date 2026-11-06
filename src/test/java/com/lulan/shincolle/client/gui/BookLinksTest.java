package com.lulan.shincolle.client.gui;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BookLinksTest {
    private static final Path JAVA = Path.of("src/main/java/com/lulan/shincolle");
    private static final Path LANG = Path.of("src/main/resources/assets/shincolle_kai/lang");

    @Test
    void everyLinkTargetsAnExistingChapterAndPage() throws IOException {
        String guiBook = Files.readString(JAVA.resolve("client/gui/GuiBook.java"));
        var limits = Pattern.compile("PageLimit = new int\\[\\]\\{([^}]+)}").matcher(guiBook);
        assertTrue(limits.find());
        int[] pageLimits = Arrays.stream(limits.group(1).split(","))
                .map(String::trim).mapToInt(Integer::parseInt).toArray();
        int count = 0;
        for (var page : bookRows().entrySet()) {
            for (String[] row : page.getValue()) {
                if (!row[0].equals("4"))
                    continue;
                assertEquals(7, row.length, "Link on page " + page.getKey());
                int side = Integer.parseInt(row[1]);
                int chapter = Integer.parseInt(row[4]);
                int targetPage = Integer.parseInt(row[5]);
                assertTrue(side == 0 || side == 1);
                assertTrue(chapter >= 0 && chapter < pageLimits.length, "Chapter on page " + page.getKey());
                assertTrue(targetPage >= 0 && targetPage <= pageLimits[chapter], "Target on page " + page.getKey());
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
                for (String[] row : page.getValue()) {
                    if (!row[0].equals("4"))
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
    void tutorialLinksMatchTheChapterDestinations() throws IOException {
        Map<String, String> actual = new HashMap<>();
        for (var page : bookRows().entrySet()) {
            for (String[] row : page.getValue()) {
                if (row[0].equals("4")) {
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
    void firstConstructionPageUsesTheExistingRecipePictureAndTenIcons() throws IOException {
        var rows = bookRows();
        List<String> expected = rows.get(2000).stream()
                .filter(row -> (row[0].equals("1") || row[0].equals("2")) && row[1].equals("0"))
                .map(Arrays::toString).toList();
        List<String> actual = rows.get(3).stream()
                .filter(row -> row[0].equals("1") || row[0].equals("2"))
                .map(Arrays::toString).toList();
        assertEquals(11, expected.size());
        assertEquals(expected, actual);
    }

    // Read the literal table without initializing Minecraft registries or client rendering classes.
    private static Map<Integer, List<String[]>> bookRows() throws IOException {
        String values = Files.readString(JAVA.resolve("reference/Values.java"));
        values = values.substring(values.indexOf("BookList = "));
        var pages = Pattern.compile("put\\((\\d+),\\s*Arrays\\.asList\\((.*?)\\)\\);", Pattern.DOTALL).matcher(values);
        Map<Integer, List<String[]>> result = new HashMap<>();
        while (pages.find()) {
            List<String[]> rows = new ArrayList<>();
            var arrays = Pattern.compile("new int\\[\\]\\{([^}]+)}").matcher(pages.group(2));
            while (arrays.find()) {
                rows.add(Arrays.stream(arrays.group(1).split(",")).map(String::trim).toArray(String[]::new));
            }
            assertFalse(rows.isEmpty(), "Could not parse page " + pages.group(1));
            result.put(Integer.parseInt(pages.group(1)), rows);
        }
        assertFalse(result.isEmpty(), "Could not parse BookList");
        return result;
    }
}
