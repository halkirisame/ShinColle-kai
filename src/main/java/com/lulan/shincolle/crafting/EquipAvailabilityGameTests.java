package com.lulan.shincolle.crafting;

import com.lulan.shincolle.api.attribute.ShipAttributeLayout;
import com.lulan.shincolle.api.attribute.ShipAttributeValues;
import com.lulan.shincolle.equipdata.EquipAvailability;
import com.lulan.shincolle.equipdata.EquipDefinition;
import com.lulan.shincolle.reference.Reference;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class EquipAvailabilityGameTests {

    private EquipAvailabilityGameTests() {
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void shipyardCandidatesRespectAvailability(GameTestHelper helper) {
        List<EquipDefinition> definitions = List.of(
                definition("any", 1, EquipAvailability.ANY),
                definition("shipyard", 1, EquipAvailability.SHIPYARD_ONLY),
                definition("treasure", 1, EquipAvailability.TREASURE_ONLY),
                definition("hidden", 1, EquipAvailability.UNOBTAINABLE),
                definition("other_roll", 2, EquipAvailability.ANY));

        Set<String> candidates = EquipCalc.collectDevelopableCandidates(definitions, 1).stream()
                .map(definition -> definition.id().getPath())
                .collect(Collectors.toSet());
        if (!candidates.equals(Set.of("any", "shipyard"))) {
            throw new AssertionError("Unexpected shipyard candidates: " + candidates);
        }
        helper.succeed();
    }

    private static EquipDefinition definition(String path, int rollType, EquipAvailability availability) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("availability_test", path);
        return new EquipDefinition(id, ResourceLocation.fromNamespaceAndPath("minecraft", "stick"),
                path.hashCode() & Integer.MAX_VALUE, 0, null,
                ShipAttributeValues.zero(ShipAttributeLayout.current()), List.of(), 0,
                "grudge", 0, 1, rollType, availability);
    }
}
