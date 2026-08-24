package com.lulan.shincolle.api.attribute;

/**
 * How the legacy equipment-enchantment calculation treats an attribute.
 */
public enum ShipAttributeEnchantRule {
    NONE,
    MULTIPLY,
    WEAPON_MULTIPLY,
    ARMOR_MULTIPLY,
    SIGNED_MULTIPLY,
    WEAPON_ADDITIVE,
    NON_WEAPON_ADDITIVE
}
