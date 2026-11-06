package com.lulan.shincolle.gametest;

import com.lulan.shincolle.init.ModItems;
import com.lulan.shincolle.item.BasicEquip;
import com.lulan.shincolle.reference.Reference;
import com.mojang.authlib.GameProfile;
import net.minecraft.ChatFormatting;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.AnvilUpdateEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.Map;
import java.util.UUID;

/** Regression coverage for applying enchanted books through the Forge anvil event. */
@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class EquipmentAnvilGameTests {

    private EquipmentAnvilGameTests() {
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void equipmentAnvilReadsStoredBookAndPreservesInputs(GameTestHelper helper) {
        ItemStack equipment = equipment();
        BasicEquip.setEquipMeta(equipment, 2);
        equipment.setHoverName(Component.literal("Original cannon"));
        equipment.setRepairCost(80);
        equipment.getOrCreateTag().putString("CustomData", "retained");
        ItemStack book = book(Enchantments.SHARPNESS, 2);
        book.setRepairCost(90);
        ItemStack equipmentBefore = equipment.copy();
        ItemStack bookBefore = book.copy();
        helper.assertTrue(book.getEnchantmentTags().isEmpty()
                        && !EnchantedBookItem.getEnchantments(book).isEmpty(),
                "Fixture must store book enchantments only in StoredEnchantments.");

        AnvilUpdateEvent event = event(helper, equipment, book, "Original cannon");
        MinecraftForge.EVENT_BUS.post(event);

        ItemStack output = event.getOutput();
        helper.assertTrue(output.is(equipment.getItem()) && output != equipment,
                "Anvil must produce a separate copy of the equipment.");
        assertLevel(helper, output, Enchantments.SHARPNESS, 2);
        helper.assertTrue(event.getCost() == 30 && event.getMaterialCost() == 1,
                "Anvil must cost exactly 30 levels and one book regardless of repair costs.");
        helper.assertTrue(output.getBaseRepairCost() == 80 && BasicEquip.getEquipMeta(output) == 2
                        && "retained".equals(output.getOrCreateTag().getString("CustomData"))
                        && output.getHoverName().equals(equipmentBefore.getHoverName()),
                "Equipment metadata, repair cost and an unchanged name must be preserved.");
        helper.assertTrue(ItemStack.matches(equipment, equipmentBefore) && ItemStack.matches(book, bookBefore),
                "Preview calculation must not modify either input.");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void equipmentAnvilCombinesEqualLevelsAndCapsAtMaximum(GameTestHelper helper) {
        for (int level : new int[] {2, Enchantments.SHARPNESS.getMaxLevel()}) {
            ItemStack equipment = equipment();
            EnchantmentHelper.setEnchantments(Map.of(Enchantments.SHARPNESS, level), equipment);
            assertLevel(helper, equipment, Enchantments.SHARPNESS, level);
            AnvilUpdateEvent event = event(helper, equipment, book(Enchantments.SHARPNESS, level), "");
            MinecraftForge.EVENT_BUS.post(event);
            assertLevel(helper, event.getOutput(), Enchantments.SHARPNESS,
                    Math.min(level + 1, Enchantments.SHARPNESS.getMaxLevel()));
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void equipmentAnvilKeepsHigherUnequalLevelAndOtherEnchantments(GameTestHelper helper) {
        for (int[] levels : new int[][] {{1, 3}, {4, 2}}) {
            ItemStack equipment = equipment();
            EnchantmentHelper.setEnchantments(Map.of(Enchantments.SHARPNESS, levels[0],
                    Enchantments.UNBREAKING, 1), equipment);
            assertLevel(helper, equipment, Enchantments.SHARPNESS, levels[0]);
            AnvilUpdateEvent event = event(helper, equipment, book(Enchantments.SHARPNESS, levels[1]), "");
            MinecraftForge.EVENT_BUS.post(event);
            assertLevel(helper, event.getOutput(), Enchantments.SHARPNESS, Math.max(levels[0], levels[1]));
            assertLevel(helper, event.getOutput(), Enchantments.UNBREAKING, 1);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void equipmentAnvilAcceptsInapplicableAndIncompatibleEnchantments(GameTestHelper helper) {
        ItemStack equipment = equipment();
        ItemStack book = book(Enchantments.ALL_DAMAGE_PROTECTION, 3);
        EnchantedBookItem.addEnchantment(book, new EnchantmentInstance(Enchantments.FIRE_PROTECTION, 2));
        helper.assertTrue(!Enchantments.ALL_DAMAGE_PROTECTION.canEnchant(equipment)
                        && !Enchantments.ALL_DAMAGE_PROTECTION.isCompatibleWith(Enchantments.FIRE_PROTECTION),
                "Fixture must exercise both inapplicable and mutually exclusive enchantments.");
        AnvilUpdateEvent event = event(helper, equipment, book, "");
        MinecraftForge.EVENT_BUS.post(event);
        assertLevel(helper, event.getOutput(), Enchantments.ALL_DAMAGE_PROTECTION, 3);
        assertLevel(helper, event.getOutput(), Enchantments.FIRE_PROTECTION, 2);
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void equipmentAnvilLeavesOtherInputsUntouched(GameTestHelper helper) {
        ItemStack[][] inputs = {
                {new ItemStack(Items.DIAMOND_SWORD), book(Enchantments.SHARPNESS, 2)},
                {equipment(), new ItemStack(Items.BOOK)},
                {ItemStack.EMPTY, book(Enchantments.SHARPNESS, 2)},
                {equipment(), ItemStack.EMPTY}
        };
        for (ItemStack[] pair : inputs) {
            AnvilUpdateEvent event = event(helper, pair[0], pair[1], "Vanilla rename");
            MinecraftForge.EVENT_BUS.post(event);
            helper.assertTrue(event.getOutput().isEmpty() && event.getCost() == 7
                            && event.getMaterialCost() == 0 && !event.isCanceled(),
                    "Non-equipment, non-book and empty inputs must be left to vanilla handling.");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void equipmentAnvilReceivesCanceledEventWithoutUncanceling(GameTestHelper helper) {
        AnvilUpdateEvent event = event(helper, equipment(), book(Enchantments.SHARPNESS, 1), "");
        event.setCanceled(true);
        MinecraftForge.EVENT_BUS.post(event);
        assertLevel(helper, event.getOutput(), Enchantments.SHARPNESS, 1);
        helper.assertTrue(event.isCanceled(), "Handling a canceled event must not undo another mod's cancellation.");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void equipmentAnvilAppliesNewNameWithoutChangingInputsOrCost(GameTestHelper helper) {
        for (boolean alreadyNamed : new boolean[] {false, true}) {
            ItemStack equipment = equipment();
            if (alreadyNamed) {
                equipment.setHoverName(Component.literal("Old cannon"));
            }
            ItemStack book = book(Enchantments.SHARPNESS, 2);
            ItemStack equipmentBefore = equipment.copy();
            ItemStack bookBefore = book.copy();
            AnvilUpdateEvent event = event(helper, equipment, book, "New cannon");
            MinecraftForge.EVENT_BUS.post(event);

            ItemStack output = event.getOutput();
            assertLevel(helper, output, Enchantments.SHARPNESS, 2);
            helper.assertTrue(output.hasCustomHoverName()
                            && Component.literal("New cannon").equals(output.getHoverName()),
                    "A supplied name must apply to both unnamed and already named equipment.");
            helper.assertTrue(event.getCost() == 30 && event.getMaterialCost() == 1,
                    "Simultaneous renaming must not add any level or material cost.");
            helper.assertTrue(ItemStack.matches(equipment, equipmentBefore) && ItemStack.matches(book, bookBefore),
                    "Renaming the preview must not change either input.");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void equipmentAnvilRemovesCustomNameForNullEmptyAndBlankInput(GameTestHelper helper) {
        for (String name : new String[] {null, "", " \t "}) {
            for (boolean alreadyNamed : new boolean[] {false, true}) {
                ItemStack equipment = equipment();
                if (alreadyNamed) {
                    equipment.setHoverName(Component.literal("Old cannon"));
                }
                ItemStack equipmentBefore = equipment.copy();
                helper.assertTrue(equipment.hasCustomHoverName() == alreadyNamed,
                        "Fixture must exercise named and unnamed equipment.");
                AnvilUpdateEvent event = event(helper, equipment, book(Enchantments.SHARPNESS, 2), name);
                MinecraftForge.EVENT_BUS.post(event);

                assertLevel(helper, event.getOutput(), Enchantments.SHARPNESS, 2);
                helper.assertTrue(!event.getOutput().hasCustomHoverName(),
                        "Null, empty and whitespace-only names must leave the result without a custom name.");
                helper.assertTrue(event.getCost() == 30 && event.getMaterialCost() == 1
                                && ItemStack.matches(equipment, equipmentBefore),
                        "Removing a name must retain the fixed cost and leave the input untouched.");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void equipmentAnvilRemovesNameForNonBreakingSpace(GameTestHelper helper) {
        assertUnicodeBlankSurvivalResult(helper, "\u00a0");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void equipmentAnvilRemovesNameForFigureSpace(GameTestHelper helper) {
        assertUnicodeBlankSurvivalResult(helper, "\u2007");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void equipmentAnvilRemovesNameForNarrowNonBreakingSpace(GameTestHelper helper) {
        assertUnicodeBlankSurvivalResult(helper, "\u202f");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void equipmentAnvilKeepsSameNameComponentAndDefaultNameUnchanged(GameTestHelper helper) {
        for (boolean alreadyNamed : new boolean[] {false, true}) {
            ItemStack equipment = equipment();
            if (alreadyNamed) {
                equipment.setHoverName(Component.literal("Named cannon").withStyle(ChatFormatting.GOLD));
            }
            Component nameBefore = equipment.getHoverName().copy();
            AnvilUpdateEvent event = event(helper, equipment, book(Enchantments.SHARPNESS, 2), nameBefore.getString());
            MinecraftForge.EVENT_BUS.post(event);

            assertLevel(helper, event.getOutput(), Enchantments.SHARPNESS, 2);
            helper.assertTrue(event.getOutput().hasCustomHoverName() == alreadyNamed
                            && nameBefore.equals(event.getOutput().getHoverName()),
                    "An unchanged name must retain its component style or remain an unnamed default.");
            helper.assertTrue(event.getCost() == 30 && event.getMaterialCost() == 1,
                    "Leaving the name unchanged must retain the fixed cost.");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void equipmentAnvilSurvivalMenuChargesThirtyLevelsAndOneBook(GameTestHelper helper) {
        FakePlayer player = FakePlayerFactory.get(helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "anvil_survival"));
        player.getAbilities().instabuild = false;
        player.experienceLevel = 29;
        AnvilMenu menu = new AnvilMenu(0, player.getInventory());
        ItemStack book = book(Enchantments.SHARPNESS, 2);
        book.setCount(3);
        menu.getSlot(0).set(equipment());
        menu.getSlot(1).set(book);
        menu.setItemName("Renamed cannon");

        helper.assertTrue(!player.getAbilities().instabuild && menu.getCost() == 30,
                "Fixture must use survival mode and the handler's 30-level preview.");
        helper.assertTrue(!menu.getSlot(2).mayPickup(player), "29 levels must not permit taking the result.");
        player.experienceLevel = 30;
        helper.assertTrue(menu.getSlot(2).mayPickup(player), "30 levels must permit taking the result in survival.");
        ItemStack result = menu.getSlot(2).remove(1);
        assertLevel(helper, result, Enchantments.SHARPNESS, 2);
        helper.assertTrue(result.hasCustomHoverName() && Component.literal("Renamed cannon").equals(result.getHoverName()),
                "The survival menu result must include the supplied name as well as the enchantment.");
        menu.getSlot(2).onTake(player, result);
        helper.assertTrue(player.experienceLevel == 0 && menu.getSlot(0).getItem().isEmpty()
                        && menu.getSlot(1).getItem().getCount() == 2,
                "Taking the result must consume 30 levels, the equipment and exactly one book.");
        helper.succeed();
    }

    private static void assertUnicodeBlankSurvivalResult(GameTestHelper helper, String name) {
        FakePlayer player = FakePlayerFactory.get(helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "anvil_blank"));
        player.getAbilities().instabuild = false;
        player.experienceLevel = 30;
        ItemStack equipment = equipment();
        equipment.setHoverName(Component.literal("Old cannon"));
        ItemStack book = book(Enchantments.SHARPNESS, 2);
        book.setCount(3);
        ItemStack equipmentBefore = equipment.copy();
        ItemStack bookBefore = book.copy();
        helper.assertTrue(equipment.hasCustomHoverName() && "Old cannon".equals(equipment.getHoverName().getString())
                        && book.getCount() == 3 && !player.getAbilities().instabuild && player.experienceLevel == 30,
                "Fixture must use named equipment, three books and a survival player with 30 levels.");
        assertLevel(helper, book, Enchantments.SHARPNESS, 2);

        AnvilMenu menu = new AnvilMenu(0, player.getInventory());
        menu.getSlot(0).set(equipment);
        menu.getSlot(1).set(book);
        helper.assertTrue(menu.setItemName(name), "The Unicode blank name must be accepted by the real anvil menu.");
        ItemStack preview = menu.getSlot(2).getItem();
        assertLevel(helper, preview, Enchantments.SHARPNESS, 2);
        helper.assertTrue(menu.getCost() == 30 && menu.getSlot(2).mayPickup(player),
                "The Unicode blank preview must still cost 30 levels and allow survival pickup.");
        helper.assertTrue(ItemStack.matches(equipment, equipmentBefore) && ItemStack.matches(book, bookBefore),
                "The Unicode blank preview must not change either input.");

        ItemStack result = menu.getSlot(2).remove(1);
        menu.getSlot(2).onTake(player, result);
        assertLevel(helper, result, Enchantments.SHARPNESS, 2);
        helper.assertTrue(player.experienceLevel == 0 && menu.getSlot(0).getItem().isEmpty()
                        && menu.getSlot(1).getItem().getCount() == 2,
                "The Unicode blank result must consume exactly 30 levels, the equipment and one book.");
        helper.assertTrue(!result.hasCustomHoverName()
                        && result.getHoverName().equals(equipment().getHoverName()),
                "Unicode blank U+" + Integer.toHexString(name.charAt(0)) + " must remove the existing custom name.");
    }

    private static ItemStack equipment() {
        return new ItemStack(ModItems.EQUIP_CANNON.get());
    }

    private static ItemStack book(Enchantment enchantment, int level) {
        return EnchantedBookItem.createForEnchantment(new EnchantmentInstance(enchantment, level));
    }

    private static AnvilUpdateEvent event(GameTestHelper helper, ItemStack left, ItemStack right, String name) {
        return new AnvilUpdateEvent(left, right, name, 7, FakePlayerFactory.getMinecraft(helper.getLevel()));
    }

    private static void assertLevel(GameTestHelper helper, ItemStack stack, Enchantment enchantment, int expected) {
        helper.assertTrue(!stack.isEmpty() && EnchantmentHelper.getEnchantments(stack).getOrDefault(enchantment, 0) == expected,
                "Expected enchantment level " + expected + " for " + enchantment.getDescriptionId() + ".");
    }
}
