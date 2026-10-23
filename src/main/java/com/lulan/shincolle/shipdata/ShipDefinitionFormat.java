package com.lulan.shincolle.shipdata;

import com.lulan.shincolle.reference.Reference;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.Set;

/** Limits and vocabulary of the server ship JSON format. */
public final class ShipDefinitionFormat {
    public static final int SCHEMA = 1;
    public static final String DIRECTORY = Reference.MOD_ID + "/ships";
    public static final int MAX_ID_LENGTH = 64;
    public static final int MAX_DISPLAY_NAME_LENGTH = 64;
    public static final int MAX_ATTACKS = 16;
    public static final int MAX_SLOTS = 16;
    public static final int MAX_JSON_BYTES = 64 * 1024;
    public static final int MAX_JSON_DEPTH = 32;
    public static final ShipDefinition.Dimensions DEFAULT_DIMENSIONS = new ShipDefinition.Dimensions(0.5F, 1.5F);
    public static final double DEFAULT_MOUNT_OFFSET = 0.9;
    public static final double MAX_WIDTH = 1.6;
    public static final double MAX_HEIGHT = 4.4;
    public static final ResourceLocation DEFAULT_ATTACK_PROFILE = new ResourceLocation(Reference.MOD_ID, "default");

    /** Twice the maxima of the six base columns of the built-in ship table. */
    public static final Map<String, Double> BASE_MAX = Map.of(
            "hp", 700.0, "atk", 110.0, "def", 0.9, "spd", 2.4, "mov", 1.2, "hit", 60.0);
    /** Twice the maxima of the six growth columns, not twice a level-scaled total. */
    public static final Map<String, Double> GROWTH_MAX = Map.of(
            "hp", 3.0, "atk", 2.0, "def", 0.68, "spd", 1.5, "mov", 2.4, "hit", 2.0);
    public static final Set<String> SHIP_TYPES = Set.of(
            "destroyer", "light_cruiser", "heavy_cruiser", "torpedo_cruiser", "light_carrier",
            "standard_carrier", "battleship", "transport", "submarine", "demon", "hime");
    public static final Set<String> FIELDS = Set.of("$schema", "schema", "display_name", "ship_type", "visual",
            "base_stats", "growth", "equipment", "construction", "spawn", "behavior", "recruitment",
            "dimensions", "mount_offset", "equip_type", "attack_profile", "hostile_modifiers");

    private ShipDefinitionFormat() {
    }

    static ResourceLocation requireId(String text) {
        if (text == null || text.length() > MAX_ID_LENGTH
                || !text.matches("[a-z0-9_.-]+:[a-z0-9/._-]+")) {
            throw new IllegalArgumentException("expected an explicit namespaced ID of at most " + MAX_ID_LENGTH + " characters");
        }
        ResourceLocation id = ResourceLocation.tryParse(text);
        if (id == null) {
            throw new IllegalArgumentException("invalid resource ID '" + text + "'");
        }
        return id;
    }
}
