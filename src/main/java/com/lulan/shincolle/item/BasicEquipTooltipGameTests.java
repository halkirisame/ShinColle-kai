package com.lulan.shincolle.item;

import com.lulan.shincolle.api.attribute.CoreShipAttributes;
import com.lulan.shincolle.api.attribute.ShipAttributeDisplayFormat;
import com.lulan.shincolle.api.attribute.ShipAttributeLayout;
import com.lulan.shincolle.api.attribute.ShipAttributeScaleGroup;
import com.lulan.shincolle.api.attribute.ShipAttributeType;
import com.lulan.shincolle.api.attribute.ShipAttributeValues;
import com.lulan.shincolle.api.equipment.ShipAttackEffect;
import com.lulan.shincolle.equipdata.ClientEquipData;
import com.lulan.shincolle.equipdata.EquipDataSnapshot;
import com.lulan.shincolle.equipdata.EquipDefinition;
import com.lulan.shincolle.handler.ConfigHandler;
import com.lulan.shincolle.init.ModItems;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.Reference;
import net.minecraft.ChatFormatting;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Regression coverage for dynamic and opaque equipment attribute tooltip rendering. */
@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BasicEquipTooltipGameTests {

    private static final ResourceLocation CUSTOM_ALPHA = id("tooltip_test", "alpha");
    private static final ResourceLocation OPAQUE = id("tooltip_test", "opaque");
    private static final ResourceLocation CUSTOM_ZERO = id("tooltip_test", "zero");
    private static final ResourceLocation CUSTOM_ZETA = id("tooltip_test", "zeta");
    private static final ResourceLocation POISON = id("minecraft", "poison");

    private BasicEquipTooltipGameTests() {
    }

    @GameTest(template = "empty", templateNamespace = "minecraft", batch = "equipment_tooltip_config")
    public static void equipmentTooltipPreservesCoreOrderAndFormatsDynamicAttributes(GameTestHelper helper) {
        double[] originalScale = ConfigHandler.scaleShip.clone();
        try {
            ConfigHandler.scaleShip[ID.AttrsBase.HP] = 1D;
            ConfigHandler.scaleShip[ID.AttrsBase.ATK] = 1.5D;
            ConfigHandler.scaleShip[ID.AttrsBase.DEF] = 1D;
            ConfigHandler.scaleShip[ID.AttrsBase.SPD] = 1D;
            ShipAttributeLayout valueLayout = layoutWithAttributes(true);
            ShipAttributeLayout displayLayout = layoutWithAttributes(false);
            ShipAttributeValues values = ShipAttributeValues.builder(valueLayout)
                    .set(CoreShipAttributes.HP, 2F)
                    .set(CoreShipAttributes.ATK_L, 3F)
                    .set(CoreShipAttributes.DEF, 0.2F)
                    .set(CoreShipAttributes.SPD, 1.234F)
                    .set(CoreShipAttributes.XP, 0.5F)
                    .set(CUSTOM_ALPHA, 2F)
                    .set(OPAQUE, 0.125F)
                    .set(CUSTOM_ZERO, 0F)
                    .set(CUSTOM_ZETA, 0.125F)
                    .build();
            List<Component> tooltip = new ArrayList<>();
            List<Component> formatterTooltip = new ArrayList<>();

            BasicEquip.appendAttributeTooltip(values, displayLayout, tooltip);
            ShipAttributeTooltipFormatter.append(values, displayLayout, formatterTooltip);

            List<String> expected = List.of(
                    colored(ChatFormatting.RED, "2.0", "gui.shincolle_kai.hp", false),
                    colored(ChatFormatting.RED, "4.5", "gui.shincolle_kai.firepower1", false),
                    colored(ChatFormatting.WHITE, "20.0%", "gui.shincolle_kai.armor", false),
                    colored(ChatFormatting.WHITE, "1.23", "gui.shincolle_kai.attackspeed", false),
                    colored(ChatFormatting.GREEN, "50%", "gui.shincolle_kai.equip.xp", true),
                    ChatFormatting.GRAY + CUSTOM_ALPHA.toString() + " 3",
                    ChatFormatting.GRAY + OPAQUE.toString() + " 0.13",
                    ChatFormatting.GRAY + CUSTOM_ZETA.toString() + " 13%"
            );
            assertTooltipEquals(expected, tooltip, "core/dynamic tooltip");
            assertTooltipEquals(expected, formatterTooltip, "shared core/dynamic tooltip");
        } finally {
            System.arraycopy(originalScale, 0, ConfigHandler.scaleShip, 0, originalScale.length);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void finalCustomTooltipDoesNotRescaleAndBoundsVisibleLines(GameTestHelper helper) {
        Map<ResourceLocation, ShipAttributeType> types = new HashMap<>();
        ShipAttributeValues.Builder values;
        for (int i = 0; i < 27; i++) {
            ResourceLocation attributeId = id("final_tooltip_test", String.format("attr_%02d", i));
            ShipAttributeType.Builder type = ShipAttributeType.builder();
            if (i == 0) {
                type.translationKey("gui.shincolle_kai.hp")
                        .scaleGroup(ShipAttributeScaleGroup.ATK)
                        .displayFormat(ShipAttributeDisplayFormat.INTEGER);
            } else if (i == 1) {
                type.displayFormat(ShipAttributeDisplayFormat.PERCENT);
            }
            types.put(attributeId, type.build());
        }
        ShipAttributeLayout layout = ShipAttributeLayout.detached(types);
        values = ShipAttributeValues.builder(layout);
        for (int i = 0; i < 27; i++) {
            float value = i == 0 ? 2F : i == 1 ? 0.125F : i + 0.25F;
            values.set(id("final_tooltip_test", String.format("attr_%02d", i)), value);
        }
        List<Component> tooltip = new ArrayList<>();

        ShipAttributeTooltipFormatter.appendFinalCustom(values.build(), layout, tooltip);

        if (tooltip.size() != ShipAttributeTooltipFormatter.MAX_FINAL_CUSTOM_ATTRIBUTES + 1) {
            throw new AssertionError("Final custom tooltip did not enforce its line bound: " + tooltip.size());
        }
        String translatedHp = Component.translatable("gui.shincolle_kai.hp").getString();
        if (!tooltip.get(0).getString().equals(ChatFormatting.GRAY + translatedHp + " 2")) {
            throw new AssertionError("Final custom value was rescaled or mistranslated: " + tooltip.get(0));
        }
        if (!tooltip.get(1).getString().equals(ChatFormatting.GRAY + "final_tooltip_test:attr_01 13%")) {
            throw new AssertionError("Missing translation did not fall back to ID or percent format changed: "
                    + tooltip.get(1));
        }
        String more = Component.translatable("gui.shincolle_kai.additional_attributes.more", 3).getString();
        if (!tooltip.get(tooltip.size() - 1).getString().equals(more)) {
            throw new AssertionError("Final custom tooltip omitted-count line differs: " + tooltip);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void attackEffectTooltipUsesRegistryNameAndStableFallback(GameTestHelper helper) {
        ResourceLocation unknown = id("tooltip_test", "unknown_effect");
        List<Component> tooltip = new ArrayList<>();
        ShipAttackEffectTooltipFormatter.append(Map.of(
                POISON, new ShipAttackEffect(POISON, 1, 120, 50),
                unknown, new ShipAttackEffect(unknown, 0, 40, 25)), tooltip);
        if (tooltip.size() != 2) {
            throw new AssertionError("Attack effect formatter did not emit two lines: " + tooltip);
        }
        String allText = tooltip.stream().map(Component::getString).reduce("", (left, right) -> left + right);
        String poisonName = Component.translatable("effect.minecraft.poison").getString();
        if (!allText.contains(poisonName) || !allText.contains(unknown.toString())) {
            throw new AssertionError("Attack effect tooltip lost registry name or stable ID fallback: " + tooltip);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "minecraft")
    public static void equipmentTooltipKeepsMetadataAfterInvalidAttributeCalculation(GameTestHelper helper) {
        EquipDataSnapshot original = ClientEquipData.current();
        Item item = ModItems.EQUIP_CANNON.get();
        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(item);
        if (itemId == null) {
            throw new AssertionError("Registered cannon item has no registry ID");
        }

        EquipDefinition definition = new EquipDefinition(id("tooltip_test", "overflow"), itemId, 999, 0,
                null, ShipAttributeValues.builder(ShipAttributeLayout.current())
                .set(CoreShipAttributes.ATK_L, Float.MAX_VALUE).build(), List.of("cannon"), 1,
                "grudge", 1, 2, 0);
        EquipDataSnapshot snapshot = new EquipDataSnapshot(Map.of(definition.id(), definition),
                Map.of(itemId, Map.of(999, definition)), Map.of());
        ItemStack stack = new ItemStack(item);
        BasicEquip.setEquipMeta(stack, 999);
        stack.enchant(Enchantments.SHARPNESS, 1);
        List<Component> tooltip = new ArrayList<>();

        try {
            ClientEquipData.install(snapshot);
            ((BasicEquip) item).appendHoverText(stack, helper.getLevel(), tooltip, TooltipFlag.Default.NORMAL);
        } finally {
            ClientEquipData.install(original);
        }

        if (tooltip.size() != 3) {
            throw new AssertionError("Invalid stats must skip only attribute lines and retain three metadata lines; got "
                    + tooltip.size() + " lines: " + tooltip);
        }
        assertContains(tooltip.get(0), Component.translatable("gui.shincolle_kai.equip.enchtype").getString(),
                "enchant metadata");
        assertContains(tooltip.get(1), Component.translatable("block.shincolle_kai.block_small_shipyard").getString(),
                "shipyard metadata");
        assertContains(tooltip.get(2), Component.translatable("gui.shincolle_kai.equip.matstype").getString(),
                "material metadata");
        helper.succeed();
    }

    private static ShipAttributeLayout layoutWithAttributes(boolean includeOpaque) {
        Map<ResourceLocation, ShipAttributeType> types = canonicalTypes();
        types.put(CUSTOM_ALPHA, ShipAttributeType.builder()
                .translationKey("tooltip_test.alpha")
                .scaleGroup(ShipAttributeScaleGroup.ATK)
                .displayFormat(ShipAttributeDisplayFormat.INTEGER)
                .build());
        types.put(CUSTOM_ZERO, ShipAttributeType.builder()
                .translationKey("tooltip_test.zero")
                .build());
        types.put(CUSTOM_ZETA, ShipAttributeType.builder()
                .translationKey("tooltip_test.zeta")
                .displayFormat(ShipAttributeDisplayFormat.PERCENT)
                .build());
        if (includeOpaque) {
            types.put(OPAQUE, ShipAttributeType.builder().build());
        }
        return ShipAttributeLayout.detached(types);
    }

    private static Map<ResourceLocation, ShipAttributeType> canonicalTypes() {
        ShipAttributeLayout canonical = ShipAttributeLayout.current();
        Map<ResourceLocation, ShipAttributeType> types = new HashMap<>();
        canonical.ids().forEach(id -> types.put(id, canonical.type(id)));
        return types;
    }

    private static String colored(ChatFormatting color, String number, String translationKey, boolean labelFirst) {
        String label = Component.translatable(translationKey).getString();
        return color + (labelFirst ? label + " " + number : number + " " + label);
    }

    private static void assertTooltipEquals(List<String> expected, List<Component> actual, String name) {
        List<String> actualText = actual.stream().map(Component::getString).toList();
        if (!expected.equals(actualText)) {
            throw new AssertionError(name + " mismatch; expected " + expected + " but was " + actualText);
        }
    }

    private static void assertContains(Component component, String expected, String name) {
        if (!component.getString().contains(expected)) {
            throw new AssertionError(name + " was not retained: " + component.getString());
        }
    }

    private static ResourceLocation id(String namespace, String path) {
        return ResourceLocation.fromNamespaceAndPath(namespace, path);
    }
}
