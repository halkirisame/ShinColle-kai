package com.lulan.shincolle.gametest;

import com.lulan.shincolle.capability.CapaShipSavedValues;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.entity.destroyer.EntityDestroyerShimakaze;
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
 * Regression coverage: a saved ship's appearance-state bit that has
 * no visible effect on its own ship type must be cleared on load, not merely
 * hidden from the equipment grid.
 * <p>
 * Shimakaze's model state bit0 picks the summoned rensouhou-chan type
 * ({@code EntityDestroyerShimakaze#attackEntityWithAmmo}); the appearance grid
 * stopped offering that cell, but ships that had it set before still kept
 * summoning the deep-sea variant forever. {@code CapaShipSavedValues#loadNBTData}
 * now clears {@code BasicEntityShip#getHiddenAppearanceBits()} out of the loaded
 * {@code State} value after both the current and legacy load paths run.
 */
@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ShimakazeAppearanceBitClearGameTests {

    private ShimakazeAppearanceBitClearGameTests() {
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void loadNBTDataClearsHiddenBitFromCurrentFormat(GameTestHelper helper) {
        try (GameTestEntities entities = GameTestEntities.open(helper)) {
            Entity e = entities.add(ModEntities.DESTROYER_SHIMAKAZE.get().create(helper.getLevel()));
            if (!(e instanceof BasicEntityShip ship)) {
                throw new AssertionError("Failed to create ship");
            }

            CompoundTag nbt = new CompoundTag();
            int[] emotions = ship.getStateEmotionArray().clone();
            emotions[ID.S.State] = 0b111111;
            nbt.putIntArray("StateEmotion", emotions);

            CapaShipSavedValues.loadNBTData(nbt, ship);

            int state = ship.getStateEmotion(ID.S.State);
            if (state != 0b111110) {
                throw new AssertionError("Expected hidden bit0 cleared to 0b111110, got "
                        + Integer.toBinaryString(state));
            }
            helper.succeed();
        }
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void loadNBTDataClearsHiddenBitFromLegacyFormat(GameTestHelper helper) {
        try (GameTestEntities entities = GameTestEntities.open(helper)) {
            Entity e = entities.add(ModEntities.DESTROYER_SHIMAKAZE.get().create(helper.getLevel()));
            if (!(e instanceof BasicEntityShip ship)) {
                throw new AssertionError("Failed to create ship");
            }

            CompoundTag display = new CompoundTag();
            display.putInt("State", 63);
            CompoundTag shipExtProps = new CompoundTag();
            shipExtProps.put("Display", display);
            CompoundTag nbt = new CompoundTag();
            nbt.put("ShipExtProps", shipExtProps);

            CapaShipSavedValues.loadNBTData(nbt, ship);

            int state = ship.getStateEmotion(ID.S.State);
            if (state != 62) {
                throw new AssertionError("Expected legacy-loaded State cleared to 62, got " + state);
            }
            helper.succeed();
        }
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void loadNBTDataLeavesStateUntouchedForShipWithoutHiddenBits(GameTestHelper helper) {
        try (GameTestEntities entities = GameTestEntities.open(helper)) {
            Entity e = entities.add(ModEntities.DESTROYER_HIBIKI.get().create(helper.getLevel()));
            if (!(e instanceof BasicEntityShip ship)) {
                throw new AssertionError("Failed to create ship");
            }

            CompoundTag nbt = new CompoundTag();
            int[] emotions = ship.getStateEmotionArray().clone();
            emotions[ID.S.State] = 63;
            nbt.putIntArray("StateEmotion", emotions);

            CapaShipSavedValues.loadNBTData(nbt, ship);

            int state = ship.getStateEmotion(ID.S.State);
            if (state != 63) {
                throw new AssertionError("Ship with no hidden appearance bits must keep State=63, got " + state);
            }
            helper.succeed();
        }
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void getHiddenAppearanceBitsDefaultsToZeroAndShimakazeReturnsBit0(GameTestHelper helper) {
        try (GameTestEntities entities = GameTestEntities.open(helper)) {
            Entity shimakaze = entities.add(ModEntities.DESTROYER_SHIMAKAZE.get().create(helper.getLevel()));
            Entity hibiki = entities.add(ModEntities.DESTROYER_HIBIKI.get().create(helper.getLevel()));
            if (!(shimakaze instanceof EntityDestroyerShimakaze) || !(hibiki instanceof BasicEntityShip hibikiShip)) {
                throw new AssertionError("Failed to create ships");
            }

            int shimakazeBits = ((BasicEntityShip) shimakaze).getHiddenAppearanceBits();
            if (shimakazeBits != 1) {
                throw new AssertionError("Shimakaze getHiddenAppearanceBits() must be 1, got " + shimakazeBits);
            }

            int defaultBits = hibikiShip.getHiddenAppearanceBits();
            if (defaultBits != 0) {
                throw new AssertionError("BasicEntityShip default getHiddenAppearanceBits() must be 0, got "
                        + defaultBits);
            }
            helper.succeed();
        }
    }
}
