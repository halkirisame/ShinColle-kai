package com.lulan.shincolle.shipdata;

import com.lulan.shincolle.ShinColle;
import com.lulan.shincolle.ai.registry.ShipTraitRegistries;
import com.lulan.shincolle.init.ModShipBehaviors;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Server-owned snapshot. Unknown references survive; future versions remain opaque and inert. */
public final class ShipIndividual {
    public static final String KEY = "ShinIndividual";
    private static final Set<String> REPORTED = ConcurrentHashMap.newKeySet();
    private final CompoundTag future;
    private final boolean rolled;
    private final String variant;
    private final Presentation presentation;
    private final List<ResourceLocation> traits;

    private ShipIndividual(boolean rolled, String variant, Presentation presentation,
                           List<ResourceLocation> traits, CompoundTag future) {
        this.rolled = rolled;
        this.variant = variant;
        this.presentation = presentation;
        this.traits = List.copyOf(traits);
        this.future = future == null ? null : future.copy();
    }

    public static ShipIndividual normal(boolean rolled) {
        return new ShipIndividual(rolled, "", Presentation.NORMAL, List.of(), null);
    }

    public static ShipIndividual selected(HostileVariants.Variant variant) {
        if (variant == null) return normal(true);
        return new ShipIndividual(true, variant.id(), new Presentation(variant.name().orElse(""),
                variant.rarity(), variant.visualVariant().map(Object::toString).orElse(""), variant.redAura()),
                variant.traits(), null);
    }

    public boolean rolled() { return rolled; }
    public String variant() { return variant; }
    public Presentation presentation() { return presentation; }
    public List<ResourceLocation> traits() { return traits; }

    /** Friendly saves retain opaque future data, but never activate hostile presentation. */
    public ShipIndividual asFriendly() {
        if (future != null) return this;
        return new ShipIndividual(true, variant, new Presentation(presentation.name(), presentation.rarity(),
                presentation.visual(), false), traits, null);
    }

    /** Fresh recruitment copies only the understood individual fields, never an entity save. */
    public CompoundTag recruitmentData() {
        if (future != null || (variant.isEmpty() && traits.isEmpty() && presentation.equals(Presentation.NORMAL))) return null;
        return asFriendly().save();
    }

    public float[] traitMultipliers(String shipId) {
        float[] result = {1, 1, 1, 1, 1, 1};
        traits.stream().sorted().forEach(id -> {
            if (ShipTraitRegistries.isSelectableTrait(id)) {
                var trait = ModShipBehaviors.TRAIT_REGISTRY.get().getValue(id);
                result[trait.attribute().ordinal()] *= (float) trait.multiplier();
            } else warn(shipId, "unknown trait:" + id);
        });
        return result;
    }

    public CompoundTag save() {
        if (future != null) return future.copy();
        CompoundTag tag = new CompoundTag();
        tag.putInt("V", 1);
        tag.putBoolean("Rolled", rolled);
        if (!variant.isEmpty()) tag.putString("Variant", variant);
        if (!presentation.name().isEmpty()) tag.putString("Name", presentation.name());
        tag.putString("Rarity", presentation.rarity().name().toLowerCase(Locale.ROOT));
        ListTag list = new ListTag();
        traits.forEach(id -> list.add(StringTag.valueOf(id.toString())));
        tag.put("Traits", list);
        if (!presentation.visual().isEmpty()) tag.putString("Visual", presentation.visual());
        tag.putBoolean("RedAura", presentation.redAura());
        return tag;
    }

