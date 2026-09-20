package com.lulan.shincolle.init;

import com.lulan.shincolle.api.attribute.CoreShipAttributes;
import com.lulan.shincolle.api.attribute.ShipAttributeCombiners;
import com.lulan.shincolle.api.attribute.ShipAttributeDisplayFormat;
import com.lulan.shincolle.api.attribute.ShipAttributeEnchantRule;
import com.lulan.shincolle.api.attribute.ShipAttributeLayer;
import com.lulan.shincolle.api.attribute.ShipAttributeLayout;
import com.lulan.shincolle.api.attribute.ShipAttributeRegistries;
import com.lulan.shincolle.api.attribute.ShipAttributeScaleGroup;
import com.lulan.shincolle.api.attribute.ShipAttributeType;
import com.lulan.shincolle.reference.Reference;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.IForgeRegistry;
import net.minecraftforge.registries.RegistryBuilder;
import net.minecraftforge.registries.RegistryObject;

import java.util.function.Supplier;

/**
 * Code-defined registry of extensible ship attributes.
 */
public final class ModShipAttributes {

    public static final DeferredRegister<ShipAttributeType> SHIP_ATTRIBUTES =
            DeferredRegister.create(ShipAttributeRegistries.REGISTRY_KEY, Reference.MOD_ID);
    public static final Supplier<IForgeRegistry<ShipAttributeType>> REGISTRY = SHIP_ATTRIBUTES.makeRegistry(
            () -> new RegistryBuilder<ShipAttributeType>().disableSaving().disableOverrides());

    public static final RegistryObject<ShipAttributeType> HP = register(CoreShipAttributes.HP,
            base(4F, 0F, 0F)
                    .combiner(ShipAttributeCombiners.scaledAdditive(true))
                    .minimum(1F)
                    .scaleGroup(ShipAttributeScaleGroup.HP)
                    .displayFormat(ShipAttributeDisplayFormat.INTEGER)
                    .enchantRule(ShipAttributeEnchantRule.MULTIPLY));
    public static final RegistryObject<ShipAttributeType> ATK_L = register(CoreShipAttributes.ATK_L,
            attack(1F));
    public static final RegistryObject<ShipAttributeType> ATK_H = register(CoreShipAttributes.ATK_H,
            attack(3F).enchantEffectSource(CoreShipAttributes.ATK_L));
    public static final RegistryObject<ShipAttributeType> ATK_AL = register(CoreShipAttributes.ATK_AL,
            attack(1F).enchantEffectSource(CoreShipAttributes.ATK_L));
    public static final RegistryObject<ShipAttributeType> ATK_AH = register(CoreShipAttributes.ATK_AH,
            attack(3F).enchantEffectSource(CoreShipAttributes.ATK_L));
    public static final RegistryObject<ShipAttributeType> DEF = register(CoreShipAttributes.DEF,
            base(0F, 0F, 1F)
                    .combiner(ShipAttributeCombiners.DEFENSE)
                    .minimum(0F)
                    .scaleGroup(ShipAttributeScaleGroup.DEF)
                    .displayFormat(ShipAttributeDisplayFormat.PERCENT)
                    .enchantRule(ShipAttributeEnchantRule.ARMOR_MULTIPLY));
    public static final RegistryObject<ShipAttributeType> SPD = register(CoreShipAttributes.SPD,
            base(0.2F, 1F, 1F)
                    .combiner(ShipAttributeCombiners.multiplicative(1F))
                    .minimum(0.2F)
                    .scaleGroup(ShipAttributeScaleGroup.SPD)
                    .enchantRule(ShipAttributeEnchantRule.MULTIPLY));
    public static final RegistryObject<ShipAttributeType> MOV = register(CoreShipAttributes.MOV,
            base(0F, 0F, 0F)
                    .combiner(ShipAttributeCombiners.scaledAdditive(false))
                    .minimum(0F)
                    .scaleGroup(ShipAttributeScaleGroup.MOV)
                    .enchantRule(ShipAttributeEnchantRule.SIGNED_MULTIPLY));
    public static final RegistryObject<ShipAttributeType> HIT = register(CoreShipAttributes.HIT,
            base(1F, 0F, 0F)
                    .combiner(ShipAttributeCombiners.scaledAdditive(true))
                    .minimum(1F)
                    .scaleGroup(ShipAttributeScaleGroup.HIT)
                    .enchantRule(ShipAttributeEnchantRule.MULTIPLY));
    public static final RegistryObject<ShipAttributeType> CRI = register(CoreShipAttributes.CRI,
            multiplicative(ShipAttributeEnchantRule.MULTIPLY));
    public static final RegistryObject<ShipAttributeType> DHIT = register(CoreShipAttributes.DHIT,
            multiplicative(ShipAttributeEnchantRule.MULTIPLY));
    public static final RegistryObject<ShipAttributeType> THIT = register(CoreShipAttributes.THIT,
            multiplicative(ShipAttributeEnchantRule.MULTIPLY));
    public static final RegistryObject<ShipAttributeType> MISS = register(CoreShipAttributes.MISS,
            multiplicative(ShipAttributeEnchantRule.MULTIPLY));
    public static final RegistryObject<ShipAttributeType> AA = register(CoreShipAttributes.AA,
            multiplicative(ShipAttributeEnchantRule.MULTIPLY));
    public static final RegistryObject<ShipAttributeType> ASM = register(CoreShipAttributes.ASM,
            multiplicative(ShipAttributeEnchantRule.MULTIPLY));
    public static final RegistryObject<ShipAttributeType> DODGE = register(CoreShipAttributes.DODGE,
            additive(0F, ShipAttributeEnchantRule.SIGNED_MULTIPLY));
    public static final RegistryObject<ShipAttributeType> XP = register(CoreShipAttributes.XP,
            additive(1F, ShipAttributeEnchantRule.WEAPON_ADDITIVE));
    public static final RegistryObject<ShipAttributeType> GRUDGE = register(CoreShipAttributes.GRUDGE,
            additive(1F, ShipAttributeEnchantRule.NON_WEAPON_ADDITIVE));
    public static final RegistryObject<ShipAttributeType> AMMO = register(CoreShipAttributes.AMMO,
            additive(1F, ShipAttributeEnchantRule.WEAPON_ADDITIVE));
    public static final RegistryObject<ShipAttributeType> HPRES = register(CoreShipAttributes.HPRES,
            additive(1F, ShipAttributeEnchantRule.NON_WEAPON_ADDITIVE));
    public static final RegistryObject<ShipAttributeType> KB = register(CoreShipAttributes.KB,
            additive(0F, ShipAttributeEnchantRule.NON_WEAPON_ADDITIVE));

