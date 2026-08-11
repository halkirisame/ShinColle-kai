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
 * keyed by their {@code equip_id}. Any datapack can add, override, or (with
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

    private static volatile Map<Integer, EquipDefinition> registry = Map.of();

    public EquipDataLoader() {
        super(GSON, DIRECTORY);
    }

    static Map<Integer, EquipDefinition> currentRegistry() {
        return registry;
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> object, ResourceManager resourceManager,
                          ProfilerFiller profiler) {
        Map<Integer, EquipDefinition> result = new HashMap<>();
        for (Map.Entry<ResourceLocation, JsonElement> entry : object.entrySet()) {
            try {
                EquipDefinition def = parse(entry.getValue().getAsJsonObject());
                EquipDefinition previous = result.put(def.equipId(), def);
                if (previous != null) {
                    ShinColle.LOGGER.warn("Ship equipment {}: equip_id {} collides with another file, "
                            + "last one loaded wins", entry.getKey(), def.equipId());
                }
            } catch (RuntimeException e) {
                ShinColle.LOGGER.error("Failed to parse ship equipment {}: {}", entry.getKey(), e.toString());
            }
        }
        registry = Map.copyOf(result);
        ShinColle.LOGGER.info("Loaded {} ship equipment definitions", registry.size());
    }

    private static EquipDefinition parse(JsonObject json) {
        int equipId = json.get("equip_id").getAsInt();

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

        int rollType = json.has("roll_type") ? json.get("roll_type").getAsInt() : equipId % 100;

        return new EquipDefinition(equipId, stats, compatible, enchantType, material, amount, rareMean, rollType);
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
