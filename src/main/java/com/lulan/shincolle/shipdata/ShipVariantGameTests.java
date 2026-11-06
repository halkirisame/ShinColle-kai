package com.lulan.shincolle.shipdata;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.lulan.shincolle.ai.registry.ShipTraitRegistries;
import com.lulan.shincolle.ai.registry.ShipTraitType;
import com.lulan.shincolle.init.ModShipBehaviors;
import com.lulan.shincolle.reference.Reference;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.profiling.InactiveProfiler;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.Map;
import java.util.Set;

@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ShipVariantGameTests {
    private ShipVariantGameTests() {
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void builtInTraitsHaveFixedIdsAndStatMetadata(GameTestHelper helper) {
        var registry = ModShipBehaviors.TRAIT_REGISTRY.get();
        helper.assertTrue(registry != null, "Trait registry must be registered");
        Map<String, ShipTraitType.Attribute> expected = Map.of(
                "sturdy", ShipTraitType.Attribute.HP, "heavy_hitter", ShipTraitType.Attribute.ATK,
                "armored", ShipTraitType.Attribute.DEF, "quick_loader", ShipTraitType.Attribute.SPD,
                "swift", ShipTraitType.Attribute.MOV, "keen_eye", ShipTraitType.Attribute.HIT);
        Set<ResourceLocation> actual = new java.util.HashSet<>();
        registry.getKeys().stream().filter(ShipTraitRegistries::isSelectableTrait).forEach(actual::add);
        helper.assertTrue(actual.size() == expected.size(), "There must be exactly six selectable built-in traits");
        expected.forEach((name, attribute) -> {
            ResourceLocation id = new ResourceLocation(Reference.MOD_ID, name);
            helper.assertTrue(ShipTraitRegistries.isSelectableTrait(id), "Missing selectable trait " + id);
            ShipTraitType trait = registry.getValue(id);
            helper.assertTrue(trait.attribute() == attribute && trait.multiplier() == 1.1, "Wrong metadata for " + id);
            helper.assertTrue(trait == registry.getValue(id), "Registry entries must be singletons");
        });
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void traitReferencesAndVariantReloadUseRealRegistry(GameTestHelper helper) {
        ShipDefinitionId id = ShipDefinitionId.parse("shincolle_test:variant_fixture");
        ShipDefinitionId bad = ShipDefinitionId.parse("shincolle_test:bad_variant_fixture");
        ShipDefinitionRegistry registry = new ShipDefinitionRegistry();
        ShipDefinitionLoader loader = new ShipDefinitionLoader(new ShipDefinitionValidator(), registry);
        JsonElement first = definition("flagship", "sturdy");
        loader.apply(Map.of(id.location(), first, bad.location(), definition("bad", "missing")), null, InactiveProfiler.INSTANCE);
        ShipDefinitionSnapshot old = registry.snapshot();
        helper.assertTrue(old.generation() == 1 && old.all().size() == 1, "Only the invalid definition must be rejected");
        helper.assertTrue(old.getVariant(id, "flagship") != null, "First generation must resolve variant by ID");
        loader.apply(Map.of(id.location(), definition("elite", "swift")), null, InactiveProfiler.INSTANCE);
        ShipDefinitionSnapshot current = registry.snapshot();
        helper.assertTrue(current.generation() == 2 && current.getVariant(id, "elite") != null, "Reload must add new variant");
        helper.assertTrue(current.getVariant(id, "flagship") == null && old.getVariant(id, "elite") == null,
                "Variant generations must not mix");
        helper.assertTrue(old.getVariant(id, "flagship") != null, "Captured old snapshot must remain immutable");
        helper.succeed();
    }

    private static JsonElement definition(String variant, String trait) {
        return JsonParser.parseString("""
                {"schema":1,"ship_type":"shincolle_kai:destroyer","visual":"shincolle_test:visual","base_stats":{},
                "hostile_variants":{"variants":[{"id":"%s","weight":1,"traits":["shincolle_kai:%s"]}]}}
                """.formatted(variant, trait));
    }
}
