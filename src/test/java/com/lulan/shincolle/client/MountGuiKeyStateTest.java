package com.lulan.shincolle.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MountGuiKeyStateTest {
    @Test
    void closePressMustNotReopenUntilReleasedAndPressedAgain() {
        MountGuiKeyState state = new MountGuiKeyState();
        assertTrue(state.sample(true, true));
        assertFalse(state.sample(false, false));
        state.pressedInScreen();
        assertFalse(state.sample(true, true), "Closing E is still the same physical press");
        assertFalse(state.sample(true, true));
        assertFalse(state.sample(false, true));
        assertTrue(state.sample(true, true), "A fresh press must open again");
    }

    @Test
    void holdingAcrossAScreenOrMountTransitionMustNotCountAsANewPress() {
        MountGuiKeyState state = new MountGuiKeyState();
        assertFalse(state.sample(true, false));
        assertFalse(state.sample(true, true));
        assertFalse(state.sample(false, true));
        assertTrue(state.sample(true, true));
        assertFalse(state.sample(true, false));
        assertFalse(state.sample(true, true));
    }

    @Test
    void modifiedBindingMustNotOpenWithTheMainKeyAlone() {
        MountGuiKeyState state = new MountGuiKeyState();
        assertFalse(state.sample(false, true, false));
        assertFalse(state.sample(true, true, false), "E alone does not activate Ctrl+E");
        assertFalse(state.sample(true, true, false));
    }

    @Test
    void modifiedBindingMustOpenWithTheCorrectCombination() {
        MountGuiKeyState state = new MountGuiKeyState();
        assertFalse(state.sample(false, true, false));
        assertTrue(state.sample(true, true, true));
        assertFalse(state.sample(true, true, true));
        assertFalse(state.sample(false, true, false));
        assertTrue(state.sample(true, true, true));
    }

    @Test
    void lateModifierMustFollowTheMappingActivationRatherThanPhysicalStateAlone() {
        MountGuiKeyState state = new MountGuiKeyState();
        assertFalse(state.sample(true, true, false), "E was pressed without Ctrl");
        assertFalse(state.sample(true, true, false), "Ctrl alone does not set the E mapping down");
        assertTrue(state.sample(true, true, true), "An accepted E repeat activates the mapping");
        assertFalse(state.sample(true, true, true));
    }

    @Test
    void releasingTheModifierFirstMustFollowTheMappingDownEdges() {
        MountGuiKeyState state = new MountGuiKeyState();
        assertFalse(state.sample(true, true, false), "Ctrl was released before the first sample");
        assertTrue(state.sample(true, true, true), "Ctrl reactivates an already-set mapping");
        assertFalse(state.sample(true, true, false), "Ctrl is released while E stays held");
        assertTrue(state.sample(true, true, true), "The legacy mapping has a new down edge");
    }

    @Test
    void closingPressMustStayBlockedAcrossModifierChangesUntilPhysicalRelease() {
        MountGuiKeyState state = new MountGuiKeyState();
        state.pressedInScreen();
        assertFalse(state.sample(true, true, false));
        assertFalse(state.sample(true, true, true));
        assertFalse(state.sample(true, true, false));
        assertFalse(state.sample(true, true, true));
        assertFalse(state.sample(false, true, false));
        assertTrue(state.sample(true, true, true));
    }

    @Test
    void inactiveConflictContextMustNotQualifyThePhysicalPress() {
        MountGuiKeyState state = new MountGuiKeyState();
        assertFalse(state.sample(true, true, false), "The mapping context is inactive");
        assertFalse(state.sample(true, true, false));
        assertFalse(state.sample(false, true, false));
        assertTrue(state.sample(true, true, true), "The mapping context is active again");
    }

    @Test
    void unqualifiedPressAcrossAScreenOrMountTransitionMustStayBlockedUntilRelease() {
        MountGuiKeyState state = new MountGuiKeyState();
        assertFalse(state.sample(true, false, false));
        assertFalse(state.sample(true, true, true));
        assertFalse(state.sample(true, true, false));
        assertFalse(state.sample(true, true, true));
        assertFalse(state.sample(false, true, false));
        assertTrue(state.sample(true, true, true));
    }
}
