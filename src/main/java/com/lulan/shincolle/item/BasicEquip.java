package com.lulan.shincolle.item;

import com.lulan.shincolle.ShinColle;
import com.lulan.shincolle.api.attribute.ShipAttributeLayout;
import com.lulan.shincolle.api.attribute.ShipAttributeValues;
import com.lulan.shincolle.crafting.EquipCalc;
import com.lulan.shincolle.equipdata.EquipDataRegistry;
import com.lulan.shincolle.equipdata.EquipDefinition;
import com.lulan.shincolle.equipdata.EquipTier;
import com.lulan.shincolle.reference.Enums.EnumEquipEffectSP;
import com.lulan.shincolle.reference.unitclass.ResourceAmount;
import com.lulan.shincolle.utility.ClientRuntimeHelper;
import com.lulan.shincolle.utility.EnchantHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.locale.Language;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraftforge.registries.ForgeRegistries;

/**
 * Base class for ship equipment items.
 * Equipment items have max stack size of 1 and use NBT for variant data.
 * In 1.10.2, variants were stored as item damage/meta values.
 * In 1.20.1, we store the variant in NBT tag "EquipMeta".
 */
public abstract class BasicEquip extends BasicItem implements IShipResourceItem {

    public static final String TAG_EQUIP_META = "EquipMeta";

    protected static final Random itemRand = new Random();
    private static final Set<String> REPORTED_MISSING_DEFINITIONS = ConcurrentHashMap.newKeySet();
    private static final Set<String> REPORTED_INVALID_TOOLTIPS = ConcurrentHashMap.newKeySet();

    private final int numVariants;

    public BasicEquip(int numVariants) {
        super(new Properties().stacksTo(1));
        this.numVariants = numVariants;
    }

