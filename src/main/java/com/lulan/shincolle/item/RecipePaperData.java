package com.lulan.shincolle.item;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** The shared boundary for the persisted crafting pattern and its recorded result. */
public final class RecipePaperData {
    private static final int INPUT_COUNT = 9;
    private static final int MAX_ENTRIES = 10;

    private RecipePaperData() { }

    public sealed interface Decoded permits Valid, Invalid { }

    public enum Invalid implements Decoded {
        INSTANCE
    }

    /** Owns its stacks; callers receive independent copies, never the persisted objects. */
    public static final class Valid implements Decoded {
        private final List<ItemStack> inputs;
        private final ItemStack recordedResult;

        private Valid(List<ItemStack> inputs, ItemStack recordedResult) {
            this.inputs = inputs;
            this.recordedResult = recordedResult;
        }

        public ItemStack input(int index) {
            return inputs.get(index).copy();
        }

        public ItemStack recordedResult() {
            return recordedResult.copy();
        }
    }

    public static Decoded decode(ItemStack paper) {
        List<ItemStack> inputs = new ArrayList<>(INPUT_COUNT);
        for (int i = 0; i < INPUT_COUNT; i++) inputs.add(ItemStack.EMPTY);
        CompoundTag root = paper.getTag();
        if (root == null || !root.contains("Recipe")) return new Valid(inputs, ItemStack.EMPTY);
        Tag raw = root.get("Recipe");
        if (!(raw instanceof ListTag entries) || entries.size() > MAX_ENTRIES
                || (!entries.isEmpty() && entries.getElementType() != Tag.TAG_COMPOUND)) return Invalid.INSTANCE;

        boolean[] seen = new boolean[MAX_ENTRIES];
        ItemStack recordedResult = ItemStack.EMPTY;
        try {
            for (Tag entry : entries) {
                CompoundTag stackTag = (CompoundTag) entry;
                if (!stackTag.contains("Slot", Tag.TAG_INT)) return Invalid.INSTANCE;
                int slot = stackTag.getInt("Slot");
                if (slot < 0 || slot >= MAX_ENTRIES || seen[slot]) return Invalid.INSTANCE;
                seen[slot] = true;
                if (!stackTag.contains("id", Tag.TAG_STRING) || !stackTag.contains("Count", Tag.TAG_BYTE)
                        || (stackTag.contains("tag") && !stackTag.contains("tag", Tag.TAG_COMPOUND))
                        || (stackTag.contains("ForgeCaps") && !stackTag.contains("ForgeCaps", Tag.TAG_COMPOUND))) {
                    return Invalid.INSTANCE;
                }
                ResourceLocation id = ResourceLocation.tryParse(stackTag.getString("id"));
                if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) return Invalid.INSTANCE;
                int count = stackTag.getByte("Count");
                boolean air = BuiltInRegistries.ITEM.get(id) == Items.AIR;
                if (air ? count != 0 : count <= 0) return Invalid.INSTANCE;
                ItemStack stack = air ? ItemStack.EMPTY : ItemStack.of(stackTag.copy());
                if (!air && stack.isEmpty()) return Invalid.INSTANCE;
                if (slot < INPUT_COUNT) {
                    inputs.set(slot, stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1));
                } else {
                    recordedResult = stack;
                }
            }
        } catch (RuntimeException invalidStackData) {
            // Item loading/capability deserialization is an external NBT boundary.
            return Invalid.INSTANCE;
        }
        return new Valid(inputs, recordedResult);
    }

    /** Replace only Recipe, and only after an accepted server-side edit. */
    public static void write(ItemStack paper, List<ItemStack> inputs, ItemStack result) {
        ListTag entries = new ListTag();
        for (int i = 0; i < INPUT_COUNT; i++) {
            ItemStack input = inputs.get(i);
            if (!input.isEmpty()) {
                CompoundTag entry = input.copyWithCount(1).save(new CompoundTag());
                entry.putInt("Slot", i);
                entries.add(entry);
            }
        }
        if (!result.isEmpty()) {
            CompoundTag entry = result.save(new CompoundTag());
            entry.putInt("Slot", INPUT_COUNT);
            entries.add(entry);
        }
        paper.getOrCreateTag().put("Recipe", entries);
    }
}
