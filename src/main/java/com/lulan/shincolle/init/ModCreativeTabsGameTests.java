package com.lulan.shincolle.init;

import com.lulan.shincolle.api.attribute.ShipAttributeLayout;
import com.lulan.shincolle.api.attribute.ShipAttributeValues;
import com.lulan.shincolle.equipdata.EquipAvailability;
import com.lulan.shincolle.equipdata.EquipDataSnapshot;
import com.lulan.shincolle.equipdata.EquipDefinition;
import com.lulan.shincolle.item.BasicEquip;
import com.lulan.shincolle.reference.Reference;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ModCreativeTabsGameTests {

    private ModCreativeTabsGameTests() {
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void creativeTabHidesUnobtainableButKeepsUnknownVariants(GameTestHelper helper) {
        if (ModCreativeTabs.shouldShowEquipmentVariant(definition(EquipAvailability.UNOBTAINABLE))) {
            throw new AssertionError("Unobtainable definition remained visible in the creative tab");
        }
        if (!ModCreativeTabs.shouldShowEquipmentVariant(definition(EquipAvailability.ANY))) {
            throw new AssertionError("Normal definition was hidden from the creative tab");
        }
        if (!ModCreativeTabs.shouldShowEquipmentVariant(null)) {
            throw new AssertionError("Unknown variant was hidden from the creative tab");
        }

        EquipDefinition airplaneDebug = definition("airplane_debug", 22, EquipAvailability.UNOBTAINABLE);
        EquipDefinition turbineDebug = definition("turbine_debug", 5, EquipAvailability.UNOBTAINABLE);
        EquipDefinition normal = definition("normal", 7, EquipAvailability.ANY);
        EquipDataSnapshot snapshot = new EquipDataSnapshot(Map.of(
                airplaneDebug.id(), airplaneDebug,
                turbineDebug.id(), turbineDebug,
                normal.id(), normal), Map.of(), Map.of());
        Set<Integer> debugVariants = ModCreativeTabs.debugEquipmentStacks(snapshot).stream()
                .map(BasicEquip::getEquipMeta)
                .collect(Collectors.toSet());
        if (!debugVariants.equals(Set.of(5, 22))) {
            throw new AssertionError("Debug tab stacks did not exactly match hidden definitions: " + debugVariants);
        }
        if (!ModCreativeTabs.shouldShowDebugEquipmentVariant(airplaneDebug)
                || ModCreativeTabs.shouldShowDebugEquipmentVariant(normal)
                || ModCreativeTabs.shouldShowDebugEquipmentVariant(null)) {
            throw new AssertionError("Debug tab visibility did not require a known hidden definition");
        }
        helper.succeed();
    }

    private static EquipDefinition definition(EquipAvailability availability) {
        return definition(availability.jsonName(), 0, availability);
    }

    private static EquipDefinition definition(String path, int variant, EquipAvailability availability) {
        return new EquipDefinition(ResourceLocation.fromNamespaceAndPath("creative_test", path),
                ResourceLocation.fromNamespaceAndPath("minecraft", "stick"), variant, 0, null,
                ShipAttributeValues.zero(ShipAttributeLayout.current()), List.of(), 0,
                "grudge", 0, 0, 0, availability);
    }
}
