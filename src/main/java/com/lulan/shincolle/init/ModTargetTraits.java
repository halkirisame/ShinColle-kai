package com.lulan.shincolle.init;

import com.lulan.shincolle.api.experimental.target.TargetTrait;
import com.lulan.shincolle.api.experimental.target.TargetTraitRegistries;
import com.lulan.shincolle.api.target.CoreTargetTraits;
import com.lulan.shincolle.reference.Reference;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.IForgeRegistry;
import net.minecraftforge.registries.RegistryBuilder;

import java.util.function.Supplier;

/** Registry of combat-target traits. Not saved, not synchronized, no overrides. */
public final class ModTargetTraits {

    public static final DeferredRegister<TargetTrait> TRAITS =
            DeferredRegister.create(TargetTraitRegistries.REGISTRY_KEY, Reference.MOD_ID);
    /** Resolves to {@code null} until the registry has been created. */
    public static final Supplier<IForgeRegistry<TargetTrait>> REGISTRY = TRAITS.makeRegistry(
            () -> new RegistryBuilder<TargetTrait>().disableSaving().disableSync().disableOverrides());

    static {
        TRAITS.register(CoreTargetTraits.ANTI_AIR_ELIGIBLE.getPath(),
                () -> new TargetTrait(CoreTargetTraits.ANTI_AIR_ELIGIBLE));
        TRAITS.register(CoreTargetTraits.ANTI_SUBMARINE_ELIGIBLE.getPath(),
                () -> new TargetTrait(CoreTargetTraits.ANTI_SUBMARINE_ELIGIBLE));
    }

    private ModTargetTraits() {
    }

    public static void register(IEventBus eventBus) {
        TRAITS.register(eventBus);
    }
}
