package com.lulan.shincolle.loot;

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

    private static final ResourceLocation ITEM = ResourceLocation.fromNamespaceAndPath("minecraft", "stick");

    private EquipAvailabilityGameTests() {
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void treasureCandidatesRespectAvailability(GameTestHelper helper) {
        List<EquipDefinition> definitions = List.of(
                definition("any", ITEM, EquipAvailability.ANY),
                definition("shipyard", ITEM, EquipAvailability.SHIPYARD_ONLY),
                definition("treasure", ITEM, EquipAvailability.TREASURE_ONLY),
                definition("hidden", ITEM, EquipAvailability.UNOBTAINABLE),
                definition("other_item", ResourceLocation.fromNamespaceAndPath("minecraft", "stone"),
                        EquipAvailability.ANY));

        Set<String> candidates = InjectLootTableModifier.collectLootCandidates(definitions, ITEM).stream()
                .map(definition -> definition.id().getPath())
                .collect(Collectors.toSet());
        if (!candidates.equals(Set.of("any", "treasure"))) {
            throw new AssertionError("Unexpected treasure candidates: " + candidates);
        }
        helper.succeed();
    }

    private static EquipDefinition definition(String path, ResourceLocation item, EquipAvailability availability) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("availability_test", path);
        return new EquipDefinition(id, item, path.hashCode() & Integer.MAX_VALUE, 0, null,
                ShipAttributeValues.zero(ShipAttributeLayout.current()), List.of(), 0,
                "grudge", 0, 1, 1, availability);
    }
}
