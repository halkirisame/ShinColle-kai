package com.lulan.shincolle.api.attribute;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.IForgeRegistry;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Deterministic in-process dense layout for registered ship attributes.
 * Dense indices are an implementation detail and must never be serialized.
 */
public final class ShipAttributeLayout {

    private static volatile ShipAttributeLayout current;

    private final List<ResourceLocation> ids;
    private final List<ShipAttributeType> types;
    private final Map<ResourceLocation, Integer> indexById;
    private final Map<ShipAttributeType, Integer> indexByType;

    private ShipAttributeLayout(Map<ResourceLocation, ShipAttributeType> entries) {
        List<ResourceLocation> sortedIds = new ArrayList<>(entries.keySet());
        sortedIds.sort(Comparator.comparing(ResourceLocation::toString));

        List<ShipAttributeType> sortedTypes = new ArrayList<>(sortedIds.size());
        Map<ResourceLocation, Integer> idsToIndexes = new LinkedHashMap<>();
        Map<ShipAttributeType, Integer> typesToIndexes = new IdentityHashMap<>();
        for (int i = 0; i < sortedIds.size(); i++) {
            ResourceLocation id = sortedIds.get(i);
            ShipAttributeType type = Objects.requireNonNull(entries.get(id), "attribute type for " + id);
            if (typesToIndexes.put(type, i) != null) {
                throw new IllegalArgumentException("The same ShipAttributeType is assigned to multiple IDs");
            }
            sortedTypes.add(type);
            idsToIndexes.put(id, i);
        }

        this.ids = List.copyOf(sortedIds);
        this.types = List.copyOf(sortedTypes);
        this.indexById = Collections.unmodifiableMap(idsToIndexes);
        this.indexByType = Collections.unmodifiableMap(typesToIndexes);
    }

    /**
     * Initializes the canonical layout after all code registrations have completed.
     */
    public static synchronized ShipAttributeLayout initialize(IForgeRegistry<ShipAttributeType> registry) {
        Objects.requireNonNull(registry, "registry");
        Map<ResourceLocation, ShipAttributeType> entries = new HashMap<>();
        for (ResourceLocation id : registry.getKeys()) {
            ShipAttributeType previous = entries.put(id, registry.getValue(id));
            if (previous != null) {
                throw new IllegalStateException("Duplicate ship attribute ID " + id);
            }
        }
        ShipAttributeLayout candidate = new ShipAttributeLayout(entries);
        if (current != null) {
            if (!current.ids.equals(candidate.ids) || !current.types.equals(candidate.types)) {
                throw new IllegalStateException("Ship attribute layout was already initialized with different entries");
            }
            return current;
        }
        current = candidate;
        return current;
    }

    /**
     * Creates a detached layout for addon validation and tests without changing the canonical layout.
     */
    public static ShipAttributeLayout detached(Map<ResourceLocation, ShipAttributeType> entries) {
        Objects.requireNonNull(entries, "entries");
        return new ShipAttributeLayout(Map.copyOf(entries));
    }

    public static ShipAttributeLayout current() {
        ShipAttributeLayout result = current;
        if (result == null) {
            throw new IllegalStateException("Ship attribute layout is not initialized yet");
        }
        return result;
    }

    public int size() {
        return this.ids.size();
    }

    public List<ResourceLocation> ids() {
        return this.ids;
    }

    public Collection<ShipAttributeType> types() {
        return this.types;
    }

    public int indexOf(ResourceLocation id) {
        Integer index = this.indexById.get(Objects.requireNonNull(id, "id"));
        return index == null ? -1 : index;
    }

    public int indexOf(ShipAttributeType type) {
        Integer index = this.indexByType.get(Objects.requireNonNull(type, "type"));
        return index == null ? -1 : index;
    }

    public ResourceLocation idAt(int index) {
        return this.ids.get(index);
    }

    public ShipAttributeType typeAt(int index) {
        return this.types.get(index);
    }

    public ShipAttributeType type(ResourceLocation id) {
        int index = this.indexOf(id);
        return index < 0 ? null : this.types.get(index);
    }
}
