package com.lulan.shincolle.item;

import com.lulan.shincolle.crafting.EquipCalc;
import com.lulan.shincolle.equipdata.EquipDataRegistry;
import com.lulan.shincolle.equipdata.EquipDefinition;
import com.lulan.shincolle.handler.ConfigHandler;
import com.lulan.shincolle.reference.Enums.EnumEquipEffectSP;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.utility.ClientRuntimeHelper;
import com.lulan.shincolle.utility.EnchantHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Random;

/**
 * Base class for ship equipment items.
 * Equipment items have max stack size of 1 and use NBT for variant data.
 * In 1.10.2, variants were stored as item damage/meta values.
 * In 1.20.1, we store the variant in NBT tag "EquipMeta".
 */
public abstract class BasicEquip extends BasicItem implements IShipResourceItem {

    public static final String TAG_EQUIP_META = "EquipMeta";

    protected static final Random itemRand = new Random();

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

    /**
     * Get the equipment type ID for a given variant meta
     */
    public abstract int getEquipTypeIDFromMeta(int meta);

    /**
     * Calculate the unique equipment ID (EquipTypeID + meta * 100)
     */
    public int getEquipID(int meta) {
        return getEquipTypeIDFromMeta(meta) + meta * 100;
    }

    /**
     * Equip special effect
     */
    public EnumEquipEffectSP getSpecialEffect(ItemStack stack) {
        return EnumEquipEffectSP.NONE;
    }

