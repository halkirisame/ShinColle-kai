package com.lulan.shincolle.equipdata;

import com.lulan.shincolle.reference.ID;

import java.util.Map;
import java.util.Set;

/** Fixed vocabulary shared by the equipment JSON loader and its published schema tests. */
final class EquipmentJsonFormat {

    static final int MAX_STATS = 256;
    static final int MAX_STAT_ID_LENGTH = 256;
    static final int MAX_COMPATIBLE = 32;
    static final int MAX_ATTACK_EFFECTS = 64;

    static final Set<String> REQUIRED_FIELDS = Set.of("item", "variant", "equip_type");
    static final Set<String> TOP_LEVEL_FIELDS = Set.of(
            "$schema", "equip_id", "item", "variant", "equip_type", "compatible",
            "enchant_type", "develop", "roll_type", "stats", "attack_effects", "availability");
    static final Set<String> DEVELOP_FIELDS = Set.of("material", "amount", "rare_mean");
    static final Set<String> ATTACK_EFFECT_FIELDS = Set.of(
            "effect", "amplifier", "duration", "chance");
    static final Set<String> COMPATIBILITY = Set.of("cannon", "aircraft");
    static final Set<String> ENCHANT_TYPES = Set.of("none", "weapon", "armor", "misc");
    static final Set<String> DEVELOP_MATERIALS = Set.of(
            "grudge", "abyss_metal", "ammo", "abyss_metal_1");
    static final Set<String> AVAILABILITY = Set.of(
            "any", "shipyard_only", "treasure_only", "unobtainable");

    static final Map<String, Integer> EQUIP_TYPES = Map.ofEntries(
            Map.entry("cannon_si", (int) ID.EquipType.CANNON_SI),
            Map.entry("cannon_tw_lo", (int) ID.EquipType.CANNON_TW_LO),
            Map.entry("cannon_tw_hi", (int) ID.EquipType.CANNON_TW_HI),
            Map.entry("cannon_tr", (int) ID.EquipType.CANNON_TR),
            Map.entry("torpedo_lo", (int) ID.EquipType.TORPEDO_LO),
            Map.entry("torpedo_hi", (int) ID.EquipType.TORPEDO_HI),
            Map.entry("air_t_lo", (int) ID.EquipType.AIR_T_LO),
            Map.entry("air_t_hi", (int) ID.EquipType.AIR_T_HI),
            Map.entry("air_f_lo", (int) ID.EquipType.AIR_F_LO),
            Map.entry("air_f_hi", (int) ID.EquipType.AIR_F_HI),
            Map.entry("air_b_lo", (int) ID.EquipType.AIR_B_LO),
            Map.entry("air_b_hi", (int) ID.EquipType.AIR_B_HI),
            Map.entry("air_r_lo", (int) ID.EquipType.AIR_R_LO),
            Map.entry("air_r_hi", (int) ID.EquipType.AIR_R_HI),
            Map.entry("radar_lo", (int) ID.EquipType.RADAR_LO),
            Map.entry("radar_hi", (int) ID.EquipType.RADAR_HI),
            Map.entry("turbine_lo", (int) ID.EquipType.TURBINE_LO),
            Map.entry("turbine_hi", (int) ID.EquipType.TURBINE_HI),
            Map.entry("armor_lo", (int) ID.EquipType.ARMOR_LO),
            Map.entry("armor_hi", (int) ID.EquipType.ARMOR_HI),
            Map.entry("gun_lo", (int) ID.EquipType.GUN_LO),
            Map.entry("gun_hi", (int) ID.EquipType.GUN_HI),
            Map.entry("catapult_lo", (int) ID.EquipType.CATAPULT_LO),
            Map.entry("catapult_hi", (int) ID.EquipType.CATAPULT_HI),
            Map.entry("drum_lo", (int) ID.EquipType.DRUM_LO),
            Map.entry("compass_lo", (int) ID.EquipType.COMPASS_LO),
            Map.entry("flare_lo", (int) ID.EquipType.FLARE_LO),
            Map.entry("searchlight_lo", (int) ID.EquipType.SEARCHLIGHT_LO),
            Map.entry("ammo_lo", (int) ID.EquipType.AMMO_LO),
            Map.entry("ammo_hi", (int) ID.EquipType.AMMO_HI));

    private EquipmentJsonFormat() {
    }
}
