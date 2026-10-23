package com.lulan.shincolle.gametest;

import com.lulan.shincolle.ai.registry.ShipBehaviorRegistries;
import com.lulan.shincolle.ai.registry.ShipBehaviorRegistryKeys;
import com.lulan.shincolle.init.ModShipBehaviors;
import com.lulan.shincolle.reference.Reference;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistry;
import net.minecraftforge.registries.IForgeRegistry;

import java.util.ArrayList;
import java.util.List;

/** The internal ship AI and attack registries, through the production objects. */
@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ShipBehaviorRegistryGameTests {

    private ShipBehaviorRegistryGameTests() {
    }

    private static void assertFrozenInternalRegistry(GameTestHelper helper, IForgeRegistry<?> registry,
            ResourceLocation expectedId) {
        helper.assertTrue(registry != null, "The registry " + expectedId + " does not exist after startup");
        helper.assertTrue(expectedId.equals(registry.getRegistryName()),
                "The registry has another ID: " + registry.getRegistryName());
        helper.assertTrue(registry instanceof ForgeRegistry<?> forge && forge.isLocked(),
                "The registry " + expectedId + " is not frozen");
        helper.assertTrue(registry.getKeys().stream().allMatch(ShipBehaviorRegistries::isSelectableNamespace),
                "A foreign entry is registered in " + expectedId + ": " + registry.getKeys());
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void shipBehaviorRegistriesExistAndAreFrozen(GameTestHelper helper) {
        assertFrozenInternalRegistry(helper, ModShipBehaviors.AI_REGISTRY.get(), ShipBehaviorRegistryKeys.AI_ID);
        assertFrozenInternalRegistry(helper, ModShipBehaviors.ATTACK_REGISTRY.get(),
                ShipBehaviorRegistryKeys.ATTACK_ID);
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void shipBehaviorQueriesAnswerFalseForUnregisteredIds(GameTestHelper helper) {
        ResourceLocation unregistered = new ResourceLocation(Reference.MOD_ID, "not_registered");
        ResourceLocation foreign = new ResourceLocation("minecraft", "zombie");
        helper.assertTrue(!ShipBehaviorRegistries.isSelectableAi(unregistered),
                "An unregistered AI ID was selectable");
        helper.assertTrue(!ShipBehaviorRegistries.isSelectableAttack(unregistered),
                "An unregistered attack ID was selectable");
        helper.assertTrue(!ShipBehaviorRegistries.isSelectableAi(foreign),
                "An AI ID of another namespace was selectable");
        helper.assertTrue(!ShipBehaviorRegistries.isSelectableAttack(foreign),
                "An attack ID of another namespace was selectable");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void shipBehaviorForeignEntryReportIsEmptyForTheBuiltInRegistries(GameTestHelper helper) {
        List<ResourceLocation> reported = new ArrayList<>();
        int ai = ShipBehaviorRegistries.reportForeign(ModShipBehaviors.AI_REGISTRY, reported::add);
        int attack = ShipBehaviorRegistries.reportForeign(ModShipBehaviors.ATTACK_REGISTRY, reported::add);
        helper.assertTrue(ai == 0 && attack == 0 && reported.isEmpty(),
                "The built-in registries reported foreign entries: " + reported);
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void shipBehaviorForeignEntryCheckRanAfterTheRegistriesWereFrozen(GameTestHelper helper) {
        helper.assertTrue(ShipBehaviorRegistries.foreignCheckRuns() == 1,
                "The foreign-entry check ran " + ShipBehaviorRegistries.foreignCheckRuns() + " times at startup");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void shipBehaviorRegistriesHoldNoBuiltInEntriesYet(GameTestHelper helper) {
        // When the built-in list is added, replace this with a match against the set of known IDs.
        helper.assertTrue(ModShipBehaviors.AI_REGISTRY.get().getKeys().isEmpty(),
                "The AI registry holds entries: " + ModShipBehaviors.AI_REGISTRY.get().getKeys());
        helper.assertTrue(ModShipBehaviors.ATTACK_REGISTRY.get().getKeys().isEmpty(),
                "The attack registry holds entries: " + ModShipBehaviors.ATTACK_REGISTRY.get().getKeys());
        helper.succeed();
    }
}
