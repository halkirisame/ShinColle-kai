package com.lulan.shincolle.init;

import com.lulan.shincolle.api.attribute.ShipAttributeLayout;
import com.lulan.shincolle.api.attribute.ShipAttributeValues;
import com.lulan.shincolle.equipdata.EquipAvailability;
import com.lulan.shincolle.equipdata.EquipDataSnapshot;
import com.lulan.shincolle.equipdata.EquipDefinition;
import com.lulan.shincolle.item.BasicEquip;
import com.lulan.shincolle.reference.Reference;
import com.lulan.shincolle.shipdata.ShipDefinition;
import com.lulan.shincolle.shipdata.ShipPresentation;
import com.lulan.shincolle.shipdata.ShipPresentationSnapshot;
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
    public static void genericCreativeEggsMatchEverySyncedIdInLexicalOrder(GameTestHelper helper) {
        var view = new ShipPresentation("Sample", ResourceLocation.parse("addon:visual"),
                new ShipDefinition.Dimensions(1, 2), ResourceLocation.parse("shincolle_kai:destroyer"), 0, 0);
        var snapshot = new ShipPresentationSnapshot(4, Map.of(
                "z_addon:alpha", view, "a_addon:zulu", view, "a_addon:alpha", view));
        var eggs = ModCreativeTabs.genericShipEggStacks(snapshot);
        var expected = List.of("a_addon:alpha", "a_addon:zulu", "z_addon:alpha");
        helper.assertTrue(eggs.size() == snapshot.definitions().size(), "One egg per valid definition");
        for (int i = 0; i < eggs.size(); i++) {
            var egg = eggs.get(i);
            helper.assertTrue(egg.is(ModItems.GENERIC_SHIP_SPAWN_EGG.get()) && egg.getCount() == 1,
                    "Each entry must be one generic egg");
            helper.assertTrue(egg.hasTag() && expected.get(i).equals(egg.getTag().getString("ShipId")),
                    "Every egg must have its definition ID, sorted lexically");
            helper.assertTrue(egg.getTag().contains("Hostile") && !egg.getTag().getBoolean("Hostile"),
                    "No hostile eggs may appear");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void genericCreativeEggsAreEmptyBeforeDefinitionsArrive(GameTestHelper helper) {
        helper.assertTrue(ModCreativeTabs.genericShipEggStacks(new ShipPresentationSnapshot(-1, Map.of())).isEmpty(),
                "An empty client snapshot must not expose a plain egg");
        helper.succeed();
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
