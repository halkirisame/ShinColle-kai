package com.lulan.shincolle.equipdata;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.lulan.shincolle.ShinColle;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.reference.unitclass.Attrs;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads {@code data/<domain>/equipment/*.json} into {@link EquipDefinition}s,
 * keyed by their file-path {@link ResourceLocation}. Any datapack can add, override, or (with
 * {@code "replace"}-style datapack layering) shadow entries here - the point
 * of moving off the old hardcoded {@code Values.EquipAttrsMain}/{@code
 * EquipAttrsMisc} maps.
 *
 * <p>Read {@link EquipDataRegistry} to look up loaded definitions; this class
 * only owns the reload/parse step.
 */
public class EquipDataLoader extends SimpleJsonResourceReloadListener {

    private static final Gson GSON = new Gson();
    private static final String DIRECTORY = "equipment";

    /** Maps a JSON stats-object key to its {@link ID.Attrs} index. */
    private static final Map<String, Integer> STAT_KEYS = new HashMap<>();
    private static final Map<String, Integer> EQUIP_TYPES = Map.ofEntries(
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

    static {
        STAT_KEYS.put("hp", (int) ID.Attrs.HP);
        STAT_KEYS.put("atk_l", (int) ID.Attrs.ATK_L);
        STAT_KEYS.put("atk_h", (int) ID.Attrs.ATK_H);
        STAT_KEYS.put("atk_al", (int) ID.Attrs.ATK_AL);
        STAT_KEYS.put("atk_ah", (int) ID.Attrs.ATK_AH);
        STAT_KEYS.put("def", (int) ID.Attrs.DEF);
        STAT_KEYS.put("spd", (int) ID.Attrs.SPD);
        STAT_KEYS.put("mov", (int) ID.Attrs.MOV);
        STAT_KEYS.put("hit", (int) ID.Attrs.HIT);
        STAT_KEYS.put("cri", (int) ID.Attrs.CRI);
        STAT_KEYS.put("dhit", (int) ID.Attrs.DHIT);
        STAT_KEYS.put("thit", (int) ID.Attrs.THIT);
        STAT_KEYS.put("miss", (int) ID.Attrs.MISS);
        STAT_KEYS.put("aa", (int) ID.Attrs.AA);
        STAT_KEYS.put("asm", (int) ID.Attrs.ASM);
        STAT_KEYS.put("dodge", (int) ID.Attrs.DODGE);
        STAT_KEYS.put("xp", (int) ID.Attrs.XP);
        STAT_KEYS.put("grudge", (int) ID.Attrs.GRUDGE);
        STAT_KEYS.put("ammo", (int) ID.Attrs.AMMO);
        STAT_KEYS.put("hpres", (int) ID.Attrs.HPRES);
        STAT_KEYS.put("kb", (int) ID.Attrs.KB);
    }

    private static volatile EquipDataSnapshot serverSnapshot = EquipDataSnapshot.EMPTY;

    public EquipDataLoader() {
        super(GSON, DIRECTORY);
    }

    static EquipDataSnapshot currentServerSnapshot() {
        return serverSnapshot;
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> object, ResourceManager resourceManager,
                          ProfilerFiller profiler) {
        Map<ResourceLocation, EquipDefinition> definitions = new HashMap<>();
        Map<ResourceLocation, Map<Integer, EquipDefinition>> itemVariants = new HashMap<>();
        Map<Integer, EquipDefinition> legacyDefinitions = new HashMap<>();
        for (Map.Entry<ResourceLocation, JsonElement> entry : object.entrySet()) {
            try {
                EquipDefinition def = parse(entry.getKey(), entry.getValue().getAsJsonObject());
                definitions.put(def.id(), def);

                Map<Integer, EquipDefinition> variants = itemVariants.computeIfAbsent(
                        def.item(), unused -> new HashMap<>());
                EquipDefinition previousVariant = variants.put(def.variant(), def);
                if (previousVariant != null) {
                    ShinColle.LOGGER.warn("Ship equipment item/variant collision: {} and {} both use item {} "
                                    + "variant {}; last one loaded wins", previousVariant.id(), def.id(),
                            def.item(), def.variant());
                }

                if (def.legacyEquipId() != null) {
                    EquipDefinition previousLegacy = legacyDefinitions.put(def.legacyEquipId(), def);
                    if (previousLegacy != null) {
                        ShinColle.LOGGER.warn("Ship equipment legacy ID collision: {} and {} both use equip_id {}; "
                                        + "last one loaded wins", previousLegacy.id(), def.id(),
                                def.legacyEquipId());
                    }
                }
            } catch (RuntimeException e) {
                ShinColle.LOGGER.error("Failed to parse ship equipment {}: {}", entry.getKey(), e.toString());
            }
        }

        serverSnapshot = new EquipDataSnapshot(definitions, itemVariants, legacyDefinitions);
        ShinColle.LOGGER.info("Loaded {} ship equipment definitions", serverSnapshot.byId().size());
    }

    private static EquipDefinition parse(ResourceLocation id, JsonObject json) {
        ResourceLocation item = ResourceLocation.tryParse(json.get("item").getAsString());
        if (item == null) {
            throw new IllegalArgumentException("invalid item ResourceLocation");
        }
        int variant = json.get("variant").getAsInt();
        String equipTypeName = json.get("equip_type").getAsString();
        Integer equipType = EQUIP_TYPES.get(equipTypeName);
        if (equipType == null) {
            throw new IllegalArgumentException("unknown equip_type '" + equipTypeName + "'");
        }
        Integer legacyEquipId = json.has("equip_id") ? json.get("equip_id").getAsInt() : null;

        float[] stats = new float[Attrs.AttrsLength];
        if (json.has("stats")) {
            for (Map.Entry<String, JsonElement> e : json.getAsJsonObject("stats").entrySet()) {
                Integer index = STAT_KEYS.get(e.getKey());
                if (index == null) {
                    throw new IllegalArgumentException("unknown stat key '" + e.getKey() + "'");
                }
                stats[index] = e.getValue().getAsFloat();
            }
        }

        List<String> compatible = new ArrayList<>();
        if (json.has("compatible")) {
            json.getAsJsonArray("compatible").forEach(e -> compatible.add(e.getAsString()));
        }

        int enchantType = enchantTypeFromString(
                json.has("enchant_type") ? json.get("enchant_type").getAsString() : "none");

        JsonObject develop = json.has("develop") ? json.getAsJsonObject("develop") : new JsonObject();
        String material = develop.has("material") ? develop.get("material").getAsString() : "grudge";
        int amount = develop.has("amount") ? develop.get("amount").getAsInt() : 0;
        int rareMean = develop.has("rare_mean") ? develop.get("rare_mean").getAsInt() : 0;

        int rollType = json.has("roll_type") ? json.get("roll_type").getAsInt() : equipType;

        return new EquipDefinition(id, item, variant, equipType, legacyEquipId, stats, compatible,
                enchantType, material, amount, rareMean, rollType);
    }

    private static int enchantTypeFromString(String value) {
        return switch (value) {
            case "weapon" -> 1;
            case "armor" -> 2;
            case "misc" -> 3;
            default -> 0;
        };
    }

    /** Inverse of {@link #enchantTypeFromString}, used by the Values.java -> JSON conversion tool. */
    static String enchantTypeToString(int value) {
        return switch (value) {
            case 1 -> "weapon";
            case 2 -> "armor";
            case 3 -> "misc";
            default -> "none";
        };
    }

    /** Maps the old numeric DEVELOP_MAT to the JSON material key. Used by the conversion tool. */
    static String developMaterialToString(int value) {
        return switch (value) {
            case 1 -> "abyss_metal";
            case 2 -> "ammo";
            case 3 -> "abyss_metal_1";
            default -> "grudge";
        };
    }

    /** Inverse of {@link #developMaterialToString}. */
    private static int developMaterialFromString(String value) {
        return switch (value) {
            case "abyss_metal" -> 1;
            case "ammo" -> 2;
            case "abyss_metal_1" -> 3;
            default -> 0;
        };
    }
}
