package com.lulan.shincolle.ai.registry;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.IForgeRegistry;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShipTraitRegistriesTest {
    @Test
    @SuppressWarnings("unchecked")
    void onlyRegisteredBuiltInIdsAreSelectableAndMissingRegistrySelectsNothing() {
        ResourceLocation builtIn = new ResourceLocation("shincolle_kai:sturdy");
        ResourceLocation foreign = new ResourceLocation("addon:sturdy");
        Set<ResourceLocation> ids = Set.of(builtIn, foreign);
        IForgeRegistry<ShipTraitType> registry = (IForgeRegistry<ShipTraitType>) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{IForgeRegistry.class}, (proxy, method, args) -> {
                    if (method.getName().equals("containsKey")) return ids.contains(args[0]);
                    throw new UnsupportedOperationException(method.getName());
                });
        assertTrue(ShipTraitRegistries.isSelectableTrait(() -> registry, builtIn));
        assertFalse(ShipTraitRegistries.isSelectableTrait(() -> registry, foreign));
        assertFalse(ShipTraitRegistries.isSelectableTrait(() -> registry, new ResourceLocation("shincolle_kai:missing")));
        assertFalse(ShipTraitRegistries.isSelectableTrait(() -> null, builtIn));
    }

    @Test
    void traitMetadataIsTypedAndRejectsInvalidMultipliers() {
        assertEquals(6, ShipTraitType.Attribute.values().length);
        ShipTraitType trait = new ShipTraitType(ShipTraitType.Attribute.ATK, 1.1);
        assertEquals(ShipTraitType.Attribute.ATK, trait.attribute());
        assertEquals(1.1, trait.multiplier());
        assertEquals(Set.of(new ResourceLocation("shincolle_kai:atk_l"), new ResourceLocation("shincolle_kai:atk_h"),
                new ResourceLocation("shincolle_kai:atk_al"), new ResourceLocation("shincolle_kai:atk_ah")),
                Set.copyOf(trait.attribute().attributeIds()));
        for (ShipTraitType.Attribute attribute : ShipTraitType.Attribute.values()) {
            assertFalse(attribute.attributeIds().isEmpty());
        }
        for (double invalid : new double[]{0, -1, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> new ShipTraitType(ShipTraitType.Attribute.HP, invalid));
        }
        assertThrows(NullPointerException.class, () -> new ShipTraitType(null, 1));
    }
}
