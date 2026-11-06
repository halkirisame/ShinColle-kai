package com.lulan.shincolle.gametest;

import com.lulan.shincolle.ai.domain.combat.WeaponChannel;
import com.lulan.shincolle.ai.registry.ShipAttackType;
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
import java.util.Map;
import java.util.Set;

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
    public static void builtinBehaviorIdsAreRegistered(GameTestHelper helper) {
        ResourceLocation standard = new ResourceLocation(Reference.MOD_ID, "standard");
        helper.assertTrue(ModShipBehaviors.AI_REGISTRY.get().getKeys().equals(Set.of(standard)),
                "The AI registry does not contain exactly the built-in IDs");
        helper.assertTrue(ShipBehaviorRegistries.isSelectableAi(standard), "The standard AI is not selectable");
        helper.assertTrue(ModShipBehaviors.STANDARD.get() == ModShipBehaviors.AI_REGISTRY.get().getValue(standard),
                "The standard AI does not resolve to the registered singleton");
        Map<String, Set<WeaponChannel>> expected = Map.of(
                "light_cannon", Set.of(WeaponChannel.LIGHT), "heavy_cannon", Set.of(WeaponChannel.HEAVY),
                "melee", Set.of(WeaponChannel.MELEE), "aircraft", Set.of(WeaponChannel.AIR),
                "tenryuu_skill", Set.of(), "tatsuta_skill", Set.of(), "nagato_skill", Set.of());
        Set<String> selectable = Set.of("light_cannon", "heavy_cannon", "melee");
        IForgeRegistry<ShipAttackType> registry = ModShipBehaviors.ATTACK_REGISTRY.get();
        helper.assertTrue(registry.getKeys().equals(expected.keySet().stream()
                        .map(path -> new ResourceLocation(Reference.MOD_ID, path)).collect(java.util.stream.Collectors.toSet())),
                "The attack registry does not contain exactly the built-in IDs: " + registry.getKeys());
        expected.forEach((path, channels) -> {
            ResourceLocation id = new ResourceLocation(Reference.MOD_ID, path);
            ShipAttackType attack = registry.getValue(id);
            helper.assertTrue(attack.channels().equals(channels), "Incorrect weapon channels for " + id);
            helper.assertTrue(attack.selectableByDefinition() == selectable.contains(path),
                    "Incorrect definition selectability for " + id);
            helper.assertTrue(ShipBehaviorRegistries.isSelectableAttack(id) == selectable.contains(path),
                    "Incorrect attack selection query for " + id);
            helper.assertTrue(ShipBehaviorRegistries.isRegisteredAttack(id), "Registered attack was not found: " + id);
            helper.assertTrue(!ShipBehaviorRegistries.isSelectableAttack(() -> null, id),
                    "An unavailable registry selected " + id);
            helper.assertTrue(ShipBehaviorRegistries.isSelectableAttack(() -> registry, id) == selectable.contains(path),
                    "The query did not use the supplied registry for " + id);
            try {
                attack.channels().add(WeaponChannel.LIGHT);
                helper.fail("Mutable weapon channels for " + id);
            } catch (UnsupportedOperationException expectedException) {
                // Registry entries must not expose shared mutable state.
            }
        });
        ResourceLocation foreign = new ResourceLocation("some_addon", "light_cannon");
        helper.assertTrue(!ShipBehaviorRegistries.isSelectableAttack(() -> {
            throw new IllegalStateException("Foreign namespaces must be rejected before looking up a registry");
        }, foreign), "A foreign attack ID was selectable");
        helper.succeed();
    }
}
