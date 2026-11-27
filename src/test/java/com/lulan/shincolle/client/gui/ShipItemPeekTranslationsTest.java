package com.lulan.shincolle.client.gui;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShipItemPeekTranslationsTest {
    @Test
    void peekBindingAndCategoryExistInEverySupportedLanguage() throws IOException {
        Path lang = Path.of("src/main/resources/assets/shincolle_kai/lang");
        for (String language : List.of("en_us", "ja_jp", "zh_cn", "zh_tw")) {
            var translations = JsonParser.parseString(Files.readString(lang.resolve(language + ".json")))
                    .getAsJsonObject();
            assertEquals("ShinColle-kai", translations.get("key.categories.shincolle_kai.general").getAsString());
            assertTrue(translations.has("key.shincolle_kai.peek_ship_items"), language);
            assertFalse(translations.get("key.shincolle_kai.peek_ship_items").getAsString().isBlank(), language);
            assertFalse(translations.get("gui.shincolle_kai.book.chap0.text11d0").getAsString()
                    .toLowerCase(java.util.Locale.ROOT).contains("shift"), language);
        }
    }
}
