package com.lulan.shincolle.ai.registry;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.RegistryBuilder;
import net.minecraftforge.registries.IForgeRegistry;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShipBehaviorRegistriesTest {

    private static ResourceLocation id(String namespace, String path) {
        return new ResourceLocation(namespace, path);
    }

    /** A registry that contains the given IDs, the way a registry with entries of other mods would. */
    @SuppressWarnings("unchecked")
    private static IForgeRegistry<Object> registryOf(Collection<ResourceLocation> ids) {
        return (IForgeRegistry<Object>) Proxy.newProxyInstance(
                ShipBehaviorRegistriesTest.class.getClassLoader(), new Class<?>[]{IForgeRegistry.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "containsKey" -> ids.contains(args[0]);
                    case "getKeys" -> Set.copyOf(ids);
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    @Test
    void onlyTheBuiltInNamespaceIsSelectable() {
        assertTrue(ShipBehaviorRegistries.isSelectableNamespace(id("shincolle_kai", "anything")));
        assertFalse(ShipBehaviorRegistries.isSelectableNamespace(id("minecraft", "anything")));
        assertFalse(ShipBehaviorRegistries.isSelectableNamespace(id("some_addon", "anything")));
        assertFalse(ShipBehaviorRegistries.isSelectableNamespace(id("shincolle", "anything")));
    }

    @Test
    void aRegisteredEntryOfAnotherNamespaceIsNotSelectable() {
        ResourceLocation builtIn = id("shincolle_kai", "basic");
        ResourceLocation foreign = id("some_addon", "basic");
        IForgeRegistry<Object> registry = registryOf(List.of(builtIn, foreign));
        assertTrue(ShipBehaviorRegistries.isSelectable(() -> registry, builtIn));
        assertFalse(ShipBehaviorRegistries.isSelectable(() -> registry, foreign));
        assertFalse(ShipBehaviorRegistries.isSelectable(() -> registry, id("shincolle_kai", "missing")));
    }

    @Test
    void aRegistryThatDoesNotExistYetSelectsNothingAndReportsNothing() {
        assertFalse(ShipBehaviorRegistries.isSelectable(() -> null, id("shincolle_kai", "basic")));
        List<ResourceLocation> reported = new ArrayList<>();
        assertEquals(0, ShipBehaviorRegistries.reportForeign(() -> null, reported::add));
        assertTrue(reported.isEmpty());
    }

    @Test
    void foreignEntriesAreReportedOncePerIdAndTheBuiltInOnesNever() {
        IForgeRegistry<Object> registry = registryOf(List.of(
                id("shincolle_kai", "basic"), id("some_addon", "b"), id("some_addon", "a"), id("other", "a")));
        List<ResourceLocation> reported = new ArrayList<>();
        assertEquals(3, ShipBehaviorRegistries.reportForeign(() -> registry, reported::add));
        assertEquals(List.of(id("other", "a"), id("some_addon", "a"), id("some_addon", "b")), reported);
        assertEquals(List.of(id("some_addon", "a")),
                ShipBehaviorRegistries.foreignIds(List.of(id("some_addon", "a"), id("some_addon", "a"))));
    }

    /** A logger that records the formatted ERROR lines and fails on anything else it is asked to do. */
    private static Logger recordingLogger(List<String> errors) {
        return (Logger) Proxy.newProxyInstance(
                ShipBehaviorRegistriesTest.class.getClassLoader(), new Class<?>[]{Logger.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("error") && args.length == 3) {
                        errors.add(args[0] + "|" + args[1] + "|" + args[2]);
                        return null;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    @Test
    void theStartupCheckLogsOneErrorPerForeignIdOfEachRegistryAndNothingForTheBuiltInOnes() {
        IForgeRegistry<Object> ai = registryOf(List.of(
                id("shincolle_kai", "basic"), id("some_addon", "a"), id("other", "b")));
        IForgeRegistry<Object> attack = registryOf(List.of(id("shincolle_kai", "shell"), id("some_addon", "x")));
        List<String> errors = new ArrayList<>();
        int before = ShipBehaviorRegistries.foreignCheckRuns();
        ShipBehaviorRegistries.logForeignEntries(recordingLogger(errors), () -> ai, () -> attack);
        assertEquals(before + 1, ShipBehaviorRegistries.foreignCheckRuns());
        assertEquals(3, errors.size());
        assertTrue(errors.stream().filter(e -> e.startsWith("Ship AI")).count() == 2);
        assertTrue(errors.stream().anyMatch(e -> e.startsWith("Ship attack") && e.contains("|some_addon:x|")));
        assertTrue(errors.stream().noneMatch(e -> e.contains("shincolle_kai:")));
    }

    @Test
    void theStartupCheckLogsNothingForRegistriesThatHoldOnlyBuiltInEntriesOrDoNotExist() {
        IForgeRegistry<Object> builtIn = registryOf(List.of(id("shincolle_kai", "basic")));
        List<String> errors = new ArrayList<>();
        ShipBehaviorRegistries.logForeignEntries(recordingLogger(errors), () -> builtIn, () -> null);
        assertTrue(errors.isEmpty(), errors.toString());
    }

    @Test
    void bothInternalRegistriesAreBuiltUnsavedUnsyncedAndWithoutOverrides() {
        RegistryBuilder<Object> builder = ShipBehaviorRegistryBuilders.internal();
        assertFalse(builder.getSaveToDisc(), "disableSaving was dropped");
        assertFalse(builder.getSync(), "disableSync was dropped");
        assertFalse(builder.getAllowOverrides(), "disableOverrides was dropped");
    }
}
