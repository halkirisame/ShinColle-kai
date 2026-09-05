package com.lulan.shincolle.crafting;

import com.google.gson.JsonObject;
import com.lulan.shincolle.init.ModItems;
import com.lulan.shincolle.init.ModRecipes;
import com.lulan.shincolle.item.BasicEquip;
import com.lulan.shincolle.item.EquipAmmo;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Custom crafting recipe: Enchant Shell
 * <p>
 * Pattern (3x3):
 * P P P
 * P A P
 * P P P
 * <p>
 * Where A = equip_ammo (center), P = identical potion items (all 8 surrounding
 * slots).
 * <p>
 * The potion effect is applied/stacked onto the ammo item's NBT:
 * - Same potion ID and amplifier as existing: duration += 20 ticks, chance +=
 * 10% (cap 100%)
 * - Different potion or amplifier: reset to duration=100, chance=20%
 * <p>
 * NBT on the result ammo: the {@code PList} list contains one compound with
 * {@code PID} (numeric mob effect ID), {@code PLV} (amplifier), {@code PTick}
 * (duration in ticks), and {@code PChance} (percentage, capped at 100).
 */
public class RecipeEnchantShell implements CraftingRecipe {

    public static final int ENCHANT_SHELL_VARIANT = 7;

    private final ResourceLocation id;
    private final CraftingBookCategory category;

    public RecipeEnchantShell(ResourceLocation id, CraftingBookCategory category) {
        this.id = id;
        this.category = category;
    }

    @Override
    public boolean matches(CraftingContainer container, Level level) {
        if (container.getWidth() < 3 || container.getHeight() < 3) {
            return false;
        }

        // Center slot (1, 1) must be equip_ammo
        ItemStack center = container.getItem(container.getWidth() + 1);
        if (center.isEmpty() || center.getItem() != ModItems.EQUIP_AMMO.get()) {
            return false;
        }
        if (BasicEquip.getEquipMeta(center) != ENCHANT_SHELL_VARIANT) {
            return false;
        }

        // All 8 surrounding slots must be the same potion item
        ItemStack firstPotion = ItemStack.EMPTY;

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                if (row == 1 && col == 1) {
                    continue; // skip center
                }

                ItemStack stack = container.getItem(row * container.getWidth() + col);

                if (stack.isEmpty()) {
                    return false;
                }

                // Must be a potion item (regular potion, splash, or lingering)
                if (stack.getItem() != Items.POTION
                        && stack.getItem() != Items.SPLASH_POTION
                        && stack.getItem() != Items.LINGERING_POTION) {
                    return false;
                }

                // Must have at least one mob effect
                List<MobEffectInstance> effects = PotionUtils.getMobEffects(stack);
                if (effects.isEmpty()) {
                    return false;
                }

                if (firstPotion.isEmpty()) {
                    firstPotion = stack;
                } else {
                    // All potions must be identical (same potion type via NBT)
                    if (!ItemStack.isSameItemSameTags(firstPotion, stack)) {
                        return false;
                    }
                }
            }
        }

        return !firstPotion.isEmpty();
    }

    @Override
    public ItemStack assemble(CraftingContainer container, RegistryAccess registryAccess) {
        // Get center equip_ammo and copy it
        ItemStack center = container.getItem(container.getWidth() + 1);
        ItemStack result = center.copy();

        // Get potion effect from one of the surrounding potions
        ItemStack potionStack = ItemStack.EMPTY;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                if (row == 1 && col == 1)
                    continue;
                ItemStack stack = container.getItem(row * container.getWidth() + col);
                if (!stack.isEmpty()) {
                    potionStack = stack;
                    break;
                }
            }
            if (!potionStack.isEmpty())
                break;
        }

        if (potionStack.isEmpty()) {
            return ItemStack.EMPTY;
        }

        List<MobEffectInstance> effects = PotionUtils.getMobEffects(potionStack);
        if (effects.isEmpty()) {
            return ItemStack.EMPTY;
        }

        // Use the first effect from the potion.
        //
        // The stored shape has to be the one the consumers read: EquipAmmo's tooltip and
        // LegacyBasicEquipEffects both walk the "PList" list and take PID as a numeric effect id
        // (MobEffect.byId), with PLV / PTick / PChance inside. Writing flat string-keyed tags
        // instead produced a shell that showed nothing and applied nothing.
        MobEffectInstance effect = effects.get(0);
        int potionId = MobEffect.getId(effect.getEffect());
        if (potionId < 1) {
            return result;
        }
        int amplifier = effect.getAmplifier();
        int durationTicks = 100;
        int chancePercent = 20;

        // Crafting the same potion into the same shell again extends it, as upstream did.
        CompoundTag existingTag = center.getTag();
        if (existingTag != null) {
            CompoundTag previous = existingTag.getList(EquipAmmo.PLIST, Tag.TAG_COMPOUND).getCompound(0);
            if (previous.getInt(EquipAmmo.PID) == potionId
                    && previous.getInt(EquipAmmo.PLEVEL) == amplifier) {
                durationTicks = previous.getInt(EquipAmmo.PTIME) + 20;
                chancePercent = Math.min(100, previous.getInt(EquipAmmo.PCHANCE) + 10);
            }
        }

        CompoundTag stored = new CompoundTag();
        stored.putInt(EquipAmmo.PID, potionId);
        stored.putInt(EquipAmmo.PLEVEL, amplifier);
        stored.putInt(EquipAmmo.PTIME, durationTicks);
        stored.putInt(EquipAmmo.PCHANCE, chancePercent);

        ListTag list = new ListTag();
        list.add(stored);

        // Upstream replaced the whole tag here, which was safe when the variant lived in item
        // damage. In 1.20.1 the variant is NBT (EquipMeta), so only the list may be written.
        result.getOrCreateTag().put(EquipAmmo.PLIST, list);

        return result;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width >= 3 && height >= 3;
    }

    @Override
    public ItemStack getResultItem(RegistryAccess registryAccess) {
        return ((BasicEquip) ModItems.EQUIP_AMMO.get()).createStack(ENCHANT_SHELL_VARIANT);
    }

    @Override
    public ResourceLocation getId() {
        return this.id;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipes.ENCHANT_SHELL.get();
    }

    @Override
    public CraftingBookCategory category() {
        return this.category;
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        return NonNullList.create();
    }

    @Override
    public boolean isSpecial() {
        return true;
    }

    // ========== Serializer ==========

    public static class Serializer implements RecipeSerializer<RecipeEnchantShell> {

        @Override
        public RecipeEnchantShell fromJson(ResourceLocation id, JsonObject json) {
            CraftingBookCategory category = CraftingBookCategory.CODEC.byName(
                    GsonHelper.getAsString(json, "category", null), CraftingBookCategory.MISC);
            return new RecipeEnchantShell(id, category);
        }

        @Override
        public RecipeEnchantShell fromNetwork(ResourceLocation id, FriendlyByteBuf buf) {
            CraftingBookCategory category = buf.readEnum(CraftingBookCategory.class);
            return new RecipeEnchantShell(id, category);
        }

        @Override
        public void toNetwork(FriendlyByteBuf buf, RecipeEnchantShell recipe) {
            buf.writeEnum(recipe.category());
        }
    }
}
