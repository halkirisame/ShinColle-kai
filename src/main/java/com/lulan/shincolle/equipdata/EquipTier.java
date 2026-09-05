package com.lulan.shincolle.equipdata;

/**
 * How high an equipment definition sits in the development ladder.
 *
 * <p>Derived from {@code develop.rare_mean}, the material amount its roll peaks at. The four
 * bands below are where the 94 shipped definitions actually cluster (23 / 24 / 18 / 23), so
 * this is a reading of existing data rather than a new axis bolted onto it.</p>
 *
 * <p>Deliberately Minecraft-free: the mapping onto {@code Rarity} colors and onto tooltip stars
 * lives with the presentation code, and this stays unit-testable on its own.</p>
 */
public enum EquipTier {
    BASIC(1),
    IMPROVED(2),
    ADVANCED(3),
    ELITE(4);

    private static final int BASIC_MAX = 1000;
    private static final int IMPROVED_MAX = 2600;
    private static final int ADVANCED_MAX = 3600;

    /** Highest tier index, i.e. how many stars a full rating draws. */
    public static final int MAX_STARS = 4;

    private final int stars;

    EquipTier(int stars) {
        this.stars = stars;
    }

    /** Filled stars for this tier, 1 to {@link #MAX_STARS}. */
    public int stars() {
        return this.stars;
    }

    /** The tier a definition rolling around {@code rareMean} materials belongs to. */
    public static EquipTier fromRareMean(int rareMean) {
        if (rareMean <= BASIC_MAX) {
            return BASIC;
        }
        if (rareMean <= IMPROVED_MAX) {
            return IMPROVED;
        }
        if (rareMean <= ADVANCED_MAX) {
            return ADVANCED;
        }
        return ELITE;
    }

    /** The tier of a loaded definition. */
    public static EquipTier of(EquipDefinition definition) {
        return definition == null ? BASIC : fromRareMean(definition.rareMean());
    }

    /** "★★☆☆" for this tier. */
    public String starText() {
        return "★".repeat(this.stars) + "☆".repeat(MAX_STARS - this.stars);
    }
}
