package com.lulan.shincolle.client;

import com.lulan.shincolle.utility.PointerInputModifiers;
import com.lulan.shincolle.utility.PointerInputModifiers.Action;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.SharedConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.ToggleKeyMapping;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.Level;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWKeyCallbackI;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PointerGuardSprintInputTest {
    private static final int SCAN_CODE = 501;
    private static final int OTHER_SCAN_CODE = 502;
    private Minecraft previousClient;
    private Minecraft client;
    private TestPlayer player;
    private ToggleKeyMapping sprint;
    private List<KeyMapping> mappings;
    private KeyMapping guard;
    private boolean toggleSprint;

    @BeforeEach
    void setUp() throws Exception {
        SharedConstants.tryDetectVersion();
        Field bootstrapped = Bootstrap.class.getDeclaredField("isBootstrapped");
        bootstrapped.setAccessible(true);
        if (!bootstrapped.getBoolean(null)) {
            bootstrapped.setBoolean(null, true);
            BuiltInRegistries.bootStrap();
        }
        previousClient = Minecraft.getInstance();
        client = allocate(Minecraft.class);
        player = allocate(TestPlayer.class);
        player.level = allocate(TestLevel.class);
        client.player = player;
        client.setWindowActive(true);
        Field instance = Minecraft.class.getDeclaredField("instance");
        instance.setAccessible(true);
        instance.set(null, client);
        Field window = Minecraft.class.getDeclaredField("window");
        window.setAccessible(true);
        window.set(client, allocate(Window.class));
        Field options = Minecraft.class.getDeclaredField("options");
        options.setAccessible(true);
        options.set(client, allocate(Options.class));
        toggleSprint = true;
        sprint = new ToggleKeyMapping("test.guard.sprint", GLFW.GLFW_KEY_LEFT_CONTROL, "test", () -> toggleSprint);
        sprint.setKey(InputConstants.Type.SCANCODE.getOrCreate(SCAN_CODE));
        Field sprintField = Options.class.getDeclaredField("keySprint");
        sprintField.setAccessible(true);
        sprintField.set(client.options, sprint);
        mappings = new ArrayList<>();
        PointerKeyMappings.register(new RegisterKeyMappingsEvent(null) {
            @Override
            public void register(KeyMapping mapping) {
                mappings.add(mapping);
            }
        });
        guard = mappings.stream().filter(mapping -> mapping.getName().endsWith(".guard_entity")).findFirst().orElseThrow();
        mappings.forEach(mapping -> mapping.setKey(mapping.getDefaultKey()));
        release(SCAN_CODE);
        release(OTHER_SCAN_CODE);
    }

    @AfterEach
    void tearDown() throws Exception {
        release(SCAN_CODE);
        release(OTHER_SCAN_CODE);
        if (mappings != null) {
            mappings.forEach(mapping -> mapping.setKey(mapping.getDefaultKey()));
        }
        Field instance = Minecraft.class.getDeclaredField("instance");
        instance.setAccessible(true);
        instance.set(null, previousClient);
    }

    @Test
    void stoppedToggleSprintUsesPressAndReleaseInsteadOfLatch() {
        assertFalse(guardDown());
        press(SCAN_CODE);
        assertTrue(sprint.isDown());
        assertTrue(guardDown());
        release(SCAN_CODE);
        assertTrue(sprint.isDown(), "Vanilla toggle latch survives physical release");
        assertFalse(guardDown(), "Stopped and physically released must allow normal right click");
        press(SCAN_CODE);
        assertFalse(sprint.isDown(), "Second press toggles the vanilla latch off");
        assertTrue(guardDown(), "Physical press must still enable guard with the latch off");
        release(SCAN_CODE);
        assertFalse(guardDown());
    }

    @Test
    void holdSprintUsesPhysicalInputWithoutRequiringToggleMode() {
        toggleSprint = false;
        press(SCAN_CODE);
        assertTrue(sprint.isDown());
        assertTrue(guardDown());
        release(SCAN_CODE);
        assertFalse(sprint.isDown());
        assertFalse(guardDown());
        player.sprinting = true;
        assertTrue(guardDown());
    }

    @Test
    void realSprintingRemainsEffectiveAfterReleaseWithEitherLatchState() {
        for (boolean latch : List.of(false, true)) {
            if (sprint.isDown() != latch) {
                press(SCAN_CODE);
            }
            release(SCAN_CODE);
            player.sprinting = true;
            assertTrue(guardDown());
            player.sprinting = false;
            assertFalse(guardDown(), "Stopping must expose the released physical input, not the latch");
        }
    }

    @Test
    void rebindingVanillaSprintReadsTheCurrentScanCodeAndUnboundStillAllowsRealSprinting() {
        press(SCAN_CODE);
        assertTrue(guardDown());
        sprint.setKey(InputConstants.Type.SCANCODE.getOrCreate(OTHER_SCAN_CODE));
        assertFalse(guardDown(), "The previous physical key is no longer the sprint binding");
        press(OTHER_SCAN_CODE);
        assertTrue(guardDown());
        release(OTHER_SCAN_CODE);
        assertFalse(guardDown());
        sprint.setKey(InputConstants.UNKNOWN);
        assertFalse(guardDown());
        player.sprinting = true;
        assertTrue(guardDown());
    }

    @Test
    void unfocusedWindowRejectsGuardAndUnknownScanCodeCannotStickAfterRefocus() {
        press(SCAN_CODE);
        player.sprinting = true;
        client.setWindowActive(false);
        SprintKeyInput.onFocusChanged(false);
        assertFalse(guardDown());
        // GLFW does not synthesize releases for GLFW_KEY_UNKNOWN.
        client.setWindowActive(true);
        SprintKeyInput.onFocusChanged(true);
        player.sprinting = false;
        assertFalse(guardDown());
    }

    @Test
    void consumedKeyboardReleaseClearsPhysicalInputBeforeForwardingWithoutClearingVanillaLatch() throws Exception {
        press(SCAN_CODE);
        Field previousCallback = SprintKeyInput.class.getDeclaredField("previousKeyCallback");
        previousCallback.setAccessible(true);
        boolean[] forwarded = {false};
        previousCallback.set(null, (GLFWKeyCallbackI) (window, key, scan, action, modifiers) -> {
            assertEquals(17L, window);
            assertEquals(GLFW.GLFW_KEY_UNKNOWN, key);
            assertEquals(SCAN_CODE, scan);
            assertEquals(GLFW.GLFW_RELEASE, action);
            assertEquals(GLFW.GLFW_MOD_ALT, modifiers);
            assertFalse(guardDown(), "Record release even if the original callback consumes it");
            forwarded[0] = true;
        });
        try {
            SprintKeyInput.onKey(17L, GLFW.GLFW_KEY_UNKNOWN, SCAN_CODE, GLFW.GLFW_RELEASE, GLFW.GLFW_MOD_ALT);
        } finally {
            previousCallback.set(null, null);
        }
        assertTrue(forwarded[0]);
        assertTrue(sprint.isDown());
        assertFalse(guardDown());
        press(SCAN_CODE);
        assertTrue(guardDown());
        release(SCAN_CODE);
        assertFalse(guardDown());
    }

    @Test
    void customizedGuardAndOtherDefaultActionsKeepTheirExistingReaders() {
        guard.setKey(InputConstants.Type.SCANCODE.getOrCreate(OTHER_SCAN_CODE));
        guard.setDown(false);
        press(SCAN_CODE);
        player.sprinting = true;
        assertFalse(guardDown());
        guard.setDown(true);
        assertTrue(guardDown(), "Customized SCANCODE guard keeps the existing mapping reader");
        guard.setKey(InputConstants.UNKNOWN);
        assertFalse(guardDown());
        // Default actions other than guard still follow the player's sneak state.
        for (Action action : Action.values()) {
            if (action != Action.GUARD_ENTITY) {
                assertFalse(readMapping(action), action.name());
                player.sneaking = true;
                assertTrue(readMapping(action), action.name());
                player.sneaking = false;
            }
        }
    }

    @Test
    void physicalPollingUsesAssignedKeyboardOrMouseInputWithoutReadingToggleLatch() {
        for (InputConstants.Type type : List.of(InputConstants.Type.KEYSYM, InputConstants.Type.MOUSE)) {
            int value = type == InputConstants.Type.KEYSYM ? GLFW.GLFW_KEY_R : GLFW.GLFW_MOUSE_BUTTON_4;
            sprint.setKey(type.getOrCreate(value));
            for (boolean latch : List.of(false, true)) {
                if (sprint.isDown() != latch) {
                    sprint.setDown(true);
                }
                for (boolean pressed : List.of(false, true)) {
                    assertEquals(pressed, SprintKeyInput.isDown(sprint,
                            key -> type == InputConstants.Type.KEYSYM && key == value && pressed,
                            button -> type == InputConstants.Type.MOUSE && button == value && pressed));
                }
            }
        }
    }

    @Test
    void scanCodeRepeatDoesNotRestoreReleasedInputAndScreenGateRemainsEffective() throws Exception {
        press(SCAN_CODE);
        SprintKeyInput.onFocusChanged(false);
        SprintKeyInput.onKey(0L, GLFW.GLFW_KEY_UNKNOWN, SCAN_CODE, GLFW.GLFW_REPEAT, 0);
        assertFalse(guardDown());
        press(SCAN_CODE);
        assertTrue(guardDown());
        client.screen = allocate(TitleScreen.class);
        assertFalse(guardDown());
        client.screen = null;
        assertTrue(guardDown());
    }

    private boolean guardDown() {
        return readMapping(Action.GUARD_ENTITY);
    }

    private boolean readMapping(Action action) {
        return PointerInputModifiers.isDown(action, player);
    }

    private void press(int scanCode) {
        sprint.setDown(true);
        SprintKeyInput.onKey(0L, GLFW.GLFW_KEY_UNKNOWN, scanCode, GLFW.GLFW_PRESS, 0);
    }

    private void release(int scanCode) {
        if (sprint != null) {
            sprint.setDown(false);
        }
        SprintKeyInput.onKey(0L, GLFW.GLFW_KEY_UNKNOWN, scanCode, GLFW.GLFW_RELEASE, 0);
    }

    private static <T> T allocate(Class<T> type) throws Exception {
        Field field = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return type.cast(((sun.misc.Unsafe) field.get(null)).allocateInstance(type));
    }

    private static final class TestLevel extends ClientLevel {
        private TestLevel() {
            super(null, null, null, null, 0, 0, null, null, false, 0L);
        }

        @Override
        public boolean isClientSide() {
            return true;
        }
    }

    private static final class TestPlayer extends LocalPlayer {
        private boolean sprinting;
        private boolean sneaking;
        private Level level;

        private TestPlayer() {
            super(null, null, null, null, null, false, false);
        }

        @Override
        public Level level() {
            return level;
        }

        @Override
        public boolean isSprinting() {
            return sprinting;
        }

        @Override
        public boolean isShiftKeyDown() {
            return sneaking;
        }
    }
}
