package com.lulan.shincolle.api.equipment;

import com.lulan.shincolle.api.attribute.ShipAttributeLayout;
import com.lulan.shincolle.api.attribute.ShipAttributeValues;
import com.lulan.shincolle.crafting.EquipCalc;
import com.lulan.shincolle.equipdata.EquipDataRegistry;
import com.lulan.shincolle.equipdata.EquipDataSnapshot;
import com.lulan.shincolle.equipdata.EquipDefinition;
import com.lulan.shincolle.utility.EnchantHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Resolves datapack, Item, and provider equipment contributions exactly once per stack.
 *
 * <p>Server game logic must use {@link #resolveServer(ItemStack)} and client display/prediction
 * must use {@link #resolveClient(ItemStack)}. Both are explicit about their separate snapshots;
 * callers that already own a snapshot can use {@link #resolve(ItemStack, EquipDataSnapshot,
 * ShipAttributeLayout)}.</p>
 */
public final class ShipEquipmentResolver {

    public static final String EQUIP_META_TAG = "EquipMeta";

    private static final Logger LOGGER = LoggerFactory.getLogger(ShipEquipmentResolver.class);
    private static final Set<String> REPORTED_FAILURES = ConcurrentHashMap.newKeySet();
    private static final ShipEquipmentResolver GLOBAL = new ShipEquipmentResolver(ShipEquipmentProviders.global());

    private final ShipEquipmentProviders.Registry providers;
    private final Function<ItemStack, IShipEquipment> directItemLookup;

    /** Creates a resolver backed by the frozen-at-load-complete global provider registry. */
    public ShipEquipmentResolver() {
        this(ShipEquipmentProviders.global());
    }

    /** Creates a resolver backed by an isolated registry, intended for tests or controlled integrations. */
    public ShipEquipmentResolver(ShipEquipmentProviders.Registry providers) {
        this(providers, stack -> stack.getItem() instanceof IShipEquipment equipment ? equipment : null);
    }

    ShipEquipmentResolver(ShipEquipmentProviders.Registry providers,
                          Function<ItemStack, IShipEquipment> directItemLookup) {
        this.providers = Objects.requireNonNull(providers, "providers");
        this.directItemLookup = Objects.requireNonNull(directItemLookup, "directItemLookup");
    }

    /** Resolves with the authoritative datapack snapshot. */
    public static Optional<ResolvedShipEquipment> resolveServer(ItemStack stack) {
        return GLOBAL.resolve(stack, EquipDataRegistry.server(), ShipAttributeLayout.current());
    }

    /** Resolves with the separately synchronized display-only client snapshot. */
    public static Optional<ResolvedShipEquipment> resolveClient(ItemStack stack) {
        return GLOBAL.resolve(stack, EquipDataRegistry.client(), ShipAttributeLayout.current());
    }

    /**
     * Resolves one stack against an explicit snapshot and canonical target layout.
     * A malformed or throwing source rejects the entire stack, never a partial JSON contribution.
     */
    public Optional<ResolvedShipEquipment> resolve(ItemStack stack, EquipDataSnapshot snapshot,
                                                    ShipAttributeLayout layout) {
        return resolveInternal(stack, snapshot, layout, false).map(Resolution::equipment);
    }

    /** Explicit-snapshot dynamic-only resolution used by Curios integrations and tests. */
    public Optional<ResolvedShipEquipment> resolveDynamic(ItemStack stack, EquipDataSnapshot snapshot,
                                                           ShipAttributeLayout layout) {
        return resolveInternal(stack, snapshot, layout, true).map(Resolution::equipment);
    }

    /**
     * Resolves a Curios/addon equipment stack on the authoritative server.
     * JSON-only equipment is deliberately rejected, while a dynamic stack
     * that also has JSON data receives both contributions.
     */
    public static Optional<ResolvedShipEquipment> resolveDynamicServer(ItemStack stack) {
        return GLOBAL.resolveInternal(stack, EquipDataRegistry.server(), ShipAttributeLayout.current(), true)
                .map(Resolution::equipment);
    }

    /** Client-side equivalent of {@link #resolveDynamicServer(ItemStack)}. */
    public static Optional<ResolvedShipEquipment> resolveDynamicClient(ItemStack stack) {
        return GLOBAL.resolveInternal(stack, EquipDataRegistry.client(), ShipAttributeLayout.current(), true)
                .map(Resolution::equipment);
    }

    /**
     * Cheap side-safe recognition used while Curios attaches capabilities.
     * It intentionally does not require the attribute layout to have been
     * initialized yet and does not execute the source's resolver.
     */
    public static boolean hasDynamicSource(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        return GLOBAL.findDynamicSource(stack) != null;
    }

    /** Returns whether a stack resolves through JSON, an Item implementation, or a provider on the server. */
    public static boolean canResolveServer(ItemStack stack) {
        return resolveServer(stack).isPresent();
    }

    /** Returns whether a stack resolves through JSON, an Item implementation, or a provider on the client. */
    public static boolean canResolveClient(ItemStack stack) {
        return resolveClient(stack).isPresent();
    }

    /**
     * Invokes only the Item or provider hook selected by the same successful resolution path.
     * A hook failure is isolated to this stack and does not affect the completed attack.
     */
    public static void dispatchServerOnShipHit(LivingEntity ship, Entity target, float attackAmount,
                                               ItemStack stack) {
        GLOBAL.dispatchOnShipHit(ship, target, attackAmount, stack, EquipDataRegistry.server(),
                ShipAttributeLayout.current());
    }

    public void dispatchOnShipHit(LivingEntity ship, Entity target, float attackAmount, ItemStack stack,
                                  EquipDataSnapshot snapshot, ShipAttributeLayout layout) {
        Objects.requireNonNull(ship, "ship");
        Objects.requireNonNull(target, "target");
        Optional<Resolution> result = resolveInternal(stack, snapshot, layout, false);
        if (result.isEmpty() || result.get().hook() == null) {
            return;
        }
        try {
            result.get().hook().onHit(ship, target, attackAmount, stack.copy());
        } catch (RuntimeException error) {
            reportFailure("on-hit", itemId(stack), equipMeta(stack), result.get().equipment().providerId().orElse(null),
                    error);
        }
    }

    private Optional<Resolution> resolveInternal(ItemStack stack, EquipDataSnapshot snapshot,
                                                 ShipAttributeLayout layout, boolean requireDynamic) {
        Objects.requireNonNull(stack, "stack");
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(layout, "layout");
        if (stack.isEmpty()) {
            return Optional.empty();
        }

        ResourceLocation itemId = itemId(stack);
        int variant = equipMeta(stack);
        EquipDefinition definition = itemId == null ? null : snapshot.byItemVariant(itemId, variant);
        ShipAttributeValues jsonValues;
        try {
            jsonValues = definition == null ? ShipAttributeValues.zero(layout)
                    : resolveDefinitionValues(definition, stack, layout);
        } catch (RuntimeException error) {
            reportFailure("definition", itemId, variant, null, error);
            return Optional.empty();
        }

        DynamicSource dynamic;
        try {
            dynamic = findDynamicSource(stack);
        } catch (RuntimeException error) {
            reportFailure("dynamic", itemId, variant, null, error);
            return Optional.empty();
        }
        if ((definition == null && dynamic == null) || (requireDynamic && dynamic == null)) {
            return Optional.empty();
        }

        ShipAttributeValues dynamicValues = ShipAttributeValues.zero(layout);
        Map<ResourceLocation, ShipAttackEffect> attackEffects = definition == null
                ? Map.of() : definition.attackEffects();
        Set<String> compatibility = definition == null
                ? ResolvedShipEquipment.DEFAULT_COMPATIBILITY
                : new LinkedHashSet<>(definition.compatible());
        ResourceLocation providerId = null;
        Hook hook = null;
        if (dynamic != null) {
            ResolvedShipEquipment resolvedDynamic;
            try {
                resolvedDynamic = Objects.requireNonNull(dynamic.resolve(new ShipEquipmentContext(stack, layout)),
                        "Ship equipment source returned null");
                dynamicValues = rebase(resolvedDynamic.attributes(), layout);
                attackEffects = mergeAttackEffects(attackEffects, resolvedDynamic.attackEffects());
            } catch (RuntimeException error) {
                reportFailure("dynamic resolve", itemId, variant, dynamic.providerId(), error);
                return Optional.empty();
            }
            if (definition == null) {
                compatibility = resolvedDynamic.compatibility();
            }
            providerId = dynamic.providerId();
            hook = dynamic.hook();
        }

        try {
            ShipAttributeValues merged = merge(jsonValues, dynamicValues, layout);
            ResolvedShipEquipment equipment = new ResolvedShipEquipment(merged, compatibility, attackEffects,
                    definition == null ? null : definition.id(), providerId);
            return Optional.of(new Resolution(equipment, hook));
        } catch (RuntimeException error) {
            reportFailure("merge", itemId, variant, providerId, error);
            return Optional.empty();
        }
    }

    private DynamicSource findDynamicSource(ItemStack stack) {
        IShipEquipment itemEquipment = this.directItemLookup.apply(stack.copy());
        if (itemEquipment != null) {
            return new DynamicSource(null, itemEquipment::resolveShipEquipment, itemEquipment::onShipHit);
        }
        return this.providers.find(stack).<DynamicSource>map(match -> new DynamicSource(match.id(),
                match.provider()::resolveShipEquipment, match.provider()::onShipHit)).orElse(null);
    }

    private static ShipAttributeValues resolveDefinitionValues(EquipDefinition definition, ItemStack stack,
                                                               ShipAttributeLayout targetLayout) {
        ShipAttributeValues enchanted = EquipCalc.calcEquipStatWithEnchant(definition.enchantType(),
                definition.stats(), EnchantHelper.calcEnchantEffect(stack));
        return rebase(enchanted, targetLayout);
    }

    private static ShipAttributeValues merge(ShipAttributeValues first, ShipAttributeValues second,
                                             ShipAttributeLayout targetLayout) {
        ShipAttributeValues firstRebased = rebase(first, targetLayout);
        ShipAttributeValues secondRebased = rebase(second, targetLayout);
        ShipAttributeValues.Builder result = ShipAttributeValues.builder(targetLayout);
        for (ResourceLocation id : targetLayout.ids()) {
            result.set(id, firstRebased.get(id));
            result.add(id, secondRebased.get(id));
        }
        return result.build();
    }

    private static Map<ResourceLocation, ShipAttackEffect> mergeAttackEffects(
            Map<ResourceLocation, ShipAttackEffect> definitionEffects,
            Map<ResourceLocation, ShipAttackEffect> dynamicEffects) {
        Map<ResourceLocation, ShipAttackEffect> merged = new LinkedHashMap<>(definitionEffects);
        merged.putAll(dynamicEffects);
        return Map.copyOf(merged);
    }

    /**
     * Rebuilds values on one layout after requiring exactly the same attribute identifiers.
     * Layout instances may differ between a synchronized client snapshot and local registry,
     * but a missing or extra ID is a rejected whole-stack mismatch.
     */
    private static ShipAttributeValues rebase(ShipAttributeValues values, ShipAttributeLayout targetLayout) {
        if (!values.layout().ids().equals(targetLayout.ids())) {
            throw new IllegalArgumentException("Ship equipment values use a different attribute layout");
        }
        ShipAttributeValues.Builder result = ShipAttributeValues.builder(targetLayout);
        for (ResourceLocation id : targetLayout.ids()) {
            result.set(id, values.get(id));
        }
        return result.build();
    }

    private static ResourceLocation itemId(ItemStack stack) {
        return stack.isEmpty() ? null : ForgeRegistries.ITEMS.getKey(stack.getItem());
    }

    private static int equipMeta(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.contains(EQUIP_META_TAG) ? tag.getInt(EQUIP_META_TAG) : 0;
    }

    private static void reportFailure(String stage, ResourceLocation itemId, int variant,
                                      ResourceLocation providerId, RuntimeException error) {
        String key = stage + '|' + itemId + '|' + variant + '|' + providerId;
        if (REPORTED_FAILURES.add(key)) {
            LOGGER.warn("Ship equipment {} failed for item {} variant {} provider {}; rejecting this stack",
                    stage, itemId, variant, providerId, error);
        }
    }

    private record Resolution(ResolvedShipEquipment equipment, Hook hook) {
    }

    private record DynamicSource(ResourceLocation providerId, DynamicResolver resolver, Hook hook) {

        private ResolvedShipEquipment resolve(ShipEquipmentContext context) {
            return this.resolver.resolve(context);
        }
    }

    @FunctionalInterface
    private interface DynamicResolver {

        ResolvedShipEquipment resolve(ShipEquipmentContext context);
    }

    @FunctionalInterface
    private interface Hook {

        void onHit(LivingEntity ship, Entity target, float attackAmount, ItemStack stack);
    }
}
