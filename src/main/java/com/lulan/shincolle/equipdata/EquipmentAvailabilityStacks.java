package com.lulan.shincolle.equipdata;

import com.lulan.shincolle.item.BasicEquip;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Builds display stacks for definitions hidden from normal acquisition surfaces. */
public final class EquipmentAvailabilityStacks {

    private EquipmentAvailabilityStacks() {
    }

    public static List<ItemStack> hiddenStacks(EquipDataSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        return snapshot.all().stream()
                .filter(definition -> definition.availability().isHidden())
                .sorted(Comparator.comparing(definition -> definition.id().toString()))
                .map(EquipmentAvailabilityStacks::createStack)
                .filter(stack -> !stack.isEmpty())
                .toList();
    }

    private static ItemStack createStack(EquipDefinition definition) {
        Item item = ForgeRegistries.ITEMS.getValue(definition.item());
        if (item == null) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = new ItemStack(item);
        BasicEquip.setEquipMeta(stack, definition.variant());
        return stack;
    }
}
