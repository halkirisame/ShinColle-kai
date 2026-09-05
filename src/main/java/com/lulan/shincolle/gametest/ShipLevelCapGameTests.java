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
        int originalUnmarriedCap = ConfigHandler.COMMON.maxLevelUnmarried.get();
        int originalAbsoluteCap = ConfigHandler.COMMON.maxLevel.get();

        try {
            assertEquals(100, ConfigHandler.COMMON.maxLevelUnmarried.getDefault(),
                    "maxLevelUnmarried config default");
            assertEquals(150, ConfigHandler.COMMON.maxLevel.getDefault(), "maxLevel config default");

            ConfigHandler.COMMON.maxLevelUnmarried.set(100);
            ConfigHandler.COMMON.maxLevel.set(150);
            ConfigHandler.syncConfig();

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

            ConfigHandler.COMMON.maxLevelUnmarried.set(600);
            ConfigHandler.COMMON.maxLevel.set(1000);
            ConfigHandler.syncConfig();

            ship.setStateFlag(ID.F.IsMarried, false);
            assertEquals(600, ship.getShipLevelCap(), "configured unmarried cap");
            ship.setStateFlag(ID.F.IsMarried, true);
            assertEquals(1000, ship.getShipLevelCap(), "configured married cap");
            ship.setShipLevel(1000, false);
            assertEquals(1000, ship.getLevel(), "supported configured level");
            ship.setShipLevel(1001, false);
            assertEquals(1000, ship.getLevel(), "configured absolute cap rejection");
        } finally {
            ConfigHandler.COMMON.maxLevelUnmarried.set(originalUnmarriedCap);
            ConfigHandler.COMMON.maxLevel.set(originalAbsoluteCap);
            ConfigHandler.syncConfig();
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
