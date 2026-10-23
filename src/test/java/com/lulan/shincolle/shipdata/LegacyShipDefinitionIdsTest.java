package com.lulan.shincolle.shipdata;

import com.lulan.shincolle.reference.ID;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyShipDefinitionIdsTest {
    private static final Path JAVA = Path.of("src/main/java/com/lulan/shincolle");

    @Test
    void allShipEntityRegistrationsHaveExactlyOneMatchingClassAndRegistrationKey() throws Exception {
        Map<String, String> sources = entitySources();
        String registrations = Files.readString(JAVA.resolve("init/ModEntities.java"));
        Matcher registered = Pattern.compile("RegistryObject<EntityType<(\\w+)>>\\s+(\\w+)\\s*=\\s*ENTITIES\\s*"
                + "\\.register\\(\\s*\"([^\"]+)\"").matcher(registrations);
        Map<LegacyShipDefinitionIds.Key, ShipDefinitionId> expected = new HashMap<>();
        int friendly = 0;
        int hostile = 0;
        Set<String> withoutClass = new HashSet<>();
        while (registered.find()) {
            String entityClass = registered.group(1);
            if (Set.of("GenericShipEntity", "GenericHostileShipEntity").contains(entityClass)) {
                assertTrue(sources.get(entityClass).contains("implements DefinitionBackedShip"));
                continue;
            }
            LegacyShipDefinitionIds.Registration family = registration(entityClass, sources, new HashSet<>());
            if (family == null) {
                continue;
            }
            Matcher shipClass = Pattern.compile("setStateMinor\\(ID\\.M\\.ShipClass, ID\\.ShipClass\\.(\\w+)\\)")
                    .matcher(sources.get(entityClass));
            if (!shipClass.find()) {
                withoutClass.add(registered.group(3));
                continue;
            }
            int number = ID.ShipClass.class.getField(shipClass.group(1)).getInt(null);
            assertTrue(!shipClass.find(), entityClass + " assigns ShipClass more than once");
            LegacyShipDefinitionIds.Key key = new LegacyShipDefinitionIds.Key(number, family);
            ShipDefinitionId id = ShipDefinitionId.parse("shincolle_kai:" + registered.group(3));
            assertNull(expected.put(key, id), "duplicate registered ship key " + key);
            assertEquals(id, LegacyShipDefinitionIds.get(number, family), entityClass);
            assertEquals(family == LegacyShipDefinitionIds.Registration.HOSTILE, registered.group(2).endsWith("_MOB"));
            if (family == LegacyShipDefinitionIds.Registration.FRIENDLY) {
                friendly++;
            } else {
                hostile++;
            }
        }
        assertEquals(Set.of(), withoutClass, "ship registrations without their own ShipClass assignment");
        assertEquals(45, friendly);
        assertEquals(19, hostile);
        assertEquals(expected, LegacyShipDefinitionIds.all());
        assertEquals(64, new HashSet<>(expected.values()).size());
        assertThrows(UnsupportedOperationException.class, () -> LegacyShipDefinitionIds.all().clear());
        assertNull(LegacyShipDefinitionIds.get(9999, LegacyShipDefinitionIds.Registration.FRIENDLY));
    }

    /** Walks each registered class's complete local ancestry, rather than treating a name prefix as a ship. */
    private static LegacyShipDefinitionIds.Registration registration(String name, Map<String, String> sources, Set<String> seen) {
        assertTrue(seen.add(name), "cyclic entity ancestry " + name);
        if (name.equals("BasicEntityShip")) {
            return LegacyShipDefinitionIds.Registration.FRIENDLY;
        }
        if (name.equals("BasicEntityShipHostile")) {
            return LegacyShipDefinitionIds.Registration.HOSTILE;
        }
        String source = sources.get(name);
        if (source == null) {
            return null;
        }
        Matcher parent = Pattern.compile("class\\s+" + name + "\\s+extends\\s+(\\w+)").matcher(source);
        assertTrue(parent.find(), "unrecognized entity declaration " + name);
        return registration(parent.group(1), sources, seen);
    }

    private static Map<String, String> entitySources() throws IOException {
        Map<String, String> result = new HashMap<>();
        try (var paths = Files.walk(JAVA.resolve("entity"))) {
            for (Path path : paths.filter(p -> p.toString().endsWith(".java")).toList()) {
                String name = path.getFileName().toString().replace(".java", "");
                result.put(name, Files.readString(path));
            }
        }
        assertNotNull(result.get("BasicEntityShip"));
        return result;
    }

    @Test
    void eachPublishedLimitIsTwiceTheMeasuredMaximumOfItsBuiltInColumn() throws IOException {
        String table = Files.readString(JAVA.resolve("reference/Values.java"));
        table = table.substring(0, table.indexOf("HostileShipAttrMap"));
        table = table.replaceAll("//[^\\r\\n]*", "");
        Matcher rows = Pattern.compile("put\\(\\(int\\) ID\\.ShipClass\\.(\\w+),\\s*new float\\[\\]\\s*\\{([^}]+)}", Pattern.DOTALL)
                .matcher(table);
        double[] maxima = new double[12];
        int count = 0;
        while (rows.find()) {
            if (rows.group(1).startsWith("Player")) {
                continue;
            }
            String[] columns = rows.group(2).split(",");
            assertEquals(12, columns.length);
            for (int i = 0; i < 12; i++) {
                maxima[i] = Math.max(maxima[i], Double.parseDouble(columns[i].trim().replaceAll("[Ff]$", "")));
            }
            count++;
        }
        assertEquals(45, count);
        String[] keys = {"hp", "atk", "def", "spd", "mov", "hit"};
        for (int i = 0; i < keys.length; i++) {
            assertEquals(2 * maxima[i], ShipDefinitionFormat.BASE_MAX.get(keys[i]), 0.0000001, keys[i]);
            assertEquals(2 * maxima[i + 6], ShipDefinitionFormat.GROWTH_MAX.get(keys[i]), 0.0000001, keys[i]);
        }
    }
}
