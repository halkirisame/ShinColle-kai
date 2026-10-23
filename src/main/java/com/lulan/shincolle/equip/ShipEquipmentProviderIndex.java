package com.lulan.shincolle.equip;

import com.lulan.shincolle.api.equipment.ShipEquipmentProvider;
import com.lulan.shincolle.init.ModEquipmentProviders;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.IForgeRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Immutable, ordered view of the registered {@link ShipEquipmentProvider}s.
 * Precedence is priority descending, then ID ascending. The global index is built once, when
 * registration has finished ({@link #sealGlobal()}); {@link #of(Map)} builds an isolated one for tests.
 * A lookup made before that point is answered from the providers registered so far and is never kept,
 * so providers registered afterwards are still recognized.
 */
public final class ShipEquipmentProviderIndex {

    private static final Logger LOGGER = LoggerFactory.getLogger(ShipEquipmentProviderIndex.class);
    private static final Source GLOBAL = new Source(() -> registeredProviders(ModEquipmentProviders.REGISTRY));

    private final List<Entry> ordered;
    private final Set<String> reportedMatchFailures = ConcurrentHashMap.newKeySet();

    private ShipEquipmentProviderIndex(List<Entry> ordered) {
        this.ordered = ordered;
    }

    /**
     * The index of the Forge registry: the fixed one once {@link #sealGlobal()} has run, otherwise a
     * throwaway one over the providers registered so far.
     */
    public static ShipEquipmentProviderIndex global() {
        return GLOBAL.current();
    }

    /** Fixes the global index. Call once registration has finished (common setup). */
    public static void sealGlobal() {
        GLOBAL.seal();
    }

    /**
     * Copies the registry's entries into a map. The registry does not exist before the registry-creation
     * event (its supplier returns null); until then nothing is registered and the map is empty.
     * The same conversion serves the global index and tests.
     */
    public static Map<ResourceLocation, ShipEquipmentProvider> registeredProviders(
            Supplier<IForgeRegistry<ShipEquipmentProvider>> registrySupplier) {
        IForgeRegistry<ShipEquipmentProvider> registry = registrySupplier.get();
        if (registry == null) {
            return Map.of();
        }
        Map<ResourceLocation, ShipEquipmentProvider> providers = new HashMap<>();
        for (Map.Entry<ResourceKey<ShipEquipmentProvider>, ShipEquipmentProvider> entry
                : registry.getEntries()) {
            providers.put(entry.getKey().location(), entry.getValue());
        }
        return providers;
    }

    /** Builds an isolated index from an arbitrary set of providers, for validation and tests. */
    public static ShipEquipmentProviderIndex of(Map<ResourceLocation, ShipEquipmentProvider> providers) {
        Objects.requireNonNull(providers, "providers");
        List<Entry> entries = new ArrayList<>(providers.size());
        providers.forEach((id, provider) -> entries.add(new Entry(Objects.requireNonNull(id, "id"),
                priorityOf(id, Objects.requireNonNull(provider, "provider")), provider)));
        entries.sort(Comparator.comparingInt(Entry::priority).reversed()
                .thenComparing(entry -> entry.id().toString()));
        return new ShipEquipmentProviderIndex(List.copyOf(entries));
    }

    /** Provider IDs in precedence order. */
    public List<ResourceLocation> ids() {
        return this.ordered.stream().map(Entry::id).toList();
    }

    /**
     * Returns only the first provider whose predicate matches. A failed predicate is skipped;
     * a later resolve failure must not fall back to a lower-priority provider.
     */
    public Optional<Match> find(ItemStack stack) {
        Objects.requireNonNull(stack, "stack");
        for (Entry registered : this.ordered) {
            boolean matched;
            try {
                matched = registered.provider().matches(stack.copy());
            } catch (RuntimeException error) {
                reportMatchFailure(registered.id(), stack, error);
                continue;
            }
            if (matched) {
                return Optional.of(new Match(registered.id(), registered.provider()));
            }
        }
        return Optional.empty();
    }

    /**
     * Holds an index over a growing set of providers. Until {@link #seal()} every {@link #current()} builds a
     * fresh index from the set as it is now and keeps nothing; after it, the index built at that moment is fixed.
     * A supplier may return null while nothing exists yet, which counts as an empty set.
     * Public so that tests can drive the lifecycle with their own set.
     */
    public static final class Source {

        private final Supplier<Map<ResourceLocation, ShipEquipmentProvider>> providers;
        private ShipEquipmentProviderIndex sealed;

        public Source(Supplier<Map<ResourceLocation, ShipEquipmentProvider>> providers) {
            this.providers = Objects.requireNonNull(providers, "providers");
        }

        public synchronized ShipEquipmentProviderIndex current() {
            return this.sealed != null ? this.sealed : build();
        }

        public synchronized void seal() {
            if (this.sealed == null) {
                this.sealed = build();
            }
        }

        private ShipEquipmentProviderIndex build() {
            Map<ResourceLocation, ShipEquipmentProvider> current = this.providers.get();
            return of(current == null ? Map.of() : current);
        }
    }

    private static int priorityOf(ResourceLocation id, ShipEquipmentProvider provider) {
        try {
            return provider.priority();
        } catch (RuntimeException error) {
            LOGGER.warn("Ship equipment provider {} threw from priority; using 0", id, error);
            return 0;
        }
    }

    private void reportMatchFailure(ResourceLocation providerId, ItemStack stack, RuntimeException error) {
        String key = providerId + "|" + stack.getItem();
        if (this.reportedMatchFailures.add(key)) {
            LOGGER.warn("Ship equipment provider {} threw from matches for {}; skipping this provider",
                    providerId, stack, error);
        }
    }

    /** Deterministic provider selected by a side-safe predicate. */
    public record Match(ResourceLocation id, ShipEquipmentProvider provider) {

        public Match {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(provider, "provider");
        }
    }

    private record Entry(ResourceLocation id, int priority, ShipEquipmentProvider provider) {
    }
}
