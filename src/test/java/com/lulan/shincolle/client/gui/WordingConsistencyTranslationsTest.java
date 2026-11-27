package com.lulan.shincolle.client.gui;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WordingConsistencyTranslationsTest {
    @Test
    void englishNamesAndLabelsUseConsistentWording() throws IOException {
        JsonObject english = readLanguage("en_us");
        for (var entry : Map.of(
                "item.shincolle_kai.marriage_ring", "Shinkeiseikan Wedding Ring",
                "item.shincolle_kai.owner_paper", "Shinkeiseikan Ownership Paper",
                "gui.shincolle_kai.team.allylist", "Allies",
                "gui.shincolle_kai.team.banlist", "Enemies",
                "gui.shincolle_kai.compass",
                "Lets the ship load chunks and keep working away from the owner").entrySet()) {
            assertEquals(entry.getValue(), english.get(entry.getKey()).getAsString(), entry.getKey());
        }
        assertEquals("Ally", english.get("gui.shincolle_kai.team.ally").getAsString());
        assertEquals("Battle", english.get("gui.shincolle_kai.team.ban").getAsString());
    }

    @Test
    void simplifiedChineseSubmarineNameMatchesItsEgg() throws IOException {
        JsonObject chinese = readLanguage("zh_cn");
        assertEquals("潜水新栖姬", chinese.get("entity.shincolle_kai.ssnh").getAsString());
        assertEquals(chinese.get("item.shincolle_kai.ship_egg_74"),
                chinese.get("entity.shincolle_kai.ssnh"));
    }

    @Test
    void japanesePunctuationAndAutoPumpDescriptionArePresent() throws IOException {
        JsonObject japanese = readLanguage("ja_jp");
        for (var entry : Map.of(
                "book.chap1.text17d0", "相談シテクダサイ。",
                "book.chap1.text2d1", "見ツケルコトガデキマス。",
                "book.chap3.text15d1", "オ尻、背後。<br>",
                "repairgoddess", "撃沈ヲ防グ。").entrySet()) {
            String text = japanese.get("gui.shincolle_kai." + entry.getKey()).getAsString();
            assertTrue(text.contains(entry.getValue()), entry.getKey());
            assertFalse(text.contains("."), entry.getKey());
        }
        assertTrue(japanese.get("gui.shincolle_kai.book.chap4.text0d1").getAsString()
                .contains("ON/OFFヲ切リ替エル"));
        assertTrue(japanese.get("gui.shincolle_kai.book.chap3.text8d1").getAsString().endsWith(
                "<br><br>§916.自動抽水§0: 深海ポンプステーション(装備)ヲ持ツ棲艦ガ、液体ヲ深海液体ドラム缶ニ移シマス。"
                + "ガラス瓶ヲ持タセルト、経験値オーブモ集メマス。"));
    }

    private static JsonObject readLanguage(String language) throws IOException {
        return JsonParser.parseString(Files.readString(Path.of(
                "src/main/resources/assets/shincolle_kai/lang", language + ".json"))).getAsJsonObject();
    }
}
