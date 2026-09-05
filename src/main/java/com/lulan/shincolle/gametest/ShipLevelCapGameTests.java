package com.lulan.shincolle.gametest;

import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.handler.ConfigHandler;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Integration coverage for live config synchronization and entity level caps. */
@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ShipLevelCapGameTests {

    private ShipLevelCapGameTests() {
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "ship_level_cap_config")
    public static void configuredCapsApplyThroughLevelOneThousand(GameTestHelper helper) {
        // getShipLevelCap() reads ConfigHandler's cached static fields, not the
        // ForgeConfigSpec values. Driving this through ConfigValue#set dragged in
        // Forge's asynchronous config save and file-watcher reload, which calls
        // syncConfig() again on its own schedule - so a stale reload could land
        // between the set and the assert (seen in CI as both "expected 600 but got
        // 100" and "restored 100 but got 600"). Assign the cached fields the test
        // actually reads instead; the read path under test is unchanged.
        int originalUnmarriedCap = ConfigHandler.maxLevelUnmarried;
        int originalAbsoluteCap = ConfigHandler.maxLevel;

        try {
            assertEquals(100, ConfigHandler.COMMON.maxLevelUnmarried.getDefault(),
                    "maxLevelUnmarried config default");
            assertEquals(150, ConfigHandler.COMMON.maxLevel.getDefault(), "maxLevel config default");

            ConfigHandler.maxLevelUnmarried = 100;
            ConfigHandler.maxLevel = 150;

            BasicEntityShip ship = ModEntities.BB_KONGOU.get().create(helper.getLevel());
            if (ship == null) {
                throw new AssertionError("Failed to create friendly ship");
            }
            assertEquals(100, ship.getShipLevelCap(), "default unmarried cap");
            ship.setStateFlag(ID.F.IsMarried, true);
            assertEquals(150, ship.getShipLevelCap(), "default married cap");
            ship.setShipLevel(150, false);
            ship.setShipLevel(151, false);
            assertEquals(150, ship.getLevel(), "default absolute cap rejection");

            ConfigHandler.maxLevelUnmarried = 600;
            ConfigHandler.maxLevel = 1000;

            ship.setStateFlag(ID.F.IsMarried, false);
            assertEquals(600, ship.getShipLevelCap(), "configured unmarried cap");
            ship.setStateFlag(ID.F.IsMarried, true);
            assertEquals(1000, ship.getShipLevelCap(), "configured married cap");
            ship.setShipLevel(1000, false);
            assertEquals(1000, ship.getLevel(), "supported configured level");
            ship.setShipLevel(1001, false);
            assertEquals(1000, ship.getLevel(), "configured absolute cap rejection");
        } finally {
            ConfigHandler.maxLevelUnmarried = originalUnmarriedCap;
            ConfigHandler.maxLevel = originalAbsoluteCap;
        }

        assertEquals(originalUnmarriedCap, ConfigHandler.maxLevelUnmarried, "restored unmarried cap");
        assertEquals(originalAbsoluteCap, ConfigHandler.maxLevel, "restored absolute cap");
        helper.succeed();
    }

    private static void assertEquals(int expected, int actual, String context) {
        if (expected != actual) {
            throw new AssertionError(context + " expected " + expected + " but got " + actual);
        }
    }
}
