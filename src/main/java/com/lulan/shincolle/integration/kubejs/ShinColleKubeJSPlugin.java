package com.lulan.shincolle.integration.kubejs;

import com.lulan.shincolle.api.attribute.ShipAttributeType;
import com.lulan.shincolle.api.attribute.ShipAttributeRegistries;
import dev.latvian.mods.kubejs.KubeJSPlugin;
import dev.latvian.mods.kubejs.registry.RegistryInfo;

/**
 * Optional KubeJS bridge. KubeJS loads this class only when the manifest's required-mod check passes.
 */
public final class ShinColleKubeJSPlugin extends KubeJSPlugin {

    private static final RegistryInfo<ShipAttributeType> SHIP_ATTRIBUTE_REGISTRY =
            RegistryInfo.of(ShipAttributeRegistries.REGISTRY_KEY, ShipAttributeType.class)
                    .languageKeyPrefix("ship_attribute");

    @Override
    public void init() {
        SHIP_ATTRIBUTE_REGISTRY.addType("basic", ShipAttributeTypeKubeJSBuilder.class,
                ShipAttributeTypeKubeJSBuilder::new, true);
    }

    static RegistryInfo<ShipAttributeType> shipAttributeRegistry() {
        return SHIP_ATTRIBUTE_REGISTRY;
    }
}
