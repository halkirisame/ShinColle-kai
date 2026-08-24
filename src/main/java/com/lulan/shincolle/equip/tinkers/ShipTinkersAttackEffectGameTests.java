package com.lulan.shincolle.equip.tinkers;

import com.lulan.shincolle.api.equipment.ShipAttackEffect;
import com.lulan.shincolle.reference.Reference;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;

/** Regression coverage for Tinkers modifier conversion into the canonical Public API value. */
@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ShipTinkersAttackEffectGameTests {

    private ShipTinkersAttackEffectGameTests() {
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void modifierMappingReturnsPublicValuesAndKeepsStrongest(GameTestHelper helper) {
        Map<ResourceLocation, ShipAttackEffect> effects = new LinkedHashMap<>();
        ShipTinkersIntegration.mergeModifierEffect(effects, id("tconstruct", "venom"), 1);
        ShipTinkersIntegration.mergeModifierEffect(effects, id("tconstruct", "venom"), 3);
        ShipAttackEffect poison = effects.get(id("minecraft", "poison"));
        if (poison == null || poison.amplifier() != 2 || poison.durationTicks() != 80
                || poison.chancePercent() != 55) {
            throw new AssertionError("Tinkers venom did not produce the strongest canonical poison value");
        }
        ShipTinkersIntegration.mergeModifierEffect(effects, id("tconstruct", "unknown_modifier"), 99);
        if (effects.size() != 1) {
            throw new AssertionError("Unknown Tinkers modifier guessed an attack effect");
        }
        helper.succeed();
    }

    private static ResourceLocation id(String namespace, String path) {
        return ResourceLocation.fromNamespaceAndPath(namespace, path);
    }
}
