package com.lulan.shincolle.utility;

import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CraneItemFilterTest {
    @BeforeAll
    static void bootstrapMinecraft() throws ReflectiveOperationException {
        SharedConstants.tryDetectVersion();
        Field bootstrapped = Bootstrap.class.getDeclaredField("isBootstrapped");
        bootstrapped.setAccessible(true);
        if (bootstrapped.getBoolean(null)) return;
        bootstrapped.setBoolean(null, true);
        BuiltInRegistries.bootStrap();
    }

    @Test
    void disabledChecksRequireOnlySameNonemptyItem() {
        assertTrue(CraneItemFilter.matches(sword(2, "a"), sword(8, "b"), false, false, false));
        assertFalse(CraneItemFilter.matches(new ItemStack(Items.STONE), sword(2, "a"), false, false, false));
        assertFalse(CraneItemFilter.matches(ItemStack.EMPTY, ItemStack.EMPTY, false, false, false));
    }

    @Test
    void damageAndNbtAreIndependentForEveryFlagCombination() {
        for (boolean damage : new boolean[] {false, true}) {
            for (boolean nbt : new boolean[] {false, true}) {
                assertTrue(CraneItemFilter.matches(sword(2, "a"), sword(2, "a"), damage, nbt, false));
                assertEquals(!damage, CraneItemFilter.matches(sword(2, "a"), sword(8, "a"), damage, nbt, false));
                assertEquals(!nbt, CraneItemFilter.matches(sword(2, "a"), sword(2, "b"), damage, nbt, false));
            }
        }
    }

    @Test
    void emptyNormalizedNbtEqualsMissingNbtWithoutMutatingSources() {
        ItemStack tagged = new ItemStack(Items.STONE);
        tagged.getOrCreateTag().putInt("Damage", 3);
        CompoundTag before = tagged.getTag().copy();
        assertTrue(CraneItemFilter.matches(tagged, new ItemStack(Items.STONE), false, true, false));
        assertEquals(before, tagged.getTag());
    }

    @Test
    void nestedDamageRemainsPartOfNbt() {
        ItemStack first = sword(2, "a");
        CompoundTag child = new CompoundTag();
        child.putInt("Damage", 1);
        first.getOrCreateTag().put("Nested", child);
        ItemStack second = first.copy();
        second.getOrCreateTag().getCompound("Nested").putInt("Damage", 2);
        assertFalse(CraneItemFilter.matches(first, second, false, true, false));
        assertEquals(1, first.getTag().getCompound("Nested").getInt("Damage"));
    }

    @Test
    void notOverridesPositiveAndNotOnlyAllowsOtherItems() {
        ItemStack stone = new ItemStack(Items.STONE);
        ItemStack dirt = new ItemStack(Items.DIRT);
        assertFalse(CraneItemFilter.allows(stone, List.of(stone, stone), 2, false, false, false));
        assertTrue(CraneItemFilter.allows(dirt, List.of(stone), 1, false, false, false));
        assertFalse(CraneItemFilter.allows(dirt, List.of(stone), 0, false, false, false));
        assertTrue(CraneItemFilter.allows(dirt, List.of(ItemStack.EMPTY), 1, false, false, false));
    }

    @Test
    void lastFilterBitIsNotTruncated() {
        ItemStack stone = new ItemStack(Items.STONE);
        List<ItemStack> filters = java.util.stream.IntStream.range(0, 18)
                .mapToObj(slot -> slot == 17 ? stone : ItemStack.EMPTY).toList();
        assertFalse(CraneItemFilter.allows(stone, filters, 1 << 17, false, false, false));
        assertTrue(CraneItemFilter.allows(new ItemStack(Items.DIRT), filters, 1 << 17, false, false, false));
    }

    private static ItemStack sword(int damage, String label) {
        ItemStack stack = new ItemStack(Items.IRON_SWORD);
        stack.setDamageValue(damage);
        stack.getOrCreateTag().putString("Label", label);
        return stack;
    }
}
