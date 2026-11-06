package com.lulan.shincolle.shipdata;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShipAddonPublicationTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Path EXAMPLE = Path.of("examples/ship_addon");
    private static final Path SHIP = EXAMPLE.resolve("data/shincolle_example/shincolle_kai/ships/training_ship.json");
    private static final Path VISUAL = EXAMPLE.resolve("assets/shincolle_example/shincolle_kai/visuals/training_ship.json");
    private static final Pattern JSON_BLOCK = Pattern.compile("(?m)^```json\\R(.*?)^```", Pattern.DOTALL);

    @ParameterizedTest
    @ValueSource(strings = {"ship_definition", "ship_visual"})
    void exampleMatchesPublishedSchema(String kind) throws IOException {
        Path example = kind.equals("ship_definition") ? SHIP : VISUAL;
        assertValid(schema(kind), JSON.readTree(example.toFile()), example.toString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"docs/ship_addon_data.md", "docs/ship_addon_data.en.md"})
    void guideJsonIsSchemaValidAndMatchesInstalledExample(String document) throws IOException {
        var blocks = JSON_BLOCK.matcher(Files.readString(Path.of(document)));
        int count = 0;
        int variantFragments = 0;
        JsonSchema schema = schema("ship_definition");
        while (blocks.find()) {
            JsonNode fragment = JSON.readTree(blocks.group(1));
            if (fragment.isObject() && fragment.size() == 1 && fragment.has("hostile_variants")) {
                var merged = (com.fasterxml.jackson.databind.node.ObjectNode) JSON.readTree(SHIP.toFile());
                merged.set("hostile_variants", fragment.get("hostile_variants"));
                assertValid(schema, merged, document + " variant fragment");
                variantFragments++;
                continue;
            }
            assertValid(schema, fragment, document);
            assertEquals(JSON.readTree(SHIP.toFile()), fragment, document + " must use the unchanged example");
            count++;
        }
        assertEquals(1, count, "Both guides must contain the copyable ship definition");
        assertEquals(1, variantFragments, "Both guides must contain one validated variant fragment");
    }

    @Test
    void schemaRejectsEmptyAttacksAndPackSupportsBothSides() throws IOException {
        var invalid = JSON.readTree(SHIP.toFile()).deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode) invalid.get("behavior")).putArray("attacks");
        assertFalse(schema("ship_definition").validate(invalid).isEmpty(), "Empty attacks must be rejected");
        assertEquals(15, JSON.readTree(EXAMPLE.resolve("pack.mcmeta").toFile()).path("pack").path("pack_format").asInt());
        assertTrue(Files.isDirectory(EXAMPLE.resolve("data")) && Files.isDirectory(EXAMPLE.resolve("assets")));
    }

    private static JsonSchema schema(String kind) throws IOException {
        Path path = Path.of("docs/schemas/shincolle_kai-" + kind + ".schema.json");
        return JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(JSON.readTree(path.toFile()));
    }

    private static void assertValid(JsonSchema schema, JsonNode value, String source) {
        var errors = schema.validate(value);
        assertTrue(errors.isEmpty(), () -> source + ": " + errors);
    }
}