    /**
     * Get the equipment meta/variant from an ItemStack's NBT
     */
    public static int getEquipMeta(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains(TAG_EQUIP_META)) {
            return tag.getInt(TAG_EQUIP_META);
        }
        return 0;
    }

    /**
     * Set the equipment meta/variant on an ItemStack's NBT
     */
    public static void setEquipMeta(ItemStack stack, int meta) {
        stack.getOrCreateTag().putInt(TAG_EQUIP_META, meta);
    }

    /**
     * Get the number of equipment variants
     */
    public int getNumVariants() {
        return numVariants;
    }

    /** Resolve a stack through the authoritative server item/variant index. */
    public static EquipDefinition getServerDefinition(ItemStack stack) {
        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(stack.getItem());
        EquipDefinition definition = itemId == null ? null
                : EquipDataRegistry.server().byItemVariant(itemId, getEquipMeta(stack));
        if (definition == null) {
            reportMissingDefinition(itemId, getEquipMeta(stack));
        }
        return definition;
    }

    /**
     * Resolve a stack through whichever snapshot has it, without the missing-definition log.
     *
     * <p>For hot paths such as {@link #getRarity(ItemStack)}, which runs on every name render.</p>
     */
    private static EquipDefinition lookupDefinitionQuietly(ItemStack stack) {
        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (itemId == null) {
            return null;
        }
        int variant = getEquipMeta(stack);
        EquipDefinition definition = EquipDataRegistry.client().byItemVariant(itemId, variant);
        return definition != null ? definition : EquipDataRegistry.server().byItemVariant(itemId, variant);
    }

    /**
     * Colors the equipment name by its development tier: white, yellow, aqua, light purple.
     *
     * <p>Unlike vanilla, an enchanted copy is <em>not</em> promoted a step. The color states the
     * grade of the equipment itself; what a particular copy rolled is what the stat lines are
     * for. Without this, a starter cannon with a development enchantment would read as the same
     * grade as a late-game one.</p>
     */
    @Override
    public Rarity getRarity(ItemStack stack) {
        EquipDefinition definition = lookupDefinitionQuietly(stack);
        if (definition == null) {
            return super.getRarity(stack);
        }
        return switch (EquipTier.of(definition)) {
            case BASIC -> Rarity.COMMON;
            case IMPROVED -> Rarity.UNCOMMON;
            case ADVANCED -> Rarity.RARE;
            case ELITE -> Rarity.EPIC;
        };
    }

    /** Resolve a stack through the display-only client item/variant index. */
    public static EquipDefinition getClientDefinition(ItemStack stack) {
        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(stack.getItem());
        EquipDefinition definition = itemId == null ? null
                : EquipDataRegistry.client().byItemVariant(itemId, getEquipMeta(stack));
        if (definition == null) {
            reportMissingDefinition(itemId, getEquipMeta(stack));
        }
        return definition;
    }

    protected EquipDefinition getServerDefinition(int variant) {
        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(this);
        EquipDefinition definition = itemId == null ? null
                : EquipDataRegistry.server().byItemVariant(itemId, variant);
        if (definition == null) {
            reportMissingDefinition(itemId, variant);
        }
        return definition;
    }

    protected int getEquipType(ItemStack stack) {
        EquipDefinition definition = getServerDefinition(stack);
        return definition == null ? -1 : definition.equipType();
    }

    protected int getEquipType(int variant) {
        EquipDefinition definition = getServerDefinition(variant);
        return definition == null ? -1 : definition.equipType();
    }

    private static void reportMissingDefinition(ResourceLocation itemId, int variant) {
        String key = String.valueOf(itemId) + '#' + variant;
        if (REPORTED_MISSING_DEFINITIONS.add(key)) {
            ShinColle.LOGGER.warn("No ship equipment definition for item {} variant {}; using safe defaults",
                    itemId, variant);
        }
    }

    /**
     * Equip special effect
     */
    public EnumEquipEffectSP getSpecialEffect(ItemStack stack) {
        return EnumEquipEffectSP.NONE;
    }

    /**
     * Get the texture icon index for a synchronized client definition.
     * Used by ItemProperties to select model overrides.
     */
    public int getIconIndex(EquipDefinition definition) {
        return 0;
    }

    @Override
    public boolean isEnchantable(ItemStack stack) {
        return false;
    }

    @Override
    public int getEnchantmentValue() {
        return 9;
    }

    // ==================== NBT Variant System ====================

    /**
     * Per-stack enchantability, subclasses override for type-specific values
     */
    public int getItemEnchantability(ItemStack stack) {
        return 9;
    }

    @Override
    public ResourceAmount getResourceAmount(ItemStack stack) {
        return ResourceAmount.ZERO;
    }

    /**
     * Create an ItemStack with the specified variant meta
     */
    public ItemStack createStack(int meta) {
        ItemStack stack = new ItemStack(this);
        setEquipMeta(stack, meta);
        return stack;
    }

    /**
     * Override description ID to provide variant-specific translation keys.
     * meta 0 -> "item.shincolle_kai.equip_cannon" (base key, no suffix)
     * meta N -> "item.shincolle_kai.equip_cannon_N"
     */
    @Override
    public String getDescriptionId(ItemStack stack) {
        int meta = getEquipMeta(stack);
        if (meta > 0) {
            return super.getDescriptionId() + "_" + meta;
        }
        return super.getDescriptionId();
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable(this.getDescriptionId(stack));
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        // Toggle enchantment visibility with Ctrl key
        if (stack.hasTag()) {
            CompoundTag nbt = stack.getTag();
            if (nbt != null) {
                int hideFlag = ClientRuntimeHelper.isControlDown() ? 0 : 1;
                nbt.putInt("HideFlags", hideFlag);
            }
        }

        EquipDefinition def = getClientDefinition(stack);

        if (def != null) {
            appendVariantDescription(stack, tooltip);

            try {
                ShipAttributeValues main = EquipCalc.calcEquipStatWithEnchant(def.enchantType(), def.stats(),
                        EnchantHelper.calcEnchantEffect(stack));
                appendAttributeTooltip(main, ShipAttributeLayout.current(), tooltip);
                ShipAttackEffectTooltipFormatter.append(def.attackEffects(), tooltip);
            } catch (RuntimeException exception) {
                String key = def.id() + "#" + def.item() + "#" + getEquipMeta(stack);
                if (REPORTED_INVALID_TOOLTIPS.add(key)) {
                    ShinColle.LOGGER.warn("Skipping invalid equipment attribute tooltip for {} item {} variant {}: {}",
                            def.id(), def.item(), getEquipMeta(stack), exception.toString());
                }
            }

            // Enchant type and equip type
            String drawstr = Component.translatable("gui.shincolle_kai.equip.enchtype").getString() + " ";
            drawstr += def.enchantType() == 1
                    ? ChatFormatting.RED + Component.translatable("gui.shincolle_kai.equip.enchtype1").getString()
                    : def.enchantType() == 2
                      ? ChatFormatting.AQUA + Component.translatable("gui.shincolle_kai.equip.enchtype0").getString()
                      : def.enchantType() == 3
                        ? ChatFormatting.GRAY
                          + Component.translatable("gui.shincolle_kai.equip.enchtype2").getString()
                        : "";
            int legacyEquipType = def.legacyEquipTypeValue();
            drawstr += legacyEquipType == 1
                    ? "  " + ChatFormatting.DARK_RED + Component.translatable("gui.shincolle_kai.notforcarrier").getString()
                    : legacyEquipType == 3
                      ? "  " + ChatFormatting.DARK_AQUA
                        + Component.translatable("gui.shincolle_kai.carrieronly").getString()
                      : "";
            tooltip.add(Component.literal(drawstr));

            // Construction info
            if (def.developAmount() > 400) {
                tooltip.add(
                        Component.literal(ChatFormatting.DARK_RED
                                + Component.translatable("block.shincolle_kai.block_large_shipyard").getString()));
            } else {
                tooltip.add(
                        Component.literal(ChatFormatting.DARK_RED
                                + Component.translatable("block.shincolle_kai.block_small_shipyard").getString()));
            }

            // Material info
            String matname = switch (def.developMaterial()) {
                case "abyss_metal" -> Component.translatable("item.shincolle_kai.abyss_metal").getString();
                case "ammo" -> Component.translatable("item.shincolle_kai.ammo").getString();
                case "abyss_metal_1" -> Component.translatable("item.shincolle_kai.abyss_metal_1").getString();
                default -> Component.translatable("item.shincolle_kai.grudge").getString();
            };

            // Upstream put the material and the rare level on one line. With the stars added it
            // grew wide enough to stretch the whole tooltip box, so they get a line each.
            tooltip.add(Component.literal(
                    ChatFormatting.DARK_PURPLE
                            + Component.translatable("gui.shincolle_kai.equip.matstype").getString()
                            + ChatFormatting.GRAY + " (" + matname + ") "
                            + String.format("%.0f", (float) def.developAmount())));

            // The raw rare_mean says nothing on its own about how high this sits. Stars read at a
            // glance and, being text, are reachable from JEI's "#" search.
            tooltip.add(Component.literal(
                    ChatFormatting.DARK_PURPLE
                            + Component.translatable("gui.shincolle_kai.equip.matsrarelevel").getString()
                            + ChatFormatting.GRAY + " " + EquipTier.of(def).starText()
                            + " " + String.format("%.0f", (float) def.rareMean())));
        }
    }

    /**
     * Adds the variant's own explanation line when one is translated.
     *
     * <p>Keyed off the variant's description ID plus {@code .desc}, so a variant whose behaviour
     * is not obvious from its stats can say what it does without every other variant growing a
     * line. Absent key, absent line.</p>
     */
    private static void appendVariantDescription(ItemStack stack, List<Component> tooltip) {
        String key = stack.getItem().getDescriptionId(stack) + ".desc";
        if (Language.getInstance().has(key)) {
            tooltip.add(Component.translatable(key).withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    static void appendAttributeTooltip(ShipAttributeValues values, List<Component> tooltip) {
        ShipAttributeTooltipFormatter.append(values, tooltip);
    }

    /**
     * Appends display lines using the locally registered attribute metadata. Packet-only attributes
     * are intentionally absent from {@code displayLayout} and therefore use the opaque fallback.
     */
    static void appendAttributeTooltip(ShipAttributeValues values, ShipAttributeLayout displayLayout,
                                       List<Component> tooltip) {
        ShipAttributeTooltipFormatter.append(values, displayLayout, tooltip);
    }
}
