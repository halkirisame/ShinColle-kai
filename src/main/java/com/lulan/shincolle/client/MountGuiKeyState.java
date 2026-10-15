package com.lulan.shincolle.client;

/** Tracks one inventory press across gameplay and screen transitions. */
final class MountGuiKeyState {
    private boolean wasMappingDown;
    private boolean blockedUntilRelease;

    boolean sample(boolean down, boolean canOpen) {
        return sample(down, canOpen, down);
    }

    boolean sample(boolean down, boolean canOpen, boolean mappingDown) {
        if (!down) {
            blockedUntilRelease = false;
        } else if (!canOpen) {
            blockedUntilRelease = true;
        }

        boolean qualifiedDown = down && mappingDown;
        boolean pressed = qualifiedDown && !wasMappingDown;
        wasMappingDown = qualifiedDown;
        return pressed && canOpen && !blockedUntilRelease;
    }

    void pressedInScreen() {
        blockedUntilRelease = true;
    }
}
