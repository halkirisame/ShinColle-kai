package com.lulan.shincolle.item;

import com.lulan.shincolle.api.attribute.ShipAttributeDisplayFormat;
import com.lulan.shincolle.api.attribute.ShipAttributeLayout;
import com.lulan.shincolle.api.attribute.ShipAttributeScaleGroup;
import com.lulan.shincolle.api.attribute.ShipAttributeType;
import com.lulan.shincolle.api.attribute.ShipAttributeValues;
import com.lulan.shincolle.attribute.LegacyShipAttributeBridge;
import com.lulan.shincolle.handler.ConfigHandler;
import com.lulan.shincolle.reference.ID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Shared, side-safe presentation of resolved ship-equipment attributes.
 *
 * <p>This class deliberately consumes only immutable attribute values and
 * locally registered metadata. It is suitable for regular item tooltips and
 * client inventory detail screens without depending on an entity, a level, or
 * an optional-mod API.</p>
 */
public final class ShipAttributeTooltipFormatter {

    public static final int MAX_FINAL_CUSTOM_ATTRIBUTES = 24;

    /** One core stat's legacy-compatible display rule. */
    private record StatDisplay(byte attrIndex, ChatFormatting color, String key, String format,
                               int scaleIndex, boolean percent, boolean labelFirst) {
    }

    private static final List<StatDisplay> STAT_DISPLAYS = List.of(
            new StatDisplay(ID.Attrs.HP, ChatFormatting.RED, "gui.shincolle_kai.hp", "%.1f", ID.AttrsBase.HP, false, false),
            new StatDisplay(ID.Attrs.ATK_L, ChatFormatting.RED, "gui.shincolle_kai.firepower1", "%.1f", ID.AttrsBase.ATK, false, false),
            new StatDisplay(ID.Attrs.ATK_H, ChatFormatting.GREEN, "gui.shincolle_kai.torpedo", "%.1f", ID.AttrsBase.ATK, false, false),
            new StatDisplay(ID.Attrs.ATK_AL, ChatFormatting.RED, "gui.shincolle_kai.airfirepower", "%.1f", ID.AttrsBase.ATK, false, false),
            new StatDisplay(ID.Attrs.ATK_AH, ChatFormatting.GREEN, "gui.shincolle_kai.airtorpedo", "%.1f", ID.AttrsBase.ATK, false, false),
            new StatDisplay(ID.Attrs.DEF, ChatFormatting.WHITE, "gui.shincolle_kai.armor", "%.1f", ID.AttrsBase.DEF, true, false),
            new StatDisplay(ID.Attrs.SPD, ChatFormatting.WHITE, "gui.shincolle_kai.attackspeed", "%.2f", ID.AttrsBase.SPD, false, false),
            new StatDisplay(ID.Attrs.MOV, ChatFormatting.GRAY, "gui.shincolle_kai.movespeed", "%.2f", ID.AttrsBase.MOV, false, false),
            new StatDisplay(ID.Attrs.HIT, ChatFormatting.LIGHT_PURPLE, "gui.shincolle_kai.range", "%.1f", ID.AttrsBase.HIT, false, false),
            new StatDisplay(ID.Attrs.CRI, ChatFormatting.AQUA, "gui.shincolle_kai.critical", "%.0f", -1, true, false),
            new StatDisplay(ID.Attrs.DHIT, ChatFormatting.YELLOW, "gui.shincolle_kai.doublehit", "%.0f", -1, true, false),
            new StatDisplay(ID.Attrs.THIT, ChatFormatting.GOLD, "gui.shincolle_kai.triplehit", "%.0f", -1, true, false),
            new StatDisplay(ID.Attrs.MISS, ChatFormatting.RED, "gui.shincolle_kai.missreduce", "%.0f", -1, true, false),
            new StatDisplay(ID.Attrs.DODGE, ChatFormatting.GOLD, "gui.shincolle_kai.dodge", "%.0f", -1, true, false),
            new StatDisplay(ID.Attrs.AA, ChatFormatting.YELLOW, "gui.shincolle_kai.antiair", "%.1f", -1, false, false),
            new StatDisplay(ID.Attrs.ASM, ChatFormatting.AQUA, "gui.shincolle_kai.antiss", "%.1f", -1, false, false),
            new StatDisplay(ID.Attrs.XP, ChatFormatting.GREEN, "gui.shincolle_kai.equip.xp", "%.0f", -1, true, true),
            new StatDisplay(ID.Attrs.GRUDGE, ChatFormatting.DARK_PURPLE, "gui.shincolle_kai.equip.grudge", "%.0f", -1, true, true),
            new StatDisplay(ID.Attrs.AMMO, ChatFormatting.DARK_AQUA, "gui.shincolle_kai.equip.ammo", "%.0f", -1, true, true),
            new StatDisplay(ID.Attrs.HPRES, ChatFormatting.DARK_GREEN, "gui.shincolle_kai.equip.hpres", "%.0f", -1, true, true),
            new StatDisplay(ID.Attrs.KB, ChatFormatting.DARK_RED, "gui.shincolle_kai.equip.kb", "%.0f", -1, true, true)
    );

