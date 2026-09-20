package com.lulan.shincolle.api.target;

import net.minecraft.world.entity.EntityType;

import java.util.Collections;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Public addon registry for assigning combat-target traits to entity types. */
public final class TargetTraits {

    private static final Registry GLOBAL = new Registry();

    private TargetTraits() {
    }

    /** Registers one non-exclusive trait during mod construction. */
    public static void registerTargetTrait(EntityType<?> entityType, TargetTrait trait) {
        GLOBAL.registerTargetTrait(entityType, trait);
    }

    /** Prevents further global registrations after addon construction has completed. */
    public static void freeze() {
        GLOBAL.freeze();
    }

    public static boolean frozen() {
        return GLOBAL.frozen();
    }

    /** Returns the registered traits for an entity type. */
    public static Set<TargetTrait> traitsFor(EntityType<?> entityType) {
        return GLOBAL.traitsFor(entityType);
    }

    /** Creates an isolated registry for validation and tests. */
    public static Registry detached() {
        return new Registry();
    }

    /** Isolated or global registration state. */
    public static final class Registry {

        private final Map<EntityType<?>, EnumSet<TargetTrait>> traitsByType = new IdentityHashMap<>();
        private boolean frozen;

        public synchronized void registerTargetTrait(EntityType<?> entityType, TargetTrait trait) {
            EntityType<?> checkedType = Objects.requireNonNull(entityType, "entityType");
            TargetTrait checkedTrait = Objects.requireNonNull(trait, "trait");
            if (this.frozen) {
                throw new IllegalStateException("Target trait registry is frozen");
            }
            this.traitsByType.computeIfAbsent(checkedType, ignored -> EnumSet.noneOf(TargetTrait.class))
                    .add(checkedTrait);
        }

        public synchronized Set<TargetTrait> traitsFor(EntityType<?> entityType) {
            EnumSet<TargetTrait> traits = this.traitsByType.get(Objects.requireNonNull(entityType, "entityType"));
            if (traits == null) {
                return Set.of();
            }
            return Collections.unmodifiableSet(EnumSet.copyOf(traits));
        }

        public synchronized void freeze() {
            this.frozen = true;
        }

        public synchronized boolean frozen() {
            return this.frozen;
        }
    }
}
