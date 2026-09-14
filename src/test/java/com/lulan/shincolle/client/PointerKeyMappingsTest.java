package com.lulan.shincolle.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.client.settings.KeyModifier;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PointerKeyMappingsTest {
    private static List<KeyMapping> registeredMappings() {
        List<KeyMapping> registered = new ArrayList<>();
        PointerKeyMappings.register(new RegisterKeyMappingsEvent(null) {
            @Override
            public void register(KeyMapping mapping) {
                registered.add(mapping);
            }
        });
        return registered;
    }

    @Test
    void registersAllFiveHeldInputsWithLegacyDefaultsAndNoChordModifier() {
        Map<String, Integer> expected = Map.of(
                "formation_gui", GLFW.GLFW_KEY_LEFT_CONTROL,
                "guard_position", GLFW.GLFW_KEY_LEFT_SHIFT,
                "guard_entity", GLFW.GLFW_KEY_LEFT_CONTROL,
                "team_management", GLFW.GLFW_KEY_LEFT_SHIFT,
                "cycle_mode", GLFW.GLFW_KEY_LEFT_SHIFT);
        List<KeyMapping> registered = registeredMappings();
        assertEquals(5, registered.size());
        assertEquals(5, registered.stream().map(KeyMapping::getName).distinct().count());
        for (KeyMapping mapping : registered) {
            String name = mapping.getName().replace("key.shincolle_kai.pointer.", "");
            assertTrue(expected.containsKey(name), mapping.getName());
            assertEquals(InputConstants.Type.KEYSYM, mapping.getDefaultKey().getType());
            assertEquals(expected.get(name).intValue(), mapping.getDefaultKey().getValue());
            assertEquals(KeyModifier.NONE, mapping.getDefaultKeyModifier());
            assertEquals(KeyConflictContext.IN_GAME, mapping.getKeyConflictContext());
            assertEquals(PointerKeyMappings.CATEGORY, mapping.getCategory());
            assertTrue(mapping.isDefault());
        }
    }

    @Test
    void everyRegisteredLabelAndCategoryExistsInBothLanguages() throws IOException {
        for (String language : List.of("en_us", "ja_jp")) {
            Path path = Path.of("src/main/resources/assets/shincolle_kai/lang/" + language + ".json");
            JsonObject translations = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            for (KeyMapping mapping : registeredMappings()) {
                for (String key : List.of(mapping.getName(), mapping.getCategory())) {
                    assertTrue(translations.has(key), language + ": " + key);
                    assertFalse(translations.get(key).getAsString().isBlank());
                }
            }
        }
    }

    @Test
    void registrationIsRestrictedToTheClientModBus() throws IOException {
        List<String> values = new ArrayList<>();
        // Reading the annotation reflectively initializes Forge's Bus enum, which
        // needs a running FML container. Inspect metadata without booting Forge.
        try (var stream = PointerKeyMappings.class.getResourceAsStream("PointerKeyMappings.class")) {
            assertNotNull(stream);
            new ClassReader(stream).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                    if (!descriptor.equals("Lnet/minecraftforge/fml/common/Mod$EventBusSubscriber;")) {
                        return null;
                    }
                    return new AnnotationVisitor(Opcodes.ASM9) {
                        @Override
                        public void visitEnum(String name, String type, String value) {
                            values.add(type + ":" + value);
                        }

                        @Override
                        public AnnotationVisitor visitArray(String name) {
                            return this;
                        }
                    };
                }
            }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
        assertEquals(List.of("Lnet/minecraftforge/api/distmarker/Dist;:CLIENT",
                "Lnet/minecraftforge/fml/common/Mod$EventBusSubscriber$Bus;:MOD"), values);
    }
}
