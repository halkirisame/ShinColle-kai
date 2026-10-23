package com.lulan.shincolle.init;

import com.lulan.shincolle.ai.registry.ShipAiType;
import com.lulan.shincolle.ai.registry.ShipAttackType;
import com.lulan.shincolle.ai.registry.ShipBehaviorRegistryBuilders;
import com.lulan.shincolle.ai.registry.ShipBehaviorRegistryKeys;
import com.lulan.shincolle.reference.Reference;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.IForgeRegistry;

import java.util.function.Supplier;

/** Internal registries of ship AI and attacks. Not saved, not synchronized, no overrides. */
public final class ModShipBehaviors {

    public static final DeferredRegister<ShipAiType> AIS =
            DeferredRegister.create(ShipBehaviorRegistryKeys.AI, Reference.MOD_ID);
    public static final DeferredRegister<ShipAttackType> ATTACKS =
            DeferredRegister.create(ShipBehaviorRegistryKeys.ATTACK, Reference.MOD_ID);

    /** Resolves to {@code null} until the registry has been created. */
    public static final Supplier<IForgeRegistry<ShipAiType>> AI_REGISTRY =
            AIS.makeRegistry(ShipBehaviorRegistryBuilders::<ShipAiType>internal);
    /** Resolves to {@code null} until the registry has been created. */
    public static final Supplier<IForgeRegistry<ShipAttackType>> ATTACK_REGISTRY =
            ATTACKS.makeRegistry(ShipBehaviorRegistryBuilders::<ShipAttackType>internal);

    private ModShipBehaviors() {
    }

    public static void register(IEventBus eventBus) {
        AIS.register(eventBus);
        ATTACKS.register(eventBus);
    }
}
