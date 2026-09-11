package com.lulan.shincolle.utility;

import com.lulan.shincolle.handler.ConfigHandler;
import com.lulan.shincolle.reference.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LogHelper {
    private static final Logger LOGGER = LoggerFactory.getLogger(Reference.MOD_NAME);

    /**
     * Whether the "DIAG:" verification diagnostics should be emitted.
     * Guarded because these fire on hot paths - equipment recalculation and
     * every successful hit - and because the config may not be loaded yet
     * when an early diagnostic runs.
     */
    public static boolean diagEnabled() {
        try {
            return ConfigHandler.debugMode();
        } catch (RuntimeException configNotLoadedYet) {
            return false;
        }
    }

    /**
     * Logs a verification diagnostic, but only while debugMode is on.
     * Callers that build an expensive message should check {@link #diagEnabled()}
     * first so the message is never assembled when diagnostics are off.
     * <p>
     * These lines are parsed by the dev-only verification HUD, which decides pass
     * and fail from them. Adding, removing or reordering a field here breaks its
     * match patterns silently: the run still finishes and simply reports the wrong
     * verdict. Tell whoever maintains that tool when the format of a "DIAG:" line
     * changes, and say where the new field sits relative to the existing ones.
     */
    public static void diag(Object object) {
        if (diagEnabled()) {
            LOGGER.info(String.valueOf(object));
        }
    }

    public static void info(Object object) {
        LOGGER.info(String.valueOf(object));
    }

    public static void debug(Object object) {
        LOGGER.debug(String.valueOf(object));
    }

    public static void warn(Object object) {
        LOGGER.warn(String.valueOf(object));
    }

    public static void error(Object object) {
        LOGGER.error(String.valueOf(object));
    }

    public static void fatal(Object object) {
        LOGGER.error("[FATAL] {}", object);
    }

    public static void trace(Object object) {
        LOGGER.trace(String.valueOf(object));
    }
}
