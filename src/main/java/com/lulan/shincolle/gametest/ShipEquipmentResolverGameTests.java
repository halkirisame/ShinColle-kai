package com.lulan.shincolle.gametest;

import com.lulan.shincolle.api.attribute.CoreShipAttributes;
import com.lulan.shincolle.api.attribute.ShipAttributeLayout;
import com.lulan.shincolle.api.attribute.ShipAttributeValues;
import com.lulan.shincolle.api.equipment.IShipEquipment;
import com.lulan.shincolle.api.equipment.ResolvedShipEquipment;
import com.lulan.shincolle.api.equipment.ShipAttackEffect;
import com.lulan.shincolle.api.equipment.ShipEquipmentContext;
import com.lulan.shincolle.api.equipment.ShipEquipmentProvider;
import com.lulan.shincolle.api.experimental.equipment.ShipEquipmentHitCallback;
import com.lulan.shincolle.equip.ShipEquipmentProviderIndex;
import com.lulan.shincolle.equip.ShipEquipmentResolver;
import com.lulan.shincolle.equipdata.EquipDataSnapshot;
import com.lulan.shincolle.equipdata.EquipDefinition;
import com.lulan.shincolle.init.ModEquipmentProviders;
import com.lulan.shincolle.reference.Reference;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Pure resolver contract coverage. The normal GameTest registrar must register this class.
 */
