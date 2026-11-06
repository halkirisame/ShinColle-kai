package com.lulan.shincolle.utility;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** Read-only comparisons shared by crane transfer and completion checks. */
public final class CraneItemFilter {
    private CraneItemFilter() { }

    public static boolean matches(ItemStack item, ItemStack filter, boolean damage, boolean nbt, boolean dictionary) {
        if (item.isEmpty() || filter.isEmpty()) return false;
        if (ItemStack.isSameItem(item, filter)) {
            return (!damage || item.getDamageValue() == filter.getDamageValue())
                    && (!nbt || Objects.equals(withoutDamage(item), withoutDamage(filter)));
        }
        if (!dictionary) return false;
        Set<TagKey<Item>> tags = filter.getTags()
                .filter(tag -> "forge".equals(tag.location().getNamespace())).collect(Collectors.toSet());
        return item.getTags().anyMatch(tags::contains);
    }

    /** Exclusions always win; with no positive entries, accept every non-excluded item. */
    public static boolean allows(ItemStack item, List<ItemStack> filters, int notMask,
                                 boolean damage, boolean nbt, boolean dictionary) {
        if (item.isEmpty()) return false;
        boolean hasPositive = false;
        boolean positiveMatch = false;
        for (int slot = 0; slot < filters.size(); slot++) {
            ItemStack filter = filters.get(slot);
            if (filter.isEmpty()) continue;
            boolean match = matches(item, filter, damage, nbt, dictionary);
            if ((notMask & (1 << slot)) != 0) {
                if (match) return false;
            } else {
                hasPositive = true;
                positiveMatch |= match;
            }
        }
        return !hasPositive || positiveMatch;
    }

    private static CompoundTag withoutDamage(ItemStack stack) {
        if (!stack.hasTag()) return null;
        CompoundTag tag = stack.getTag().copy();
        tag.remove("Damage");
        return tag.isEmpty() ? null : tag;
    }
}
