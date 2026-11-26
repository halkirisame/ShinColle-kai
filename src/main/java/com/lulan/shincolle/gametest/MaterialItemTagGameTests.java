package com.lulan.shincolle.gametest;

import com.lulan.shincolle.init.ModItems;
import com.lulan.shincolle.reference.Reference;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MaterialItemTagGameTests {
    private MaterialItemTagGameTests() { }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void materialItemsHaveForgeMaterialAndParentTags(GameTestHelper helper) {
        assertTagged(helper, ModItems.ABYSS_METAL.get(), "ingots", "abyssium");
        assertTagged(helper, ModItems.ABYSS_NUGGET.get(), "nuggets", "abyssium");
        assertTagged(helper, ModItems.ABYSSIUM_BLOCK_ITEM.get(), "storage_blocks", "abyssium");
        assertTagged(helper, ModItems.POLYMETAL_NODULE.get(), "ingots", "polymetal");
        assertTagged(helper, ModItems.ABYSS_NUGGET_1.get(), "nuggets", "polymetal");
        assertTagged(helper, ModItems.POLYMETAL_BLOCK_ITEM.get(), "storage_blocks", "polymetal");
        assertTagged(helper, ModItems.POLYMETAL_ORE_BLOCK_ITEM.get(), "ores", "polymetal");
        assertTagged(helper, ModItems.GRUDGE_BLOCK_ITEM.get(), "storage_blocks", "grudge");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void nonMaterialItemsDoNotReceiveForgeTags(GameTestHelper helper) {
        for (Item item : new Item[]{ModItems.GRUDGE.get(), ModItems.GRUDGE_HEAVY_BLOCK_ITEM.get(),
                ModItems.COMBAT_RATION.get()}) {
            helper.assertTrue(new ItemStack(item).getTags()
                            .noneMatch(tag -> tag.location().getNamespace().equals("forge")),
                    "Non-material item must not receive a Forge material tag: " + item);
        }
        helper.succeed();
    }

    private static void assertTagged(GameTestHelper helper, Item item, String parent, String material) {
        var stack = new ItemStack(item);
        for (String path : new String[]{parent, parent + "/" + material}) {
            helper.assertTrue(stack.is(ItemTags.create(new ResourceLocation("forge", path))),
                    "Material item must belong to forge:" + path + ": " + item);
        }
    }
}
