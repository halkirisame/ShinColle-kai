package com.lulan.shincolle.utility;

import net.minecraft.world.entity.player.Player;

import java.util.function.BiPredicate;

/** Common-side boundary: only action names and booleans cross from the client. */
public final class PointerInputModifiers {
    public enum Action {
        FORMATION_GUI, GUARD_POSITION, GUARD_ENTITY, TEAM_MANAGEMENT, CYCLE_MODE
    }

    private static BiPredicate<Action, Player> reader = PointerInputModifiers::legacyState;

    private PointerInputModifiers() {
    }

    /** Installed by the client-only key registration event, never by common initialization. */
    public static void installClientReader(BiPredicate<Action, Player> clientReader) {
        reader = clientReader;
    }

    public static boolean isDown(Action action, Player player) {
        // Server-side test callers retain the old state-based command resolution.
        return player.level().isClientSide() ? reader.test(action, player) : legacyState(action, player);
    }

    public static boolean legacyState(Action action, Player player) {
        return switch (action) {
            case GUARD_POSITION, TEAM_MANAGEMENT, CYCLE_MODE -> player.isShiftKeyDown();
            case GUARD_ENTITY -> player.isSprinting();
            case FORMATION_GUI -> false;
        };
    }
}
