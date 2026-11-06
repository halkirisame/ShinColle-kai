package com.lulan.shincolle.client.gui;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShipClickPreferenceTranslationsTest {
    @Test
    void labelAndDescriptionExistInEverySupportedLanguage() throws IOException {
        Path lang = Path.of("src/main/resources/assets/shincolle_kai/lang");
        for (String language : List.of("en_us", "ja_jp", "zh_cn", "zh_tw")) {
            var translations = JsonParser.parseString(Files.readString(lang.resolve(language + ".json")))
                    .getAsJsonObject();
            for (String key : List.of("gui.shincolle_kai.aisettings.swapclick",
                    "gui.shincolle_kai.aisettings.swapclick.desc")) {
                assertTrue(translations.has(key), language + ": " + key);
                assertFalse(translations.get(key).getAsString().isBlank(), language + ": " + key);
            }
        }
    }
}