@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ShipEquipmentResolverGameTests {

    private static final float EPSILON = 0.00001F;
    private static final ResourceLocation POISON = ResourceLocation.fromNamespaceAndPath("minecraft", "poison");

    private ShipEquipmentResolverGameTests() {
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void resolverMergesJsonAndProviderAndRejectsUnknownStack(GameTestHelper helper) {
        ShipAttributeLayout layout = ShipAttributeLayout.current();
        ItemStack stick = new ItemStack(Items.STICK);
        EquipDefinition definition = definition(layout, Items.STICK, 0,
                values(layout, CoreShipAttributes.ATK_L, 3F), List.of("cannon"));

        ShipEquipmentResolver jsonOnlyResolver = new ShipEquipmentResolver(providers().build());
        ResolvedShipEquipment jsonOnly = jsonOnlyResolver.resolve(stick, snapshot(definition), layout)
                .orElseThrow(() -> new AssertionError("JSON-only non-equipment Item was rejected"));
        assertFloatEquals(3F, jsonOnly.attributes().get(CoreShipAttributes.ATK_L), "JSON-only attack");
        if (jsonOnlyResolver.resolveDynamic(stick, snapshot(definition), layout).isPresent()) {
            throw new AssertionError("JSON-only Item was accepted by the dynamic-only Curios path");
        }

        ShipEquipmentResolver resolver = new ShipEquipmentResolver(providers()
                .add("provider", provider(stack -> stack.is(Items.STICK),
                        values(layout, CoreShipAttributes.ATK_L, 2F), ResolvedShipEquipment.DEFAULT_COMPATIBILITY, 0))
                .build());

        ResolvedShipEquipment merged = resolver.resolve(stick, snapshot(definition), layout)
                .orElseThrow(() -> new AssertionError("JSON + provider stack was rejected"));
        assertFloatEquals(5F, merged.attributes().get(CoreShipAttributes.ATK_L), "merged attack");
        if (!merged.compatibility().equals(Set.of("cannon"))) {
            throw new AssertionError("JSON compatibility was not authoritative: " + merged.compatibility());
        }
        if (resolver.resolveDynamic(stick, snapshot(definition), layout).isEmpty()) {
            throw new AssertionError("JSON + provider Item was rejected by the dynamic Curios path");
        }
        if (resolver.resolve(new ItemStack(Items.DIRT), EquipDataSnapshot.EMPTY, layout).isPresent()) {
            throw new AssertionError("An unrelated Item resolved as ship equipment");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void resolverMergesAttackEffectsWithDynamicPrecedence(GameTestHelper helper) {
        ShipAttributeLayout layout = ShipAttributeLayout.current();
        ShipAttackEffect jsonPoison = new ShipAttackEffect(POISON, 0, 40, 25);
        ShipAttackEffect dynamicPoison = new ShipAttackEffect(POISON, 2, 100, 75);
        EquipDefinition definition = definition(layout, Items.STICK, 0,
                ShipAttributeValues.zero(layout), List.of("cannon"), Map.of(POISON, jsonPoison));

        ShipEquipmentProviderIndex providers = providers().add("effect_provider", new ShipEquipmentProvider() {
            @Override
            public boolean matches(ItemStack stack) {
                return stack.is(Items.STICK);
            }

            @Override
            public ResolvedShipEquipment resolveShipEquipment(ShipEquipmentContext context) {
                return new ResolvedShipEquipment(ShipAttributeValues.zero(layout),
                        ResolvedShipEquipment.DEFAULT_COMPATIBILITY, Map.of(POISON, dynamicPoison));
            }
        }).build();
        ResolvedShipEquipment resolved = new ShipEquipmentResolver(providers)
                .resolve(new ItemStack(Items.STICK), snapshot(definition), layout)
                .orElseThrow(() -> new AssertionError("Effect equipment did not resolve"));
        if (!dynamicPoison.equals(resolved.attackEffects().get(POISON))) {
            throw new AssertionError("Dynamic effect did not override the JSON effect with the same ID");
        }
        assertThrows(UnsupportedOperationException.class,
                () -> resolved.attackEffects().put(POISON, jsonPoison), "immutable attack effect map");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void itemImplementationWinsOverProviderAndProviderErrorsDoNotFallback(GameTestHelper helper) {
        ShipAttributeLayout layout = ShipAttributeLayout.current();
        AtomicInteger providerMatches = new AtomicInteger();
        TestItem item = new TestItem(values(layout, CoreShipAttributes.DEF, 4F));
        ItemStack itemStack = new ItemStack(Items.STICK);
        ShipEquipmentProviderIndex directRegistry = providers().add("should_not_run", new ShipEquipmentProvider() {
            @Override
            public int priority() {
                return 100;
            }

            @Override
            public boolean matches(ItemStack stack) {
                providerMatches.incrementAndGet();
                return true;
            }

            @Override
            public ResolvedShipEquipment resolveShipEquipment(ShipEquipmentContext context) {
                return new ResolvedShipEquipment(values(layout, CoreShipAttributes.DEF, 99F),
                        ResolvedShipEquipment.DEFAULT_COMPATIBILITY);
            }
        }).build();
        EquipDefinition definition = definition(layout, Items.STICK, 0,
                values(layout, CoreShipAttributes.DEF, 3F), List.of("cannon"));
        ResolvedShipEquipment direct = new ShipEquipmentResolver(directRegistry, stack -> item)
                .resolve(itemStack, snapshot(definition), layout)
                .orElseThrow(() -> new AssertionError("canonical Item was rejected"));
        assertFloatEquals(7F, direct.attributes().get(CoreShipAttributes.DEF), "JSON + Item value");
        if (!direct.compatibility().equals(Set.of("cannon"))) {
            throw new AssertionError("JSON did not override Item compatibility");
        }
        if (providerMatches.get() != 0) {
            throw new AssertionError("Provider was evaluated for a canonical Item");
        }

        ShipEquipmentProviderIndex fallbackRegistry = providers()
                .add("first", provider(stack -> true, null, ResolvedShipEquipment.DEFAULT_COMPATIBILITY, 10))
                .add("second", provider(stack -> true, values(layout, CoreShipAttributes.DEF, 8F),
                        ResolvedShipEquipment.DEFAULT_COMPATIBILITY, 0))
                .build();
        if (new ShipEquipmentResolver(fallbackRegistry).resolve(new ItemStack(Items.DIRT), EquipDataSnapshot.EMPTY,
                layout).isPresent()) {
            throw new AssertionError("Lower-priority provider was used after selected provider failed");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void resolverRejectsLayoutMismatchAndMergedOverflow(GameTestHelper helper) {
        ShipAttributeLayout layout = ShipAttributeLayout.current();
        ShipAttributeLayout hpOnly = ShipAttributeLayout.detached(
                Map.of(CoreShipAttributes.HP, layout.type(CoreShipAttributes.HP)));
        ShipEquipmentProviderIndex mismatchProviders = providers()
                .add("wrong_layout", provider(stack -> true, ShipAttributeValues.zero(hpOnly),
                        ResolvedShipEquipment.DEFAULT_COMPATIBILITY, 0))
                .build();
        if (new ShipEquipmentResolver(mismatchProviders)
                .resolve(new ItemStack(Items.STICK), EquipDataSnapshot.EMPTY, layout).isPresent()) {
            throw new AssertionError("A provider using a different attribute layout was accepted");
        }

        ShipAttributeValues maximum = values(layout, CoreShipAttributes.ATK_L, Float.MAX_VALUE);
        ShipEquipmentProviderIndex overflowProviders = providers()
                .add("overflow", provider(stack -> true, maximum, ResolvedShipEquipment.DEFAULT_COMPATIBILITY, 0))
                .build();
        EquipDefinition definition = definition(layout, Items.STICK, 0, maximum, List.of("cannon"));
        if (new ShipEquipmentResolver(overflowProviders)
                .resolve(new ItemStack(Items.STICK), snapshot(definition), layout).isPresent()) {
            throw new AssertionError("A JSON + provider merged overflow was accepted");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void resolverDispatchesOnlySelectedItemHookAndIsolatesFailure(GameTestHelper helper) {
        ShipAttributeLayout layout = ShipAttributeLayout.current();
        LivingEntity ship = EntityType.COW.create(helper.getLevel());
        Entity target = EntityType.PIG.create(helper.getLevel());
        if (ship == null || target == null) {
            throw new AssertionError("Failed to create hook test entities");
        }

        AtomicInteger itemHooks = new AtomicInteger();
        AtomicInteger providerMatches = new AtomicInteger();
        class HookingItem implements IShipEquipment, ShipEquipmentHitCallback {
            @Override
            public ResolvedShipEquipment resolveShipEquipment(ShipEquipmentContext context) {
                return new ResolvedShipEquipment(ShipAttributeValues.zero(layout),
                        ResolvedShipEquipment.DEFAULT_COMPATIBILITY);
            }

            @Override
            public void onShipHit(LivingEntity owner, Entity hitTarget, float amount, ItemStack stack) {
                itemHooks.incrementAndGet();
            }
        }
        IShipEquipment item = new HookingItem();
        ShipEquipmentProviderIndex providers = providers().add("hook_provider", provider(stack -> {
            providerMatches.incrementAndGet();
            return true;
        }, ShipAttributeValues.zero(layout), ResolvedShipEquipment.DEFAULT_COMPATIBILITY, 0)).build();
        ShipEquipmentResolver resolver = new ShipEquipmentResolver(providers, stack -> item);
        resolver.dispatchOnShipHit(ship, target, 5F, new ItemStack(Items.STICK),
                EquipDataSnapshot.EMPTY, layout);
        if (itemHooks.get() != 1 || providerMatches.get() != 0) {
            throw new AssertionError("On-hit source selection was not exactly once");
        }

        class ThrowingItem implements IShipEquipment, ShipEquipmentHitCallback {
            @Override
            public ResolvedShipEquipment resolveShipEquipment(ShipEquipmentContext context) {
                return new ResolvedShipEquipment(ShipAttributeValues.zero(layout),
                        ResolvedShipEquipment.DEFAULT_COMPATIBILITY);
            }

            @Override
            public void onShipHit(LivingEntity owner, Entity hitTarget, float amount, ItemStack stack) {
                throw new IllegalStateException("expected hook failure");
            }
        }
        IShipEquipment throwing = new ThrowingItem();
        new ShipEquipmentResolver(providers().build(), stack -> throwing)
                .dispatchOnShipHit(ship, target, 5F, new ItemStack(Items.STICK),
                        EquipDataSnapshot.EMPTY, layout);
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void providerHitCallbackRunsOnlyWhenProviderImplementsIt(GameTestHelper helper) {
        ShipAttributeLayout layout = ShipAttributeLayout.current();
        LivingEntity ship = EntityType.COW.create(helper.getLevel());
        Entity target = EntityType.PIG.create(helper.getLevel());
        if (ship == null || target == null) {
            throw new AssertionError("Failed to create hook test entities");
        }
        AtomicInteger callbackHits = new AtomicInteger();
        class CallbackProvider implements ShipEquipmentProvider, ShipEquipmentHitCallback {
            @Override
            public boolean matches(ItemStack stack) {
                return true;
            }

            @Override
            public ResolvedShipEquipment resolveShipEquipment(ShipEquipmentContext context) {
                return new ResolvedShipEquipment(ShipAttributeValues.zero(layout),
                        ResolvedShipEquipment.DEFAULT_COMPATIBILITY);
            }

            @Override
            public void onShipHit(LivingEntity owner, Entity hitTarget, float amount, ItemStack stack) {
                callbackHits.incrementAndGet();
            }
        }
        ShipEquipmentProvider plain = provider(stack -> true, ShipAttributeValues.zero(layout),
                ResolvedShipEquipment.DEFAULT_COMPATIBILITY, 0);
        ShipEquipmentResolver withCallback = new ShipEquipmentResolver(
                providers().add("with_callback", new CallbackProvider()).build(), stack -> null);
        ShipEquipmentResolver withoutCallback = new ShipEquipmentResolver(
                providers().add("without_callback", plain).build(), stack -> null);
        ItemStack stick = new ItemStack(Items.STICK);
        withCallback.dispatchOnShipHit(ship, target, 5F, stick, EquipDataSnapshot.EMPTY, layout);
        if (callbackHits.get() != 1) {
            throw new AssertionError("A provider implementing the hit callback was not called exactly once");
        }
        withoutCallback.dispatchOnShipHit(ship, target, 5F, stick, EquipDataSnapshot.EMPTY, layout);
        if (callbackHits.get() != 1) {
            throw new AssertionError("A provider without the hit callback must not be called");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void providerRegistryOrdersSkipsPredicateFailureAndFreezes(GameTestHelper helper) {
        ShipAttributeValues zero = ShipAttributeValues.zero(ShipAttributeLayout.current());
        ShipEquipmentProviderIndex providers = providers()
                .add("crashing", provider(stack -> {
                    throw new IllegalStateException("test predicate failure");
                }, zero, ResolvedShipEquipment.DEFAULT_COMPATIBILITY, 3))
                .add("zulu", provider(stack -> true, zero, ResolvedShipEquipment.DEFAULT_COMPATIBILITY, 2))
                .add("alpha", provider(stack -> true, zero, ResolvedShipEquipment.DEFAULT_COMPATIBILITY, 2))
                .build();
        ResourceLocation first = providers.find(new ItemStack(Items.STICK)).orElseThrow().id();
        if (!first.equals(id("alpha"))) {
            throw new AssertionError("Same-priority providers were not ordered by ID: " + first);
        }
        assertThrows(UnsupportedOperationException.class, () -> providers.ids().add(id("late")),
                "immutable provider order");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void providerIndexOrdersByPriorityDescendingThenId(GameTestHelper helper) {
        ShipAttributeValues zero = ShipAttributeValues.zero(ShipAttributeLayout.current());
        ShipEquipmentProviderIndex providers = providers()
                .add("low", provider(stack -> true, zero, ResolvedShipEquipment.DEFAULT_COMPATIBILITY, -5))
                .add("zulu", provider(stack -> true, zero, ResolvedShipEquipment.DEFAULT_COMPATIBILITY, 0))
                .add("high", provider(stack -> true, zero, ResolvedShipEquipment.DEFAULT_COMPATIBILITY, 7))
                .add("alpha", provider(stack -> true, zero, ResolvedShipEquipment.DEFAULT_COMPATIBILITY, 0))
                .build();
        List<ResourceLocation> expected = List.of(id("high"), id("alpha"), id("zulu"), id("low"));
        if (!expected.equals(providers.ids())) {
            throw new AssertionError("Provider order was " + providers.ids() + " but expected " + expected);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void providerIndexSourceRecognizesProvidersRegisteredAfterAnEarlyLookup(GameTestHelper helper) {
        ShipAttributeValues zero = ShipAttributeValues.zero(ShipAttributeLayout.current());
        Map<ResourceLocation, ShipEquipmentProvider> registered = new LinkedHashMap<>();
        ShipEquipmentProviderIndex.Source source = new ShipEquipmentProviderIndex.Source(
                () -> new LinkedHashMap<>(registered));
        ItemStack stick = new ItemStack(Items.STICK);
        if (source.current().find(stick).isPresent()) {
            throw new AssertionError("An empty provider set matched a stack");
        }
        registered.put(id("late"), provider(stack -> stack.is(Items.STICK), zero,
                ResolvedShipEquipment.DEFAULT_COMPATIBILITY, 0));
        if (source.current().find(stick).isEmpty()) {
            throw new AssertionError("A provider registered after an early lookup was not recognized");
        }
        source.seal();
        registered.put(id("after_seal"), provider(stack -> true, zero,
                ResolvedShipEquipment.DEFAULT_COMPATIBILITY, 5));
        if (!source.current().ids().equals(List.of(id("late")))) {
            throw new AssertionError("The sealed index changed: " + source.current().ids());
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void providerIndexSourceTreatsAMissingRegistryAsEmptyThenRecognizesLaterProviders(
            GameTestHelper helper) {
        ShipAttributeValues zero = ShipAttributeValues.zero(ShipAttributeLayout.current());
        AtomicReference<Map<ResourceLocation, ShipEquipmentProvider>> registry = new AtomicReference<>();
        ShipEquipmentProviderIndex.Source source = new ShipEquipmentProviderIndex.Source(registry::get);
        ItemStack stick = new ItemStack(Items.STICK);
        if (!source.current().ids().isEmpty() || source.current().find(stick).isPresent()) {
            throw new AssertionError("A lookup before the registry exists was not an empty answer");
        }
        registry.set(Map.of(id("created"), provider(stack -> stack.is(Items.STICK), zero,
                ResolvedShipEquipment.DEFAULT_COMPATIBILITY, 0)));
        if (source.current().find(stick).isEmpty()) {
            throw new AssertionError("A provider registered after the early lookup was not recognized");
        }
        source.seal();
        if (!source.current().ids().equals(List.of(id("created")))) {
            throw new AssertionError("The sealed index was wrong: " + source.current().ids());
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void registryConversionIsEmptyBeforeTheRegistryExistsAndCopiesTheRealRegistry(
            GameTestHelper helper) {
        Map<ResourceLocation, ShipEquipmentProvider> beforeCreation =
                ShipEquipmentProviderIndex.registeredProviders(() -> null);
        if (!beforeCreation.isEmpty()) {
            throw new AssertionError("A missing registry did not convert to an empty map: " + beforeCreation);
        }
        Map<ResourceLocation, ShipEquipmentProvider> real =
                ShipEquipmentProviderIndex.registeredProviders(ModEquipmentProviders.REGISTRY);
        ResourceLocation tinkers = ResourceLocation.fromNamespaceAndPath(Reference.MOD_ID, "tconstruct_tools");
        boolean loaded = ModList.get().isLoaded("tconstruct");
        if (real.containsKey(tinkers) != loaded) {
            throw new AssertionError("The converted registry did not follow Tinkers presence (loaded="
                    + loaded + "): " + real.keySet());
        }
        if (real.size() != ModEquipmentProviders.REGISTRY.get().getKeys().size()) {
            throw new AssertionError("The converted registry lost or added entries: " + real.keySet());
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void tinkersProviderIsRegisteredOnlyWhenTinkersIsLoaded(GameTestHelper helper) {
        ResourceLocation tinkers = ResourceLocation.fromNamespaceAndPath(Reference.MOD_ID, "tconstruct_tools");
        boolean loaded = ModList.get().isLoaded("tconstruct");
        if (ModEquipmentProviders.REGISTRY.get().containsKey(tinkers) != loaded) {
            throw new AssertionError("Tinkers provider registration did not follow Tinkers presence (loaded="
                    + loaded + ")");
        }
        if (ShipEquipmentProviderIndex.global().ids().contains(tinkers) != loaded) {
            throw new AssertionError("Global provider index did not follow Tinkers presence (loaded=" + loaded + ")");
        }
        helper.succeed();
    }

    private static EquipDefinition definition(ShipAttributeLayout layout, Item item, int variant,
                                              ShipAttributeValues attributes, List<String> compatibility) {
        return definition(layout, item, variant, attributes, compatibility, Map.of());
    }

    private static EquipDefinition definition(ShipAttributeLayout layout, Item item, int variant,
                                              ShipAttributeValues attributes, List<String> compatibility,
                                              Map<ResourceLocation, ShipAttackEffect> attackEffects) {
        ResourceLocation itemId = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(item);
        if (itemId == null) {
            throw new AssertionError("Test Item is not registered");
        }
        return new EquipDefinition(id("definition"), itemId, variant, 0, null, attributes, attackEffects, compatibility,
                0, "grudge", 0, 0, 0);
    }

    private static EquipDataSnapshot snapshot(EquipDefinition definition) {
        Map<ResourceLocation, EquipDefinition> byId = Map.of(definition.id(), definition);
        Map<Integer, EquipDefinition> variants = Map.of(definition.variant(), definition);
        return new EquipDataSnapshot(byId, Map.of(definition.item(), variants), Map.of());
    }

    private static ProviderSet providers() {
        return new ProviderSet();
    }

    private static ShipEquipmentProvider provider(StackMatcher matcher, ShipAttributeValues attributes,
                                                  Set<String> compatibility, int priority) {
        return new ShipEquipmentProvider() {
            @Override
            public int priority() {
                return priority;
            }

            @Override
            public boolean matches(ItemStack stack) {
                return matcher.matches(stack);
            }

            @Override
            public ResolvedShipEquipment resolveShipEquipment(ShipEquipmentContext context) {
                if (attributes == null) {
                    return null;
                }
                return new ResolvedShipEquipment(attributes, compatibility);
            }
        };
    }

    private static ShipAttributeValues values(ShipAttributeLayout layout, ResourceLocation id, float value) {
        return ShipAttributeValues.builder(layout).set(id, value).build();
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(Reference.MOD_ID, "resolver_test_" + path);
    }

    private static void assertFloatEquals(float expected, float actual, String message) {
        if (Math.abs(expected - actual) > EPSILON) {
            throw new AssertionError(message + ": expected " + expected + " but was " + actual);
        }
    }

    private static void assertThrows(Class<? extends Throwable> expected, Runnable action, String message) {
        try {
            action.run();
        } catch (Throwable error) {
            if (expected.isInstance(error)) {
                return;
            }
            throw new AssertionError(message + ": expected " + expected.getSimpleName() + " but got " + error,
                    error);
        }
        throw new AssertionError(message + ": expected " + expected.getSimpleName() + " but got nothing");
    }

    private static final class TestItem implements IShipEquipment {

        private final ShipAttributeValues attributes;

        private TestItem(ShipAttributeValues attributes) {
            this.attributes = attributes;
        }

        @Override
        public ResolvedShipEquipment resolveShipEquipment(ShipEquipmentContext context) {
            return new ResolvedShipEquipment(this.attributes, ResolvedShipEquipment.DEFAULT_COMPATIBILITY);
        }
    }

    private static final class ProviderSet {

        private final Map<ResourceLocation, ShipEquipmentProvider> providers = new LinkedHashMap<>();

        private ProviderSet add(String path, ShipEquipmentProvider provider) {
            this.providers.put(id(path), provider);
            return this;
        }

        private ShipEquipmentProviderIndex build() {
            return ShipEquipmentProviderIndex.of(this.providers);
        }
    }

    @FunctionalInterface
    private interface StackMatcher {

        boolean matches(ItemStack stack);
    }
}
