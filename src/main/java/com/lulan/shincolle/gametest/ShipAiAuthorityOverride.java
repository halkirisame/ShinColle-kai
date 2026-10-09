package com.lulan.shincolle.gametest;

import com.lulan.shincolle.handler.ConfigHandler;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;

import java.lang.reflect.Field;

/** Restores the effective ship AI authority after a scoped GameTest override. */
final class ShipAiAuthorityOverride implements AutoCloseable {
    private final ConfigHandler.ShipAiTargetAuthority previous;
    private boolean closed;

    private ShipAiAuthorityOverride(ConfigHandler.ShipAiTargetAuthority previous) {
        this.previous = previous;
    }

    static ShipAiAuthorityOverride use(ConfigHandler.ShipAiTargetAuthority authority) {
        ConfigHandler.ShipAiTargetAuthority previous = ConfigHandler.shipAiTargetAuthority();
        ConfigHandler.setShipAiTargetAuthorityForTest(authority);
        return new ShipAiAuthorityOverride(previous);
    }

    /** Holds an override for one isolated test, including deferred callbacks and timeout cleanup. */
    static void useUntilComplete(GameTestHelper helper, ConfigHandler.ShipAiTargetAuthority authority) {
        ShipAiAuthorityOverride override = use(authority);
        try {
            Field field = GameTestHelper.class.getDeclaredField("testInfo");
            field.setAccessible(true);
            GameTestInfo test = (GameTestInfo) field.get(helper);
            test.addListener(new GameTestListener() {
                @Override
                public void testStructureLoaded(GameTestInfo info) {
                }

                @Override
                public void testPassed(GameTestInfo info) {
                    override.close();
                }

                @Override
                public void testFailed(GameTestInfo info) {
                    override.close();
                }
            });
        } catch (ReflectiveOperationException error) {
            override.close();
            throw new IllegalStateException("Cannot attach authority restoration to test completion", error);
        }
    }

    @Override
    public void close() {
        if (!this.closed) {
            this.closed = true;
            ConfigHandler.setShipAiTargetAuthorityForTest(this.previous);
        }
    }
}
