package com.lulan.shincolle.equip;

import com.lulan.shincolle.ShinColle;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * Internal callback boundary between common equipment code and optional-mod
 * integrations. Callback signatures use only Minecraft and JDK types, so
 * callers never need to load classes from an absent optional dependency.
 */
public final class ShipEquipmentOptionalIntegrations {

    private static final Comparator<ResourceLocation> ID_ORDER =
            Comparator.comparing(ResourceLocation::toString);
    private static volatile Map<ResourceLocation, Function<LivingEntity, List<ItemStack>>> stackSources =
            Map.of();

    private ShipEquipmentOptionalIntegrations() {
    }

    /** Registers one optional equipment-storage source during mod construction. */
    public static synchronized void registerStackSource(ResourceLocation id,
                                                        Function<LivingEntity, List<ItemStack>> source) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(source, "source");
        if (stackSources.containsKey(id)) {
            throw new IllegalStateException("Duplicate optional equipment stack source: " + id);
        }
        Map<ResourceLocation, Function<LivingEntity, List<ItemStack>>> updated =
                new LinkedHashMap<>(stackSources);
        updated.put(id, source);
        stackSources = sortedCopy(updated);
    }

    /** Returns defensive stack copies from every loaded optional storage source. */
    public static List<ItemStack> getEquippedStacks(LivingEntity entity) {
        List<ItemStack> result = new ArrayList<>();
        for (Map.Entry<ResourceLocation, Function<LivingEntity, List<ItemStack>>> entry
                : stackSources.entrySet()) {
            try {
                List<ItemStack> stacks = entry.getValue().apply(entity);
                if (stacks == null) {
                    continue;
                }
                for (ItemStack stack : stacks) {
                    if (stack != null && !stack.isEmpty()) {
                        result.add(stack.copy());
                    }
                }
            } catch (RuntimeException error) {
                ShinColle.LOGGER.warn("Optional equipment stack source {} failed; continuing dispatch",
                        entry.getKey(), error);
            }
        }
        return List.copyOf(result);
    }

    private static <T> Map<ResourceLocation, T> sortedCopy(Map<ResourceLocation, T> source) {
        List<Map.Entry<ResourceLocation, T>> entries = new ArrayList<>(source.entrySet());
        entries.sort(Map.Entry.comparingByKey(ID_ORDER));
        Map<ResourceLocation, T> sorted = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, T> entry : entries) {
            sorted.put(entry.getKey(), entry.getValue());
        }
        return Collections.unmodifiableMap(sorted);
    }
}
