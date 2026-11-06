package com.lulan.shincolle.init;

import com.lulan.shincolle.ai.domain.combat.WeaponChannel;
import com.lulan.shincolle.ai.registry.GenericShipHost;
import com.lulan.shincolle.ai.registry.ShipAiType;
import com.lulan.shincolle.ai.registry.ShipAttackType;
import com.lulan.shincolle.ai.registry.ShipBehaviorRegistryBuilders;
import com.lulan.shincolle.ai.registry.ShipBehaviorRegistryKeys;
import com.lulan.shincolle.ai.registry.ShipTraitType;
import com.lulan.shincolle.reference.Reference;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.IForgeRegistry;
import net.minecraftforge.registries.RegistryObject;

import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Internal registries of ship AI, attacks and traits. Not saved, not synchronized, no overrides. */
public final class ModShipBehaviors {

    public static final DeferredRegister<ShipAiType> AIS =
            DeferredRegister.create(ShipBehaviorRegistryKeys.AI, Reference.MOD_ID);
    public static final DeferredRegister<ShipAttackType> ATTACKS =
            DeferredRegister.create(ShipBehaviorRegistryKeys.ATTACK, Reference.MOD_ID);
    public static final DeferredRegister<ShipTraitType> TRAITS =
            DeferredRegister.create(ShipBehaviorRegistryKeys.TRAIT, Reference.MOD_ID);

    /** Resolves to {@code null} until the registry has been created. */
    public static final Supplier<IForgeRegistry<ShipAiType>> AI_REGISTRY =
            AIS.makeRegistry(ShipBehaviorRegistryBuilders::<ShipAiType>internal);
    /** Resolves to {@code null} until the registry has been created. */
    public static final Supplier<IForgeRegistry<ShipAttackType>> ATTACK_REGISTRY =
            ATTACKS.makeRegistry(ShipBehaviorRegistryBuilders::<ShipAttackType>internal);
    /** Resolves to {@code null} until the registry has been created. */
    public static final Supplier<IForgeRegistry<ShipTraitType>> TRAIT_REGISTRY =
            TRAITS.makeRegistry(ShipBehaviorRegistryBuilders::<ShipTraitType>internal);

    public static final RegistryObject<ShipTraitType> STURDY = trait("sturdy", ShipTraitType.Attribute.HP);
    public static final RegistryObject<ShipTraitType> HEAVY_HITTER = trait("heavy_hitter", ShipTraitType.Attribute.ATK);
    public static final RegistryObject<ShipTraitType> ARMORED = trait("armored", ShipTraitType.Attribute.DEF);
    public static final RegistryObject<ShipTraitType> QUICK_LOADER = trait("quick_loader", ShipTraitType.Attribute.SPD);
    public static final RegistryObject<ShipTraitType> SWIFT = trait("swift", ShipTraitType.Attribute.MOV);
    public static final RegistryObject<ShipTraitType> KEEN_EYE = trait("keen_eye", ShipTraitType.Attribute.HIT);

    private static RegistryObject<ShipTraitType> trait(String name, ShipTraitType.Attribute attribute) {
        return TRAITS.register(name, () -> new ShipTraitType(attribute, 1.1));
    }

    public static final RegistryObject<ShipAiType> STANDARD = AIS.register("standard", () -> GenericShipHost::installStandardAi);

    public static final RegistryObject<ShipAttackType> LIGHT_CANNON = ATTACKS.register("light_cannon",
            () -> new BuiltinAttackType(Set.of(WeaponChannel.LIGHT), true, GenericShipHost::installCannonAttack));
    public static final RegistryObject<ShipAttackType> HEAVY_CANNON = ATTACKS.register("heavy_cannon",
            () -> new BuiltinAttackType(Set.of(WeaponChannel.HEAVY), true, GenericShipHost::installCannonAttack));
    public static final RegistryObject<ShipAttackType> MELEE = ATTACKS.register("melee",
            () -> new BuiltinAttackType(Set.of(WeaponChannel.MELEE), true, GenericShipHost::installMeleeAttack));
    public static final RegistryObject<ShipAttackType> AIRCRAFT = ATTACKS.register("aircraft",
            () -> new BuiltinAttackType(Set.of(WeaponChannel.AIR), false, ModShipBehaviors::unselectable));
    public static final RegistryObject<ShipAttackType> TENRYUU_SKILL = ATTACKS.register("tenryuu_skill",
            () -> new BuiltinAttackType(Set.of(), false, ModShipBehaviors::unselectable));
    public static final RegistryObject<ShipAttackType> TATSUTA_SKILL = ATTACKS.register("tatsuta_skill",
            () -> new BuiltinAttackType(Set.of(), false, ModShipBehaviors::unselectable));
    public static final RegistryObject<ShipAttackType> NAGATO_SKILL = ATTACKS.register("nagato_skill",
            () -> new BuiltinAttackType(Set.of(), false, ModShipBehaviors::unselectable));

    private static void unselectable(GenericShipHost host) {
        throw new UnsupportedOperationException("This attack is registered only; generic ships cannot install it");
    }

    private static final class BuiltinAttackType implements ShipAttackType {
        private final Set<WeaponChannel> channels;
        private final boolean selectableByDefinition;
        private final Consumer<GenericShipHost> installer;

        private BuiltinAttackType(Set<WeaponChannel> channels, boolean selectableByDefinition,
                Consumer<GenericShipHost> installer) {
            this.channels = Set.copyOf(channels);
            this.selectableByDefinition = selectableByDefinition;
            this.installer = installer;
        }

        @Override
        public Set<WeaponChannel> channels() {
            return channels;
        }

        @Override
        public boolean selectableByDefinition() {
            return selectableByDefinition;
        }

        @Override
        public void install(GenericShipHost host) {
            installer.accept(host);
        }
    }

    private ModShipBehaviors() {
    }

    public static void register(IEventBus eventBus) {
        AIS.register(eventBus);
        ATTACKS.register(eventBus);
        TRAITS.register(eventBus);
    }
}
