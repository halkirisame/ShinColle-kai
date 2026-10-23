package com.lulan.shincolle.ai.registry;

import com.lulan.shincolle.init.ModShipBehaviors;
import com.lulan.shincolle.reference.Reference;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.IForgeRegistry;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Queries on the internal ship AI and attack registries. Every query is safe at any time:
 * a registry that does not exist yet has no selectable entries, and nothing here fixes a state.
 */
public final class ShipBehaviorRegistries {

    private static final AtomicInteger FOREIGN_CHECK_RUNS = new AtomicInteger();

    private ShipBehaviorRegistries() {
    }

    /** How many times the foreign-entry check has run. It runs once, after the registries are frozen. */
    public static int foreignCheckRuns() {
        return FOREIGN_CHECK_RUNS.get();
    }

    /** True for the namespace whose entries ship definitions may select in this version. */
    public static boolean isSelectableNamespace(ResourceLocation id) {
        Objects.requireNonNull(id, "id");
        return Reference.MOD_ID.equals(id.getNamespace());
    }

    /** True when the AI is registered under the built-in namespace. */
    public static boolean isSelectableAi(ResourceLocation id) {
        return isSelectable(ModShipBehaviors.AI_REGISTRY, id);
    }

    /** True when the attack is registered under the built-in namespace. */
    public static boolean isSelectableAttack(ResourceLocation id) {
        return isSelectable(ModShipBehaviors.ATTACK_REGISTRY, id);
    }

    /** Shared by production and tests. A registry that does not exist yet selects nothing. */
    public static <T> boolean isSelectable(Supplier<IForgeRegistry<T>> registrySupplier, ResourceLocation id) {
        Objects.requireNonNull(id, "id");
        if (!isSelectableNamespace(id)) {
            return false;
        }
        IForgeRegistry<T> registry = registrySupplier.get();
        return registry != null && registry.containsKey(id);
    }

    /** The IDs outside the built-in namespace, each once, in a stable order. */
    public static List<ResourceLocation> foreignIds(Collection<ResourceLocation> ids) {
        TreeSet<ResourceLocation> foreign = new TreeSet<>();
        for (ResourceLocation id : ids) {
            if (!isSelectableNamespace(id)) {
                foreign.add(id);
            }
        }
        return new ArrayList<>(foreign);
    }

    /** Reports each foreign entry of the registry once. A registry that does not exist reports nothing. */
    public static <T> int reportForeign(
            Supplier<IForgeRegistry<T>> registrySupplier, Consumer<ResourceLocation> report) {
        IForgeRegistry<T> registry = registrySupplier.get();
        if (registry == null) {
            return 0;
        }
        List<ResourceLocation> foreign = foreignIds(registry.getKeys());
        foreign.forEach(report);
        return foreign.size();
    }

    /** Logs the foreign entries of both registries at ERROR. Does not stop the startup. */
    public static void logForeignEntries(Logger logger) {
        logForeignEntries(logger, ModShipBehaviors.AI_REGISTRY, ModShipBehaviors.ATTACK_REGISTRY);
    }

    /** Shared by production and tests. */
    public static <A, B> void logForeignEntries(Logger logger, Supplier<IForgeRegistry<A>> aiRegistry,
            Supplier<IForgeRegistry<B>> attackRegistry) {
        FOREIGN_CHECK_RUNS.incrementAndGet();
        reportForeign(aiRegistry, id -> logger.error(
                "Ship AI '{}' is ignored: only the '{}' namespace can be selected in this version",
                id, Reference.MOD_ID));
        reportForeign(attackRegistry, id -> logger.error(
                "Ship attack '{}' is ignored: only the '{}' namespace can be selected in this version",
                id, Reference.MOD_ID));
    }
}
