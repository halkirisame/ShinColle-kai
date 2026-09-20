package com.lulan.shincolle.api.equipment;

import com.lulan.shincolle.api.attribute.CoreShipAttributes;
import com.lulan.shincolle.api.attribute.ShipAttributeLayout;
import com.lulan.shincolle.api.attribute.ShipAttributeValues;
import com.lulan.shincolle.equipdata.EquipDataSnapshot;
import com.lulan.shincolle.equipdata.EquipDefinition;
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
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

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

        ShipEquipmentResolver jsonOnlyResolver = new ShipEquipmentResolver(ShipEquipmentProviders.detached());
        ResolvedShipEquipment jsonOnly = jsonOnlyResolver.resolve(stick, snapshot(definition), layout)
                .orElseThrow(() -> new AssertionError("JSON-only non-equipment Item was rejected"));
        assertFloatEquals(3F, jsonOnly.attributes().get(CoreShipAttributes.ATK_L), "JSON-only attack");
        if (jsonOnlyResolver.resolveDynamic(stick, snapshot(definition), layout).isPresent()) {
            throw new AssertionError("JSON-only Item was accepted by the dynamic-only Curios path");
        }

        ShipEquipmentProviders.Registry providers = ShipEquipmentProviders.detached();
        providers.register(id("provider"), 0, provider(stack -> stack.is(Items.STICK),
                values(layout, CoreShipAttributes.ATK_L, 2F), ResolvedShipEquipment.DEFAULT_COMPATIBILITY));
        ShipEquipmentResolver resolver = new ShipEquipmentResolver(providers);

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

        ShipEquipmentProviders.Registry providers = ShipEquipmentProviders.detached();
        providers.register(id("effect_provider"), 0, new ShipEquipmentProvider() {
            @Override
            public boolean matches(ItemStack stack) {
                return stack.is(Items.STICK);
            }

            @Override
            public ResolvedShipEquipment resolveShipEquipment(ShipEquipmentContext context) {
                return new ResolvedShipEquipment(ShipAttributeValues.zero(layout),
                        ResolvedShipEquipment.DEFAULT_COMPATIBILITY, Map.of(POISON, dynamicPoison));
            }
        });
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
        ShipEquipmentProviders.Registry directRegistry = ShipEquipmentProviders.detached();
        directRegistry.register(id("should_not_run"), 100, new ShipEquipmentProvider() {
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
        });
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

        ShipEquipmentProviders.Registry fallbackRegistry = ShipEquipmentProviders.detached();
        fallbackRegistry.register(id("first"), 10, provider(stack -> true,
                null, ResolvedShipEquipment.DEFAULT_COMPATIBILITY));
        fallbackRegistry.register(id("second"), 0, provider(stack -> true,
                values(layout, CoreShipAttributes.DEF, 8F), ResolvedShipEquipment.DEFAULT_COMPATIBILITY));
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
        ShipEquipmentProviders.Registry mismatchProviders = ShipEquipmentProviders.detached();
        mismatchProviders.register(id("wrong_layout"), 0, provider(stack -> true,
                ShipAttributeValues.zero(hpOnly), ResolvedShipEquipment.DEFAULT_COMPATIBILITY));
        if (new ShipEquipmentResolver(mismatchProviders)
                .resolve(new ItemStack(Items.STICK), EquipDataSnapshot.EMPTY, layout).isPresent()) {
            throw new AssertionError("A provider using a different attribute layout was accepted");
        }

        ShipAttributeValues maximum = values(layout, CoreShipAttributes.ATK_L, Float.MAX_VALUE);
        ShipEquipmentProviders.Registry overflowProviders = ShipEquipmentProviders.detached();
        overflowProviders.register(id("overflow"), 0,
                provider(stack -> true, maximum, ResolvedShipEquipment.DEFAULT_COMPATIBILITY));
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
        IShipEquipment item = new IShipEquipment() {
            @Override
            public ResolvedShipEquipment resolveShipEquipment(ShipEquipmentContext context) {
                return new ResolvedShipEquipment(ShipAttributeValues.zero(layout),
                        ResolvedShipEquipment.DEFAULT_COMPATIBILITY);
            }

            @Override
            public void onShipHit(LivingEntity owner, Entity hitTarget, float amount, ItemStack stack) {
                itemHooks.incrementAndGet();
            }
        };
        ShipEquipmentProviders.Registry providers = ShipEquipmentProviders.detached();
        providers.register(id("hook_provider"), 0, provider(stack -> {
            providerMatches.incrementAndGet();
            return true;
        }, ShipAttributeValues.zero(layout), ResolvedShipEquipment.DEFAULT_COMPATIBILITY));
        ShipEquipmentResolver resolver = new ShipEquipmentResolver(providers, stack -> item);
        resolver.dispatchOnShipHit(ship, target, 5F, new ItemStack(Items.STICK),
                EquipDataSnapshot.EMPTY, layout);
        if (itemHooks.get() != 1 || providerMatches.get() != 0) {
            throw new AssertionError("On-hit source selection was not exactly once");
        }

        IShipEquipment throwing = new IShipEquipment() {
            @Override
            public ResolvedShipEquipment resolveShipEquipment(ShipEquipmentContext context) {
                return new ResolvedShipEquipment(ShipAttributeValues.zero(layout),
                        ResolvedShipEquipment.DEFAULT_COMPATIBILITY);
            }

            @Override
            public void onShipHit(LivingEntity owner, Entity hitTarget, float amount, ItemStack stack) {
                throw new IllegalStateException("expected hook failure");
            }
        };
        new ShipEquipmentResolver(ShipEquipmentProviders.detached(), stack -> throwing)
                .dispatchOnShipHit(ship, target, 5F, new ItemStack(Items.STICK),
                        EquipDataSnapshot.EMPTY, layout);
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void providerRegistryOrdersSkipsPredicateFailureAndFreezes(GameTestHelper helper) {
        ShipEquipmentProviders.Registry providers = ShipEquipmentProviders.detached();
        providers.register(id("crashing"), 3, provider(stack -> {
            throw new IllegalStateException("test predicate failure");
        }, ShipAttributeValues.zero(ShipAttributeLayout.current()), ResolvedShipEquipment.DEFAULT_COMPATIBILITY));
        providers.register(id("zulu"), 2, provider(stack -> true,
                ShipAttributeValues.zero(ShipAttributeLayout.current()), ResolvedShipEquipment.DEFAULT_COMPATIBILITY));
        providers.register(id("alpha"), 2, provider(stack -> true,
                ShipAttributeValues.zero(ShipAttributeLayout.current()), ResolvedShipEquipment.DEFAULT_COMPATIBILITY));
        ResourceLocation first = providers.find(new ItemStack(Items.STICK)).orElseThrow().id();
        if (!first.equals(id("alpha"))) {
            throw new AssertionError("Same-priority providers were not ordered by ID: " + first);
        }
        providers.freeze();
        assertThrows(IllegalStateException.class, () -> providers.register(id("late"), 0,
                provider(stack -> false, ShipAttributeValues.zero(ShipAttributeLayout.current()),
                        ResolvedShipEquipment.DEFAULT_COMPATIBILITY)), "freeze");
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

    private static ShipEquipmentProvider provider(StackMatcher matcher, ShipAttributeValues attributes,
                                                  Set<String> compatibility) {
        return new ShipEquipmentProvider() {
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

    @FunctionalInterface
    private interface StackMatcher {

        boolean matches(ItemStack stack);
    }
}
