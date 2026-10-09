package com.lulan.shincolle.gametest;

import com.lulan.shincolle.capability.CapaShipSavedValues;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Regression coverage: nothing in this port raises the crane state, and every goal
 * stands down while it is above zero. A saved value must not be restored on load,
 * or the ship stays frozen with no way to clear it.
 */
@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CraneStateLoadGameTests {

    private CraneStateLoadGameTests() {
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void loadNBTDataDropsSavedCraneStateFromCurrentFormat(GameTestHelper helper) {
        try (GameTestEntities entities = GameTestEntities.open(helper)) {
            Entity e = entities.add(ModEntities.DESTROYER_HIBIKI.get().create(helper.getLevel()));
            if (!(e instanceof BasicEntityShip ship)) {
                throw new AssertionError("Failed to create ship");
            }

            CompoundTag nbt = new CompoundTag();
            int[] minors = ship.getStateMinorArray().clone();
            minors[ID.M.CraneState] = 2;
            nbt.putIntArray("StateMinor", minors);

            CapaShipSavedValues.loadNBTData(nbt, ship);

            helper.assertTrue(ship.getStateMinor(ID.M.CraneState) == 0,
                    "Saved crane state must not survive load, got " + ship.getStateMinor(ID.M.CraneState));
            helper.succeed();
        }
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void loadNBTDataDropsSavedCraneStateFromLegacyFormat(GameTestHelper helper) {
        try (GameTestEntities entities = GameTestEntities.open(helper)) {
            Entity e = entities.add(ModEntities.DESTROYER_HIBIKI.get().create(helper.getLevel()));
            if (!(e instanceof BasicEntityShip ship)) {
                throw new AssertionError("Failed to create ship");
            }

            CompoundTag minor = new CompoundTag();
            minor.putInt("Crane", 1);
            CompoundTag shipExtProps = new CompoundTag();
            shipExtProps.put("Minor", minor);
            CompoundTag nbt = new CompoundTag();
            nbt.put("ShipExtProps", shipExtProps);

            CapaShipSavedValues.loadNBTData(nbt, ship);

            helper.assertTrue(ship.getStateMinor(ID.M.CraneState) == 0,
                    "Legacy crane state must not survive load, got " + ship.getStateMinor(ID.M.CraneState));
            helper.succeed();
        }
    }
}
