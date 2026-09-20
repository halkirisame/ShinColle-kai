package com.lulan.shincolle.api.equipment;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Deterministic provider registry for ship equipment.
 *
 * <p>Addons register during construction and the core freezes the global registry at load complete.
 * A detached registry can be used by tests without changing global registration state.</p>
 */
public final class ShipEquipmentProviders {

    private static final Logger LOGGER = LoggerFactory.getLogger(ShipEquipmentProviders.class);
    private static final Registry GLOBAL = new Registry();

    private ShipEquipmentProviders() {
    }

    /** Registers one provider in the global addon registry. */
    public static void register(ResourceLocation id, int priority, ShipEquipmentProvider provider) {
        GLOBAL.register(id, priority, provider);
    }

    /** Prevents further global provider registration after addon construction has completed. */
    public static void freeze() {
        GLOBAL.freeze();
    }

    public static boolean frozen() {
        return GLOBAL.frozen();
    }

    /** Creates an isolated registry for validation and tests. */
    public static Registry detached() {
        return new Registry();
    }

    static Registry global() {
        return GLOBAL;
    }

    /**
     * Isolated or global registry state. Provider precedence is priority descending, then ID ascending.
     */
    public static final class Registry {

        private final Map<ResourceLocation, RegisteredProvider> byId = new HashMap<>();
        private final Set<String> reportedMatchFailures = new HashSet<>();
        private boolean frozen;
        private List<RegisteredProvider> ordered = List.of();

        public synchronized void register(ResourceLocation id, int priority, ShipEquipmentProvider provider) {
            ResourceLocation checkedId = requireProviderId(id);
            Objects.requireNonNull(provider, "provider");
            if (this.frozen) {
                throw new IllegalStateException("Ship equipment provider registry is frozen");
            }
            if (this.byId.containsKey(checkedId)) {
                throw new IllegalArgumentException("Duplicate ship equipment provider ID " + checkedId);
            }
            this.byId.put(checkedId, new RegisteredProvider(checkedId, priority, provider));
            rebuildOrder();
        }

        public synchronized void freeze() {
            this.frozen = true;
        }

        public synchronized boolean frozen() {
            return this.frozen;
        }

        /**
         * Returns only the first provider whose predicate matches. A failed predicate is skipped;
         * a later resolve failure must not fall back to a lower-priority provider.
         */
        public synchronized Optional<Match> find(ItemStack stack) {
            Objects.requireNonNull(stack, "stack");
            for (RegisteredProvider registered : this.ordered) {
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

        private void rebuildOrder() {
            List<RegisteredProvider> result = new ArrayList<>(this.byId.values());
            result.sort(Comparator.comparingInt(RegisteredProvider::priority).reversed()
                    .thenComparing(entry -> entry.id().toString()));
            this.ordered = List.copyOf(result);
        }

        private void reportMatchFailure(ResourceLocation providerId, ItemStack stack, RuntimeException error) {
            String key = providerId + "|" + stack.getItem();
            if (this.reportedMatchFailures.add(key)) {
                LOGGER.warn("Ship equipment provider {} threw from matches for {}; skipping this provider",
                        providerId, stack, error);
            }
        }
    }

    /** Deterministic provider selected by a side-safe predicate. */
    public record Match(ResourceLocation id, ShipEquipmentProvider provider) {

        public Match {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(provider, "provider");
        }
    }

    private record RegisteredProvider(ResourceLocation id, int priority, ShipEquipmentProvider provider) {
    }

    private static ResourceLocation requireProviderId(ResourceLocation id) {
        ResourceLocation checkedId = Objects.requireNonNull(id, "id");
        if (checkedId.getNamespace().isBlank() || checkedId.getPath().isBlank()) {
            throw new IllegalArgumentException("Ship equipment provider ID must not be blank");
        }
        return checkedId;
    }
}
