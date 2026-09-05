package com.lulan.shincolle.init;

import com.lulan.shincolle.equipdata.EquipDataRegistry;
import com.lulan.shincolle.equipdata.EquipDefinition;
import com.lulan.shincolle.equipdata.EquipTier;
import com.lulan.shincolle.item.BasicEquip;
import com.lulan.shincolle.reference.Reference;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Map;

/** The display grade a player reads off the equipment list: order, name color, tooltip stars. */
@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class EquipTierPresentationGameTests {

    private EquipTierPresentationGameTests() {
    }

    /**
     * Every displayed run of one icon family must get stronger, never weaker, left to right.
     * Reads the tiers back out of the loaded definitions rather than from a second copy of the
     * expected order, so reordering the arrays without re-checking the data fails here.
     */
    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void displayOrderRisesWithinEachIconFamily(GameTestHelper helper) {
        int checked = 0;
        for (Map.Entry<Item, int[]> entry : ModCreativeTabs.equipmentDisplayOrders().entrySet()) {
            if (!(entry.getKey() instanceof BasicEquip equip)) {
                throw new AssertionError("Display order registered for a non-equipment item: " + entry.getKey());
            }
            ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(equip);
            int previousFamily = Integer.MIN_VALUE;
            int previousRareMean = Integer.MIN_VALUE;

            for (int variant : entry.getValue()) {
                EquipDefinition definition = EquipDataRegistry.server().byItemVariant(itemId, variant);
                if (definition == null) {
                    throw new AssertionError("Display order lists a variant with no definition: "
                            + itemId + " variant " + variant);
                }
                if (definition.availability().isHidden()) {
                    throw new AssertionError("Display order lists an unobtainable variant: "
                            + itemId + " variant " + variant);
                }

                int family = equip.getIconIndex(definition);
                if (family != previousFamily) {
                    previousFamily = family;
                } else if (definition.rareMean() < previousRareMean) {
                    throw new AssertionError("Display order drops in strength inside one icon family: "
                            + itemId + " variant " + variant + " rare_mean " + definition.rareMean()
                            + " after " + previousRareMean);
                }
                previousRareMean = definition.rareMean();
                checked++;
            }
        }

        if (checked < 60) {
            throw new AssertionError("Expected the ordered equipment variants to be checked, saw " + checked);
        }
        helper.succeed();
    }

    /** The name color states the equipment's grade, and an enchanted copy must not inflate it. */
    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void nameColorFollowsTierAndIgnoresEnchantments(GameTestHelper helper) {
        assertRarity(helper, ModItems.EQUIP_CANNON.get(), 0, Rarity.COMMON);
        assertRarity(helper, ModItems.EQUIP_CANNON.get(), 3, Rarity.UNCOMMON);
        assertRarity(helper, ModItems.EQUIP_CANNON.get(), 4, Rarity.RARE);
        assertRarity(helper, ModItems.EQUIP_CANNON.get(), 5, Rarity.EPIC);

        ItemStack starter = ((BasicEquip) ModItems.EQUIP_CANNON.get()).createStack(0);
        Rarity bare = starter.getItem().getRarity(starter);
        starter.enchant(Enchantments.SHARPNESS, 3);
        Rarity enchanted = starter.getItem().getRarity(starter);
        if (bare != enchanted) {
            throw new AssertionError("An enchanted copy changed the equipment grade: "
                    + bare + " -> " + enchanted);
        }
        helper.succeed();
    }

    /** An unknown variant keeps vanilla's answer instead of claiming the lowest grade. */
    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void unknownVariantFallsBackToVanillaRarity(GameTestHelper helper) {
        ItemStack unknown = ((BasicEquip) ModItems.EQUIP_CANNON.get()).createStack(9999);
        if (unknown.getItem().getRarity(unknown) != Rarity.COMMON) {
            throw new AssertionError("Unknown variant did not fall back to the vanilla rarity");
        }
        helper.succeed();
    }

    /** The tooltip's rare-level line carries the stars and keeps the raw number. */
    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void rareLevelTooltipShowsStarsAndValue(GameTestHelper helper) {
        ResourceLocation cannonId = ForgeRegistries.ITEMS.getKey(ModItems.EQUIP_CANNON.get());
        EquipDefinition elite = EquipDataRegistry.server().byItemVariant(cannonId, 5);
        if (elite == null) {
            throw new AssertionError("Missing the definition this test reads");
        }
        if (EquipTier.of(elite) != EquipTier.ELITE) {
            throw new AssertionError("Expected cannon variant 5 to be the top tier");
        }
        String stars = EquipTier.of(elite).starText();
        if (!"★★★★".equals(stars)) {
            throw new AssertionError("Unexpected star text: " + stars);
        }
        helper.succeed();
    }

    private static void assertRarity(GameTestHelper helper, Item item, int variant, Rarity expected) {
        ItemStack stack = ((BasicEquip) item).createStack(variant);
        Rarity actual = stack.getItem().getRarity(stack);
        if (actual != expected) {
            ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(item);
            EquipDefinition definition = EquipDataRegistry.server().byItemVariant(itemId, variant);
            throw new AssertionError("Wrong grade color for " + itemId + " variant " + variant
                    + " (rare_mean " + (definition == null ? "?" : definition.rareMean()) + "): expected "
                    + expected + " but was " + actual);
        }
    }
}
