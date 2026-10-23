package com.lulan.shincolle.gametest;

import com.lulan.shincolle.crafting.InventoryCraftingFake;
import com.lulan.shincolle.reference.Reference;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.Optional;

/**
 * The facility and tool recipes use the cost and layout of the original mod. Every case goes through
 * the real crafting lookup, so the recipe data is exercised as the player meets it.
 */
@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FacilityRecipeParityGameTests {

    private static final Item[] WOOLS = {
            Items.WHITE_WOOL, Items.ORANGE_WOOL, Items.MAGENTA_WOOL, Items.LIGHT_BLUE_WOOL,
            Items.YELLOW_WOOL, Items.LIME_WOOL, Items.PINK_WOOL, Items.GRAY_WOOL,
            Items.LIGHT_GRAY_WOOL, Items.CYAN_WOOL, Items.PURPLE_WOOL, Items.BLUE_WOOL,
            Items.BROWN_WOOL, Items.GREEN_WOOL, Items.RED_WOOL, Items.BLACK_WOOL};

    private FacilityRecipeParityGameTests() {
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void facilitiesAndToolsMatchOriginalSevenLayouts(GameTestHelper helper) {
        // Original layouts. R radar, B book, W white wool, O obsidian, G grudge, L lava bucket,
        // A abyssium, S stick, M magma block, K grudge block, P piston.
        assertCrafts(helper, "desk", "desk", "RBW/OOO/O O", "desk");
        assertCrafts(helper, "small_shipyard", "small_shipyard", "GLG/LOL/OOO", "small_shipyard");
        ItemStack hammer = assertCrafts(helper, "kaitai_hammer", "kaitai_hammer", "AAA/AAA/ S ", "kaitai_hammer");
        helper.assertTrue(hammer.getDamageValue() == 0 && !hammer.isDamaged(), "a new hammer is undamaged: " + hammer);
        assertCrafts(helper, "target_wrench", "target_wrench", "A A/AAA/ A ", "target_wrench");
        assertCrafts(helper, "vol_block", "vol_block", "MOM/OKO/MOM", "vol_block");
        assertCrafts(helper, "vol_block_alt", "vol_block", "OMO/MKM/OMO", "vol_block");
        assertCrafts(helper, "crane", "crane", "AAA/AKA/APA", "crane");

        // One ingredient short or one more than the original asks for does not craft.
        assertNotCrafts(helper, "RBW/OOO/O  ", "desk");
        assertNotCrafts(helper, "GLG/LOL/OO ", "small_shipyard");
        assertNotCrafts(helper, "AAA/AAA/   ", "kaitai_hammer");
        assertNotCrafts(helper, "A A/AAA/   ", "target_wrench");
        assertNotCrafts(helper, "MOM/OKO/MO ", "vol_block");
        assertNotCrafts(helper, "AAA/AKA/AP ", "crane");

        // The shrunk and misplaced layouts the recipes used to have.
        assertNotCrafts(helper, "RB /WW /OO ", "desk");
        assertNotCrafts(helper, "G  /LO /OO ", "small_shipyard");
        assertNotCrafts(helper, "AA /AA /AS ", "kaitai_hammer");
        assertNotCrafts(helper, " A /A A/ A ", "target_wrench");
        assertNotCrafts(helper, "MKM/KOK/MKM", "vol_block");
        assertNotCrafts(helper, "OKO/KMK/OKO", "vol_block");
        assertNotCrafts(helper, "AAA/APK/AAA", "crane");

        assertDeskAcceptsEveryWoolColorOnly(helper);
        helper.succeed();
    }

    private static void assertDeskAcceptsEveryWoolColorOnly(GameTestHelper helper) {
        helper.assertTrue(WOOLS.length == 16, "sixteen wool colours");
        for (Item wool : WOOLS) {
            helper.assertTrue(new ItemStack(wool).is(ItemTags.WOOL), wool + " must be in the wool tag");
            CraftingContainer grid = layout("RBW/OOO/O O", wool);
            Optional<CraftingRecipe> found = lookup(helper, grid);
            helper.assertTrue(found.isPresent() && found.get().getId().equals(id("desk")),
                    "the desk must accept " + wool + " but matched " + found);
        }
        for (Item other : new Item[]{Items.WHITE_CARPET, Items.STRING, Items.WHITE_BANNER, Items.WHITE_CONCRETE}) {
            helper.assertTrue(!new ItemStack(other).is(ItemTags.WOOL), other + " is not wool");
            Optional<CraftingRecipe> found = lookup(helper, layout("RBW/OOO/O O", other));
            helper.assertTrue(found.isEmpty(), "the desk must refuse " + other + " but matched " + found);
        }
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void craftingReturnsMilkAndLavaBuckets(GameTestHelper helper) {
        Item milk = Items.MILK_BUCKET;
        Item lava = Items.LAVA_BUCKET;

        // Ice cream: four snowballs, a milk bucket and a grudge.
        assertTakeReturnsBuckets(helper, new Object[]{Items.SNOWBALL, Items.SNOWBALL, Items.SNOWBALL, Items.SNOWBALL,
                milk, item("grudge")}, "combat_ration_4", 1);
        // Small shipyard: three lava buckets in the original layout.
        assertTakeReturnsBuckets(helper, new Object[]{item("grudge"), lava, item("grudge"),
                lava, Items.OBSIDIAN, lava, Items.OBSIDIAN, Items.OBSIDIAN, Items.OBSIDIAN}, "small_shipyard", 3);
        // Bucket repair: one lava bucket and a grudge.
        assertTakeReturnsBuckets(helper, new Object[]{lava, item("grudge")}, "bucket_repair", 1);
        helper.succeed();
    }

    // ===== helpers =====

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(Reference.MOD_ID, path);
    }

    private static Item item(String path) {
        Item found = BuiltInRegistries.ITEM.get(id(path));
        if (found == Items.AIR) {
            throw new IllegalStateException("no such item " + path);
        }
        return found;
    }

    private static Item symbol(char c, Item wool) {
        switch (c) {
            case 'R': return item("desk_item_radar");
            case 'B': return item("desk_item_book");
            case 'W': return wool;
            case 'O': return Items.OBSIDIAN;
            case 'G': return item("grudge");
            case 'L': return Items.LAVA_BUCKET;
            case 'A': return item("abyss_metal");
            case 'S': return Items.STICK;
            case 'M': return Items.MAGMA_BLOCK;
            case 'K': return item("grudge_block");
            case 'P': return Items.PISTON;
            default: throw new IllegalArgumentException("unknown symbol " + c);
        }
    }

    /** A 3x3 grid from three rows joined by '/'. A short row is padded with empty cells. */
    private static CraftingContainer layout(String rows, Item wool) {
        InventoryCraftingFake grid = new InventoryCraftingFake(3, 3);
        String[] split = rows.split("/");
        for (int y = 0; y < split.length; y++) {
            for (int x = 0; x < split[y].length(); x++) {
                char c = split[y].charAt(x);
                if (c != ' ') {
                    grid.setItem(y * 3 + x, new ItemStack(symbol(c, wool)));
                }
            }
        }
        return grid;
    }

    private static Optional<CraftingRecipe> lookup(GameTestHelper helper, CraftingContainer grid) {
        return helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, grid, helper.getLevel());
    }

    private static ItemStack assertCrafts(GameTestHelper helper, String recipe, String result, String rows,
                                          String label) {
        CraftingContainer grid = layout(rows, Items.WHITE_WOOL);
        Optional<CraftingRecipe> found = lookup(helper, grid);
        helper.assertTrue(found.isPresent(), label + " must craft from " + rows);
        helper.assertTrue(found.get().getId().equals(id(recipe)),
                label + ": expected recipe " + recipe + " but got " + found.get().getId());
        ItemStack out = found.get().assemble(grid, helper.getLevel().registryAccess());
        helper.assertTrue(out.getItem() == item(result) && out.getCount() == 1,
                label + ": expected one " + result + " but got " + out);
        return out;
    }

    private static void assertNotCrafts(GameTestHelper helper, String rows, String result) {
        Optional<CraftingRecipe> found = lookup(helper, layout(rows, Items.WHITE_WOOL));
        boolean makesIt = found.isPresent()
                && found.get().getResultItem(helper.getLevel().registryAccess()).getItem() == item(result);
        helper.assertTrue(!makesIt, rows + " must not craft " + result + " but matched "
                + found.map(recipe -> recipe.getId().toString()).orElse(""));
    }

    /** Puts the items in the table's grid in reading order, takes the result and counts the empty buckets left. */
    private static void assertTakeReturnsBuckets(GameTestHelper helper, Object[] inputs, String result, int buckets) {
        Player player = FakePlayerFactory.getMinecraft(helper.getLevel());
        CraftingMenu menu = new CraftingMenu(78, player.getInventory(),
                ContainerLevelAccess.create(helper.getLevel(), helper.absolutePos(BlockPos.ZERO)));
        for (int i = 0; i < inputs.length; i++) {
            menu.getSlot(1 + i).set(new ItemStack((Item) inputs[i]));
        }
        ItemStack offered = menu.getSlot(0).getItem();
        helper.assertTrue(offered.getItem() == item(result), result + " must be offered, but the result slot holds "
                + offered);
        menu.clicked(0, 0, ClickType.PICKUP, player);
        helper.assertTrue(menu.getCarried().getItem() == item(result), result + " must be taken, carrying "
                + menu.getCarried());
        int found = 0;
        for (int slot = 1; slot <= 9; slot++) {
            ItemStack left = menu.getSlot(slot).getItem();
            if (left.isEmpty()) {
                continue;
            }
            helper.assertTrue(left.getItem() == Items.BUCKET && left.getCount() == 1 && !left.hasTag(),
                    result + ": only a plain empty bucket may stay in the grid, found " + left);
            found++;
        }
        helper.assertTrue(found == buckets, result + ": expected " + buckets + " empty buckets but found " + found);
        menu.setCarried(ItemStack.EMPTY);
    }
}