    private ShipAttributeTooltipFormatter() {
    }

    public static void append(ShipAttributeValues values, List<Component> tooltip) {
        append(values, ShipAttributeLayout.current(), tooltip);
    }

    /**
     * Appends display lines using locally registered attribute metadata.
     * Packet-only attributes absent from {@code displayLayout} use their ID as
     * a stable opaque fallback.
     */
    public static void append(ShipAttributeValues values, ShipAttributeLayout displayLayout,
                              List<Component> tooltip) {
        float[] legacy = LegacyShipAttributeBridge.toLegacyArray(values);
        for (StatDisplay display : STAT_DISPLAYS) {
            float scale = display.scaleIndex() >= 0
                    ? (float) ConfigHandler.scaleShip[display.scaleIndex()] : 1F;
            float displayValue = legacy[display.attrIndex()] * scale * (display.percent() ? 100F : 1F);
            if (legacy[display.attrIndex()] == 0F || !Float.isFinite(displayValue)) {
                continue;
            }
            String number = String.format(display.format(), displayValue) + (display.percent() ? "%" : "");
            String label = Component.translatable(display.key()).getString();
            String text = display.labelFirst() ? label + " " + number : number + " " + label;
            tooltip.add(Component.literal(display.color() + text));
        }

        appendCustom(values, displayLayout, tooltip, true, Integer.MAX_VALUE);
    }

    /** Appends final, already-combined custom values without applying equipment scale again. */
    public static void appendFinalCustom(ShipAttributeValues values, ShipAttributeLayout displayLayout,
                                         List<Component> tooltip) {
        int omitted = appendCustom(values, displayLayout, tooltip, false, MAX_FINAL_CUSTOM_ATTRIBUTES);
        if (omitted > 0) {
            tooltip.add(Component.translatable("gui.shincolle_kai.additional_attributes.more", omitted)
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    private static int appendCustom(ShipAttributeValues values, ShipAttributeLayout displayLayout,
                                    List<Component> tooltip, boolean applyScale, int maximumLines) {
        int appended = 0;
        int omitted = 0;
        for (ResourceLocation id : values.layout().ids()) {
            if (LegacyShipAttributeBridge.legacyIndex(id) >= 0) {
                continue;
            }
            float value = values.get(id);
            if (value == 0F) {
                continue;
            }
            if (appended >= maximumLines) {
                omitted++;
                continue;
            }
            ShipAttributeType type = displayLayout.type(id);
            float scaled = applyScale && type != null ? scaleCustomValue(value, type.scaleGroup()) : value;
            if (!Float.isFinite(scaled)) {
                continue;
            }
            String label = customLabel(id, type);
            ShipAttributeDisplayFormat format = type == null
                    ? ShipAttributeDisplayFormat.DECIMAL : type.displayFormat();
            tooltip.add(Component.literal(ChatFormatting.GRAY + label + " " + formatCustomValue(scaled, format)));
            appended++;
        }
        return omitted;
    }

    private static String customLabel(ResourceLocation id, ShipAttributeType type) {
        if (type == null) {
            return id.toString();
        }
        String key = type.translationKey(id);
        String translated = Component.translatable(key).getString();
        return translated.equals(key) ? id.toString() : translated;
    }

    private static float scaleCustomValue(float value, ShipAttributeScaleGroup group) {
        int scaleIndex = switch (group) {
            case NONE -> -1;
            case HP -> ID.AttrsBase.HP;
            case ATK -> ID.AttrsBase.ATK;
            case DEF -> ID.AttrsBase.DEF;
            case SPD -> ID.AttrsBase.SPD;
            case MOV -> ID.AttrsBase.MOV;
            case HIT -> ID.AttrsBase.HIT;
        };
        return scaleIndex < 0 ? value : value * (float) ConfigHandler.scaleShip[scaleIndex];
    }

    private static String formatCustomValue(float value, ShipAttributeDisplayFormat format) {
        return switch (format) {
            case DECIMAL -> String.format("%.2f", value);
            case INTEGER -> String.format("%.0f", value);
            case PERCENT -> String.format("%.0f%%", value * 100F);
        };
    }
}
