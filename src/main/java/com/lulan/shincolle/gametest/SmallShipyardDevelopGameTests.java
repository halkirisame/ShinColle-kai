package com.lulan.shincolle.gametest;

import com.lulan.shincolle.crafting.SmallRecipes;
import com.lulan.shincolle.init.ModItems;
import com.lulan.shincolle.item.BasicEquip;
import com.lulan.shincolle.reference.Reference;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.RandomSource;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SmallShipyardDevelopGameTests {
    private SmallShipyardDevelopGameTests() { }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void equipmentMissReturnsLightAndHeavyAmmoContainers(GameTestHelper helper) {
        boolean light = false;
        boolean heavy = false;
        for (int seed = 0; seed < 200; seed++) {
            var result = SmallRecipes.calculateResult(16, 16, 16, 16, false, RandomSource.create(seed));
            if (result.getItem() instanceof BasicEquip) continue;
            if (result.is(ModItems.AMMO_1.get())) {
                light = true;
                helper.assertTrue(result.getCount() >= 11 && result.getCount() <= 21,
                        "Light ammo containers must number 11 to 21");
            } else {
                helper.assertTrue(result.is(ModItems.AMMO_3.get())
                                && result.getCount() >= 2 && result.getCount() <= 3,
                        "Every other miss must return 2 to 3 heavy ammo containers, not single ammo: " + result);
                heavy = true;
            }
        }
        helper.assertTrue(light && heavy, "Fixed seeds must exercise both ammo-container branches");
        helper.succeed();
    }
}