    private ModShipAttributes() {
    }

    public static void register(IEventBus eventBus) {
        SHIP_ATTRIBUTES.register(eventBus);
    }

    public static void initializeLayout() {
        ShipAttributeLayout.initialize(REGISTRY.get());
    }

    private static RegistryObject<ShipAttributeType> register(ResourceLocation id,
                                                               ShipAttributeType.Builder builder) {
        return SHIP_ATTRIBUTES.register(id.getPath(), builder::build);
    }

    private static ShipAttributeType.Builder base(float raw, float morale, float formation) {
        return ShipAttributeType.builder()
                .defaultValue(ShipAttributeLayer.RAW, raw)
                .defaultValue(ShipAttributeLayer.MORALE, morale)
                .defaultValue(ShipAttributeLayer.FORMATION, formation);
    }

    private static ShipAttributeType.Builder attack(float potionMultiplier) {
        return base(0F, 1F, 1F)
                .combiner(ShipAttributeCombiners.multiplicative(potionMultiplier))
                .minimum(1F)
                .scaleGroup(ShipAttributeScaleGroup.ATK)
                .enchantRule(ShipAttributeEnchantRule.WEAPON_MULTIPLY);
    }

    private static ShipAttributeType.Builder multiplicative(ShipAttributeEnchantRule enchantRule) {
        return base(0F, 1F, 1F)
                .combiner(ShipAttributeCombiners.multiplicative(1F))
                .minimum(0F)
                .enchantRule(enchantRule);
    }

    private static ShipAttributeType.Builder additive(float raw, ShipAttributeEnchantRule enchantRule) {
        return base(raw, 0F, 0F)
                .combiner(ShipAttributeCombiners.ADDITIVE)
                .minimum(0F)
                .enchantRule(enchantRule);
    }
}
