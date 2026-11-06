package com.lulan.shincolle.gametest;

import com.lulan.shincolle.handler.ConfigHandler;
import com.lulan.shincolle.init.ModBlocks;
import com.lulan.shincolle.reference.Reference;
import com.lulan.shincolle.tileentity.TileMultiGrudgeHeavy;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HeavyGrudgeRecycleGameTests {
    private HeavyGrudgeRecycleGameTests() { }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void heavyGrudgeReturnsStoredMaterialsWithoutMultiplyingThem(GameTestHelper helper) {
        boolean easy = ConfigHandler.easyMode;
        try {
            for (boolean mode : new boolean[] {false, true}) {
                ConfigHandler.easyMode = mode;
                helper.assertTrue(ConfigHandler.easyMode() == mode, "Easy-mode fixture must take effect.");
                TileMultiGrudgeHeavy tile = recycle(helper, new int[] {0, 100, 0, 0}, 0);
                helper.assertTrue(tile.getMatStock(0) == (mode ? 810 : 81) && tile.getMatStock(1) == 100,
                        "Stored metal must be returned once; only the base grudge is multiplied.");
                helper.assertTrue(tile.getInventory().getStackInSlot(1).isEmpty(), "Successful recycling consumes one core.");
            }
        } finally {
            ConfigHandler.easyMode = easy;
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void heavyGrudgeRejectsOverflowAndSanitizesStoredMaterials(GameTestHelper helper) {
        boolean easy = ConfigHandler.easyMode;
        try {
            ConfigHandler.easyMode = false;
            TileMultiGrudgeHeavy full = recycle(helper, new int[] {0, 100, 0, 0}, 999950);
            helper.assertTrue(full.getMatStock(0) == 0 && full.getMatStock(1) == 999950
                            && full.getInventory().getStackInSlot(1).getCount() == 1,
                    "A full stock must reject the whole core without consuming it.");
            int[] original = {-3, Integer.MAX_VALUE, 2, 3};
            TileMultiGrudgeHeavy rejected = recycle(helper, original, 999950);
            helper.assertTrue(java.util.Arrays.equals(rejected.getInventory().getStackInSlot(1).getTag().getIntArray("Mats"), original),
                    "Reading a rejected input must not mutate its stored NBT array.");
            TileMultiGrudgeHeavy clamped = recycle(helper, new int[] {-3, Integer.MAX_VALUE, 2, 3}, 0);
            helper.assertTrue(clamped.getMatStock(0) == 81 && clamped.getMatStock(1) == 1000000
                            && clamped.getMatStock(2) == 2 && clamped.getMatStock(3) == 3,
                    "Stored values must use placement clamping.");
            for (int[] malformed : new int[][] {{1, 2}, {1, 2, 3, 4, 5}, {}}) {
                TileMultiGrudgeHeavy tile = recycle(helper, malformed, 0);
                helper.assertTrue(tile.getMatStock(0) == 81 && tile.getMatStock(1) == 0,
                        "Malformed arrays must contribute no stored resources.");
            }
        } finally {
            ConfigHandler.easyMode = easy;
        }
        helper.succeed();
    }

    private static TileMultiGrudgeHeavy recycle(GameTestHelper helper, int[] mats, int metal) {
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        TileMultiGrudgeHeavy tile = new TileMultiGrudgeHeavy(pos, ModBlocks.GRUDGE_HEAVY.get().defaultBlockState());
        tile.setLevel(helper.getLevel());
        tile.setMatStock(1, metal);
        ItemStack stack = new ItemStack(ModBlocks.GRUDGE_HEAVY.get());
        stack.getOrCreateTag().putIntArray("Mats", mats);
        tile.getInventory().setStackInSlot(1, stack);
        TileMultiGrudgeHeavy.serverTick(helper.getLevel(), pos, tile.getBlockState(), tile);
        return tile;
    }
}
