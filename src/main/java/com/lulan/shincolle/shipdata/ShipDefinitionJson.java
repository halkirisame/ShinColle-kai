package com.lulan.shincolle.shipdata;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/** Checks byte limits before decoding and rejects duplicate members before Gson can overwrite them. */
public final class ShipDefinitionJson {
    private ShipDefinitionJson() {
    }

    public static JsonElement read(byte[] bytes) throws IOException {
        if (bytes.length > ShipDefinitionFormat.MAX_JSON_BYTES) {
            throw new IllegalArgumentException("JSON file exceeds " + ShipDefinitionFormat.MAX_JSON_BYTES + " bytes");
        }
        String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        checkStringTokens(text);
        try (JsonReader reader = new JsonReader(new StringReader(text))) {
            reader.setLenient(false);
            JsonElement result = readValue(reader, 0);
            if (reader.peek() != JsonToken.END_DOCUMENT) {
                throw new IllegalArgumentException("trailing JSON content");
            }
            return result;
        }
    }

    private static void checkStringTokens(String text) throws IOException {
        boolean inString = false;
        boolean escaped = false;
        for (int i = 0; i < text.length(); i++) {
            char character = text.charAt(i);
            if (!inString) {
                inString = character == '"';
            } else if (character < 0x20) {
                // Check source characters, not decoded values: escaped controls are valid JSON.
                throw new IOException("unescaped control character in JSON string at offset " + i);
            } else if (escaped) {
                if ("\"\\/bfnrt".indexOf(character) >= 0) {
                    escaped = false;
                } else if (character == 'u' && hasHexDigits(text, i + 1)) {
                    escaped = false;
                    i += 4;
                } else {
                    throw new IOException("invalid escape in JSON string at offset " + i);
                }
            } else if (character == '\\') {
                escaped = true;
            } else if (character == '"') {
                inString = false;
            }
        }
    }

    private static boolean hasHexDigits(String text, int start) {
        if (start + 4 > text.length()) {
            return false;
        }
        for (int i = start; i < start + 4; i++) {
            char digit = text.charAt(i);
            if (digit > 'f' || Character.digit(digit, 16) < 0) {
                return false;
            }
        }
        return true;
    }

    private static JsonElement readValue(JsonReader reader, int depth) throws IOException {
        if (depth > ShipDefinitionFormat.MAX_JSON_DEPTH) {
            throw new IllegalArgumentException("JSON nesting exceeds " + ShipDefinitionFormat.MAX_JSON_DEPTH);
        }
        return switch (reader.peek()) {
            case BEGIN_OBJECT -> {
                JsonObject object = new JsonObject();
                reader.beginObject();
                while (reader.hasNext()) {
                    String key = reader.nextName();
                    if (object.has(key)) {
                        throw new IllegalArgumentException("duplicate JSON key '" + key + "'");
                    }
                    object.add(key, readValue(reader, depth + 1));
                }
                reader.endObject();
                yield object;
            }
            case BEGIN_ARRAY -> {
                JsonArray array = new JsonArray();
                reader.beginArray();
                while (reader.hasNext()) {
                    array.add(readValue(reader, depth + 1));
                }
                reader.endArray();
                yield array;
            }
            case NUMBER -> new JsonPrimitive(new BigDecimal(reader.nextString()));
            case STRING -> new JsonPrimitive(reader.nextString());
            case BOOLEAN -> new JsonPrimitive(reader.nextBoolean());
            case NULL -> {
                reader.nextNull();
                yield JsonNull.INSTANCE;
            }
            default -> throw new IllegalArgumentException("expected a JSON value");
        };
    }
}