    /**
     * Get the texture icon index for a given meta value.
     * Used by ItemProperties to select model overrides.
     * Subclasses override to map meta ranges to icon indices.
     */
    public int getIconFromDamage(int meta) {
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
    public int[] getResourceValue(int meta) {
        return new int[]{0, 0, 0, 0};
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
     * meta 0 -> "item.shincolle.equip_cannon" (base key, no suffix)
     * meta N -> "item.shincolle.equip_cannon_N"
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

    /**
     * One stat's tooltip rendering rule: which {@link ID.Attrs} slot, color,
     * number format, {@link ID.AttrsBase} scale index (-1 = no config
     * scaling), whether it's shown as a percentage, and whether the
     * translated label comes before the number (the XP/GRUDGE/AMMO/HPRES/KB
     * "regen stat" style) or after it (everything else).
     *
     * <p>Adding a 22nd stat axis some day means adding one entry here rather
     * than another copy-pasted {@code if} block.
     */
    private record StatDisplay(byte attrIndex, ChatFormatting color, String key, String format,
                                int scaleIndex, boolean percent, boolean labelFirst) {
    }

    private static final List<StatDisplay> STAT_DISPLAYS = List.of(
            new StatDisplay(ID.Attrs.HP, ChatFormatting.RED, "gui.shincolle.hp", "%.1f", ID.AttrsBase.HP, false, false),
            new StatDisplay(ID.Attrs.ATK_L, ChatFormatting.RED, "gui.shincolle.firepower1", "%.1f", ID.AttrsBase.ATK, false, false),
            new StatDisplay(ID.Attrs.ATK_H, ChatFormatting.GREEN, "gui.shincolle.torpedo", "%.1f", ID.AttrsBase.ATK, false, false),
            new StatDisplay(ID.Attrs.ATK_AL, ChatFormatting.RED, "gui.shincolle.airfirepower", "%.1f", ID.AttrsBase.ATK, false, false),
            new StatDisplay(ID.Attrs.ATK_AH, ChatFormatting.GREEN, "gui.shincolle.airtorpedo", "%.1f", ID.AttrsBase.ATK, false, false),
            new StatDisplay(ID.Attrs.DEF, ChatFormatting.WHITE, "gui.shincolle.armor", "%.1f", ID.AttrsBase.DEF, true, false),
            new StatDisplay(ID.Attrs.SPD, ChatFormatting.WHITE, "gui.shincolle.attackspeed", "%.2f", ID.AttrsBase.SPD, false, false),
            new StatDisplay(ID.Attrs.MOV, ChatFormatting.GRAY, "gui.shincolle.movespeed", "%.2f", ID.AttrsBase.MOV, false, false),
            new StatDisplay(ID.Attrs.HIT, ChatFormatting.LIGHT_PURPLE, "gui.shincolle.range", "%.1f", ID.AttrsBase.HIT, false, false),
            new StatDisplay(ID.Attrs.CRI, ChatFormatting.AQUA, "gui.shincolle.critical", "%.0f", -1, true, false),
            new StatDisplay(ID.Attrs.DHIT, ChatFormatting.YELLOW, "gui.shincolle.doublehit", "%.0f", -1, true, false),
            new StatDisplay(ID.Attrs.THIT, ChatFormatting.GOLD, "gui.shincolle.triplehit", "%.0f", -1, true, false),
            new StatDisplay(ID.Attrs.MISS, ChatFormatting.RED, "gui.shincolle.missreduce", "%.0f", -1, true, false),
            new StatDisplay(ID.Attrs.DODGE, ChatFormatting.GOLD, "gui.shincolle.dodge", "%.0f", -1, true, false),
            new StatDisplay(ID.Attrs.AA, ChatFormatting.YELLOW, "gui.shincolle.antiair", "%.1f", -1, false, false),
            new StatDisplay(ID.Attrs.ASM, ChatFormatting.AQUA, "gui.shincolle.antiss", "%.1f", -1, false, false),
            new StatDisplay(ID.Attrs.XP, ChatFormatting.GREEN, "gui.shincolle.equip.xp", "%.0f", -1, true, true),
            new StatDisplay(ID.Attrs.GRUDGE, ChatFormatting.DARK_PURPLE, "gui.shincolle.equip.grudge", "%.0f", -1, true, true),
            new StatDisplay(ID.Attrs.AMMO, ChatFormatting.DARK_AQUA, "gui.shincolle.equip.ammo", "%.0f", -1, true, true),
            new StatDisplay(ID.Attrs.HPRES, ChatFormatting.DARK_GREEN, "gui.shincolle.equip.hpres", "%.0f", -1, true, true),
            new StatDisplay(ID.Attrs.KB, ChatFormatting.DARK_RED, "gui.shincolle.equip.kb", "%.0f", -1, true, true)
    );

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

        int meta = getEquipMeta(stack);
        int equipID = getEquipID(meta);

        EquipDefinition def = EquipDataRegistry.get(equipID);

        if (def != null) {
            // Apply enchant effect
            float[] main = EquipCalc.calcEquipStatWithEnchant(def.enchantType(), def.stats(),
                    EnchantHelper.calcEnchantEffect(stack));

            // Draw stat values
            for (StatDisplay sd : STAT_DISPLAYS) {
                float value = main[sd.attrIndex()];
                if (value == 0F) {
                    continue;
                }
                float scale = sd.scaleIndex() >= 0 ? (float) ConfigHandler.scaleShip[sd.scaleIndex()] : 1F;
                float displayValue = value * scale * (sd.percent() ? 100F : 1F);
                String numStr = String.format(sd.format(), displayValue) + (sd.percent() ? "%" : "");
                String label = Component.translatable(sd.key()).getString();
                String text = sd.labelFirst() ? (label + " " + numStr) : (numStr + " " + label);
                tooltip.add(Component.literal(sd.color() + text));
            }

            // Enchant type and equip type
            String drawstr = Component.translatable("gui.shincolle.equip.enchtype").getString() + " ";
            drawstr += def.enchantType() == 1
                    ? ChatFormatting.RED + Component.translatable("gui.shincolle.equip.enchtype1").getString()
                    : def.enchantType() == 2
                      ? ChatFormatting.AQUA + Component.translatable("gui.shincolle.equip.enchtype0").getString()
                      : def.enchantType() == 3
                        ? ChatFormatting.GRAY
                          + Component.translatable("gui.shincolle.equip.enchtype2").getString()
                        : "";
            int legacyEquipType = def.legacyEquipTypeValue();
            drawstr += legacyEquipType == 1
                    ? "  " + ChatFormatting.DARK_RED + Component.translatable("gui.shincolle.notforcarrier").getString()
                    : legacyEquipType == 3
                      ? "  " + ChatFormatting.DARK_AQUA
                        + Component.translatable("gui.shincolle.carrieronly").getString()
                      : "";
            tooltip.add(Component.literal(drawstr));

            // Construction info
            if (def.developAmount() > 400) {
                tooltip.add(
                        Component.literal(ChatFormatting.DARK_RED
                                + Component.translatable("block.shincolle.block_large_shipyard").getString()));
            } else {
                tooltip.add(
                        Component.literal(ChatFormatting.DARK_RED
                                + Component.translatable("block.shincolle.block_small_shipyard").getString()));
            }

            // Material info
            String matname = switch (def.developMaterial()) {
                case "abyss_metal" -> Component.translatable("item.shincolle.abyss_metal").getString();
                case "ammo" -> Component.translatable("item.shincolle.ammo").getString();
                case "abyss_metal_1" -> Component.translatable("item.shincolle.abyss_metal_1").getString();
                default -> Component.translatable("item.shincolle.grudge").getString();
            };

            drawstr = ChatFormatting.DARK_PURPLE + Component.translatable("gui.shincolle.equip.matstype").getString() +
                    ChatFormatting.GRAY + " (" + matname + ") " +
                    String.format("%.0f", (float) def.developAmount()) + "  " +
                    ChatFormatting.DARK_PURPLE + Component.translatable("gui.shincolle.equip.matsrarelevel").getString()
                    +
                    ChatFormatting.GRAY + " " + String.format("%.0f", (float) def.rareMean());
            tooltip.add(Component.literal(drawstr));
        }
    }
}
