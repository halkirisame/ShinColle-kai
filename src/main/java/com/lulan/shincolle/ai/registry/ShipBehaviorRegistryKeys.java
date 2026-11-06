package com.lulan.shincolle.ai.registry;

import com.lulan.shincolle.reference.Reference;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

/** Keys of the internal ship AI, attack and trait registries. Not part of the public API. */
public final class ShipBehaviorRegistryKeys {

    public static final ResourceLocation AI_ID = new ResourceLocation(Reference.MOD_ID, "ship_ai");
    public static final ResourceLocation ATTACK_ID = new ResourceLocation(Reference.MOD_ID, "ship_attack");
    public static final ResourceLocation TRAIT_ID = new ResourceLocation(Reference.MOD_ID, "ship_trait");

    public static final ResourceKey<Registry<ShipAiType>> AI = ResourceKey.createRegistryKey(AI_ID);
    public static final ResourceKey<Registry<ShipAttackType>> ATTACK = ResourceKey.createRegistryKey(ATTACK_ID);
    public static final ResourceKey<Registry<ShipTraitType>> TRAIT = ResourceKey.createRegistryKey(TRAIT_ID);

    private ShipBehaviorRegistryKeys() {
    }
}
