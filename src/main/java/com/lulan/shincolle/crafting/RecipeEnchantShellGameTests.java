package com.lulan.shincolle.crafting;

import com.lulan.shincolle.api.equipment.ShipAttackEffect;
import com.lulan.shincolle.entity.BasicEntityShip;
import com.lulan.shincolle.init.ModEntities;
import com.lulan.shincolle.init.ModItems;
import com.lulan.shincolle.item.BasicEquip;
import com.lulan.shincolle.item.EquipAmmo;
import com.lulan.shincolle.reference.Reference;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * The enchant shell has to come out of the crafting grid in the shape its readers expect:
 * a "PList" entry with a numeric effect id. Writing any other shape produces a shell that
 * looks and behaves like plain ammo, which is what this pins down.
 */
@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RecipeEnchantShellGameTests {

    private RecipeEnchantShellGameTests() {
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void craftedShellStoresTheEffectWhereItsReadersLook(GameTestHelper helper) {
        RecipeEnchantShell recipe = recipe();
        InventoryCraftingFake grid = grid(shell(), potion());

        if (!recipe.matches(grid, helper.getLevel())) {
            throw new AssertionError("A shell surrounded by identical potions must be a valid recipe");
        }

        ItemStack result = recipe.assemble(grid, helper.getLevel().registryAccess());
        CompoundTag stored = firstStoredEffect(result);

        assertInt(MobEffect.getId(MobEffects.POISON), stored.getInt(EquipAmmo.PID), "effect id");
        assertInt(0, stored.getInt(EquipAmmo.PLEVEL), "amplifier");
        assertInt(100, stored.getInt(EquipAmmo.PTIME), "duration ticks");
        assertInt(20, stored.getInt(EquipAmmo.PCHANCE), "chance percent");

        // Upstream replaced the whole tag here. In 1.20.1 that would erase the variant.
        assertInt(RecipeEnchantShell.ENCHANT_SHELL_VARIANT, BasicEquip.getEquipMeta(result),
                "variant after crafting");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void resultItemIsTheEnchantShellVariant(GameTestHelper helper) {
        ItemStack result = recipe().getResultItem(helper.getLevel().registryAccess());

        assertInt(RecipeEnchantShell.ENCHANT_SHELL_VARIANT, BasicEquip.getEquipMeta(result),
                "result item variant");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void craftedShellReachesTheAttackEffectMap(GameTestHelper helper) {
        RecipeEnchantShell recipe = recipe();
        ItemStack result = recipe.assemble(grid(shell(), potion()), helper.getLevel().registryAccess());

        BasicEntityShip ship = ModEntities.BB_KONGOU.get().create(helper.getLevel());
        if (ship == null) {
            throw new AssertionError("Failed to create the test ship");
        }
        ship.getCapaShipInventory().setStackInSlot(0, result);
        ship.calcShipAttributes(2, false);

        ResourceLocation poison = ResourceLocation.fromNamespaceAndPath("minecraft", "poison");
        ShipAttackEffect applied = ship.getAttackEffectMap().get(poison);
        if (applied == null) {
            throw new AssertionError("The crafted shell carried no effect into the attack map: "
                    + ship.getAttackEffectMap().keySet());
        }
        assertInt(0, applied.amplifier(), "applied amplifier");
        assertInt(100, applied.durationTicks(), "applied duration");
        assertInt(20, applied.chancePercent(), "applied chance");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void craftingTheSamePotionAgainExtendsTheShell(GameTestHelper helper) {
        RecipeEnchantShell recipe = recipe();
        ItemStack once = recipe.assemble(grid(shell(), potion()), helper.getLevel().registryAccess());
        ItemStack twice = recipe.assemble(grid(once, potion()), helper.getLevel().registryAccess());

        CompoundTag stored = firstStoredEffect(twice);
        assertInt(120, stored.getInt(EquipAmmo.PTIME), "extended duration");
        assertInt(30, stored.getInt(EquipAmmo.PCHANCE), "extended chance");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void mixedOrEffectlessPotionsAreRejected(GameTestHelper helper) {
        RecipeEnchantShell recipe = recipe();

        InventoryCraftingFake mixed = grid(shell(), potion());
        mixed.setItem(2, PotionUtils.setPotion(new ItemStack(Items.POTION), Potions.HEALING));
        if (recipe.matches(mixed, helper.getLevel())) {
            throw new AssertionError("Two different potions must not craft a shell");
        }

        InventoryCraftingFake water = grid(shell(), PotionUtils.setPotion(new ItemStack(Items.POTION), Potions.WATER));
        if (recipe.matches(water, helper.getLevel())) {
            throw new AssertionError("A potion with no effect must not craft a shell");
        }

        ItemStack ordinaryAmmo = ((BasicEquip) ModItems.EQUIP_AMMO.get()).createStack(0);
        InventoryCraftingFake nonEnchantShell = grid(ordinaryAmmo, potion());
        if (recipe.matches(nonEnchantShell, helper.getLevel())) {
            throw new AssertionError("Only variant 7 enchant shells may receive potion effects");
        }

        InventoryCraftingFake noShell = grid(new ItemStack(Items.STONE), potion());
        if (recipe.matches(noShell, helper.getLevel())) {
            throw new AssertionError("The centre slot must hold ship ammo");
        }
        helper.succeed();
    }

    private static RecipeEnchantShell recipe() {
        return new RecipeEnchantShell(
                ResourceLocation.fromNamespaceAndPath(Reference.MOD_ID, "enchant_shell"),
                CraftingBookCategory.MISC);
    }

    private static ItemStack shell() {
        return ((BasicEquip) ModItems.EQUIP_AMMO.get()).createStack(RecipeEnchantShell.ENCHANT_SHELL_VARIANT);
    }

    private static ItemStack potion() {
        return PotionUtils.setPotion(new ItemStack(Items.POTION), Potions.POISON);
    }

    private static InventoryCraftingFake grid(ItemStack centre, ItemStack surrounding) {
        InventoryCraftingFake grid = new InventoryCraftingFake(3, 3);
        for (int slot = 0; slot < 9; slot++) {
            grid.setItem(slot, slot == 4 ? centre.copy() : surrounding.copy());
        }
        return grid;
    }

    private static CompoundTag firstStoredEffect(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null) {
            throw new AssertionError("The crafted shell carried no NBT at all");
        }
        if (tag.getList(EquipAmmo.PLIST, Tag.TAG_COMPOUND).isEmpty()) {
            throw new AssertionError("The crafted shell has no " + EquipAmmo.PLIST + " entry; tag was " + tag);
        }
        return tag.getList(EquipAmmo.PLIST, Tag.TAG_COMPOUND).getCompound(0);
    }

    private static void assertInt(int expected, int actual, String what) {
        if (expected != actual) {
            throw new AssertionError("Wrong " + what + ": expected " + expected + " but was " + actual);
        }
    }
}
