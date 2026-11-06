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
 * A saved ship must not resume a crane session without its live crane capturing it again.
 * Both the waiting state and elapsed work time are cleared when loading either save format.
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
            ship.setStateTimer(ID.T.CraneTime, 60000);

            CapaShipSavedValues.loadNBTData(nbt, ship);

            helper.assertTrue(ship.getStateMinor(ID.M.CraneState) == 0,
                    "Saved crane state must not survive load, got " + ship.getStateMinor(ID.M.CraneState));
            helper.assertTrue(ship.getStateTimer(ID.T.CraneTime) == 0,
                    "Loading current data must also reset transient crane time.");
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
            CompoundTag timer = new CompoundTag();
            timer.putInt("Crane", 60000);
            shipExtProps.put("Timer", timer);
            CompoundTag nbt = new CompoundTag();
            nbt.put("ShipExtProps", shipExtProps);

            CapaShipSavedValues.loadNBTData(nbt, ship);

            helper.assertTrue(ship.getStateMinor(ID.M.CraneState) == 0,
                    "Legacy crane state must not survive load, got " + ship.getStateMinor(ID.M.CraneState));
            helper.assertTrue(ship.getStateTimer(ID.T.CraneTime) == 0,
                    "Migrating legacy data must not restore its old crane time.");
            helper.succeed();
        }
    }
}