    public static ShipIndividual load(CompoundTag entity, String shipId) {
        if (!entity.contains(KEY)) return normal(entity.contains("ScaleLevel"));
        if (!entity.contains(KEY, Tag.TAG_COMPOUND)) {
            warn(shipId, KEY);
            return normal(true);
        }
        CompoundTag tag = entity.getCompound(KEY);
        if (tag.contains("V", Tag.TAG_INT) && tag.getInt("V") != 1) {
            warn(shipId, "version:" + tag.getInt("V"));
            return new ShipIndividual(true, "", Presentation.NORMAL, List.of(), tag);
        }
        if (!tag.contains("V", Tag.TAG_INT)) warn(shipId, "V");
        boolean rolled = bool(tag, "Rolled", true, shipId);
        String variant = text(tag, "Variant", 32, shipId);
        if (!variant.isEmpty() && !variant.matches("[a-z0-9_]{1,32}")) {
            warn(shipId, "Variant:" + variant);
            variant = "";
        }
        String name = text(tag, "Name", 128, shipId);
        String visual = text(tag, "Visual", 128, shipId);
        if (!visual.isEmpty() && ResourceLocation.tryParse(visual) == null) {
            warn(shipId, "Visual:" + visual);
            visual = "";
        }
        HostileVariants.Rarity rarity = HostileVariants.Rarity.COMMON;
        String rarityName = text(tag, "Rarity", 128, shipId);
        if (!rarityName.isEmpty()) {
            try {
                rarity = HostileVariants.Rarity.valueOf(rarityName.toUpperCase(Locale.ROOT));
                if (!rarityName.equals(rarityName.toLowerCase(Locale.ROOT))) throw new IllegalArgumentException();
            } catch (IllegalArgumentException error) {
                warn(shipId, "Rarity:" + rarityName);
                rarity = HostileVariants.Rarity.COMMON;
            }
        }
        List<ResourceLocation> traits = new ArrayList<>();
        if (tag.contains("Traits")) {
            if (!(tag.get("Traits") instanceof ListTag list) || list.size() > 4) {
                warn(shipId, "Traits");
            } else {
                Set<ResourceLocation> seen = new HashSet<>();
                for (Tag entry : list) {
                    String value = entry.getAsString();
                    ResourceLocation id = entry instanceof StringTag && value.length() <= 128
                            ? ResourceLocation.tryParse(value) : null;
                    if (id == null) warn(shipId, "Traits");
                    else if (!seen.add(id)) warn(shipId, "Traits:" + id);
                    else traits.add(id);
                }
            }
        }
        return new ShipIndividual(rolled, variant, new Presentation(name, rarity, visual,
                bool(tag, "RedAura", false, shipId)), traits, null);
    }

    /** Re-resolve effects every calculation, never mutate the saved snapshot. */
    public float[] hostileMultipliers(ShipDefinition definition) {
        float[] result = ShipDefinitionAttributes.columns(definition.hostileModifiers());
        traits.stream().sorted().forEach(id -> {
            if (ShipTraitRegistries.isSelectableTrait(id)) {
                var trait = ModShipBehaviors.TRAIT_REGISTRY.get().getValue(id);
                result[trait.attribute().ordinal()] *= (float) trait.multiplier();
            } else warn(definition.id().toString(), "unknown trait:" + id);
        });
        if (!variant.isEmpty()) {
            var current = definition.hostileVariants().get(variant);
            if (current == null) warn(definition.id().toString(), "unknown variant:" + variant);
            else {
                float[] encounter = ShipDefinitionAttributes.columns(current.hostileModifiers());
                for (int i = 0; i < result.length; i++) result[i] *= encounter[i];
            }
        }
        return result;
    }

    private static String text(CompoundTag tag, String key, int max, String shipId) {
        if (!tag.contains(key)) return "";
        if (tag.contains(key, Tag.TAG_STRING)) {
            String value = tag.getString(key);
            if (!value.isEmpty() && value.length() <= max) return value;
        }
        warn(shipId, key);
        return "";
    }

    private static boolean bool(CompoundTag tag, String key, boolean fallback, String shipId) {
        if (!tag.contains(key)) return fallback;
        if (tag.contains(key, Tag.TAG_BYTE) && (tag.getByte(key) == 0 || tag.getByte(key) == 1)) {
            return tag.getBoolean(key);
        }
        warn(shipId, key);
        return fallback;
    }

    private static void warn(String shipId, String value) {
        // Diagnostics must not grow without bound from arbitrary save inputs.
        String key = shipId + ":" + value;
        if (REPORTED.size() < 4096 && REPORTED.add(key)) {
            ShinColle.LOGGER.warn("Invalid or unavailable ship individual value {} for {}; retaining valid fields", value, shipId);
        }
    }

    /** The only individual fields sent to clients. No traits or encounter identity. */
    public record Presentation(String name, HostileVariants.Rarity rarity, String visual, boolean redAura) {
        public static final Presentation NORMAL = new Presentation("", HostileVariants.Rarity.COMMON, "", false);
    }
}
