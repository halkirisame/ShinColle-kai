package com.lulan.shincolle.ai.observation;

import com.lulan.shincolle.ai.domain.TargetTraitKind;
import com.lulan.shincolle.api.experimental.target.TargetTrait;
import com.lulan.shincolle.api.target.CoreTargetTraits;
import com.lulan.shincolle.init.ModTargetTraits;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.registries.IForgeRegistry;

import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Resolves the registered target traits an entity type carries into the AI layer's trait kinds.
 *
 * <p>Nothing is cached: the registry and the entity-type tags are read at every call, so a query
 * made before the registry exists, during registration, or after a data pack reload answers from
 * the state at that moment and never stores it.</p>
 */
public final class TargetTraitAssignment {

    /** Decides which kinds an entity type carries. */
    @FunctionalInterface
    public interface Assignment {
        Set<TargetTraitKind> kindsFor(EntityType<?> type);
    }

    private static final Map<ResourceLocation, TargetTraitKind> BUILT_IN = Map.of(
            CoreTargetTraits.ANTI_AIR_ELIGIBLE, TargetTraitKind.ANTI_AIR_ELIGIBLE,
            CoreTargetTraits.ANTI_SUBMARINE_ELIGIBLE, TargetTraitKind.ANTI_SUBMARINE_ELIGIBLE);

    private static final Assignment TAGGED =
            type -> assignedKinds(ModTargetTraits.REGISTRY, trait -> type.is(trait.tag()));

    private static volatile Assignment override;

    private TargetTraitAssignment() {
    }

    /** The kinds the entity type carries, by the registered traits' tags. */
    public static Set<TargetTraitKind> kindsFor(EntityType<?> type) {
        Objects.requireNonNull(type, "type");
        Assignment replaced = override;
        return (replaced != null ? replaced : TAGGED).kindsFor(type);
    }

    /**
     * The kinds of the built-in traits that are registered and for which {@code carries} holds.
     * Production and tests share this function. A registry that does not exist yet has no traits.
     */
    public static Set<TargetTraitKind> assignedKinds(
            Supplier<IForgeRegistry<TargetTrait>> registrySupplier, Predicate<TargetTrait> carries) {
        IForgeRegistry<TargetTrait> registry = registrySupplier.get();
        if (registry == null) {
            return Set.of();
        }
        return kindsOfEntries(registry.getEntries(), carries);
    }

    /** Maps registered traits to kinds. Traits without a kind (those of addons) are not read. */
    public static Set<TargetTraitKind> kindsOfEntries(
            Iterable<Map.Entry<ResourceKey<TargetTrait>, TargetTrait>> entries, Predicate<TargetTrait> carries) {
        EnumSet<TargetTraitKind> kinds = EnumSet.noneOf(TargetTraitKind.class);
        for (Map.Entry<ResourceKey<TargetTrait>, TargetTrait> entry : entries) {
            TargetTraitKind kind = BUILT_IN.get(entry.getKey().location());
            if (kind != null && carries.test(entry.getValue())) {
                kinds.add(kind);
            }
        }
        return kinds.isEmpty() ? Set.of() : Set.copyOf(kinds);
    }

    /** Replaces the tag lookup until the returned handle is closed. Tests only; not re-entrant. */
    public static AutoCloseable overrideForTest(Assignment replacement) {
        Objects.requireNonNull(replacement, "replacement");
        Assignment previous = override;
        override = replacement;
        return () -> override = previous;
    }
}
