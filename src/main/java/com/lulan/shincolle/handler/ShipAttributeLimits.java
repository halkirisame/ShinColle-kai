package com.lulan.shincolle.handler;

import com.lulan.shincolle.api.attribute.ShipAttributeLayout;
import com.lulan.shincolle.attribute.LegacyShipAttributeBridge;
import net.minecraft.resources.ResourceLocation;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/** Immutable resolved view of legacy and ResourceLocation ship-attribute limits. */
final class ShipAttributeLimits {

    static final int MAX_NAMED_ENTRIES = 4096;
    static final int MAX_ENTRY_LENGTH = 512;
    private static final int MAX_ID_LENGTH = 256;
    private static final double NO_LIMIT = -1D;
    private static final double[] LEGACY_DEFAULTS = {
            -1D, -1D, -1D, -1D, -1D,
            0.8D, 4D, 0.6D, 64D, 0.9D,
            0.9D, 0.9D, 0.9D, -1D, -1D,
            0.75D, -1D, -1D, -1D, -1D,
            1D
    };

    private final double[] legacyLimits;
    private final Map<ResourceLocation, Double> namedLimits;

    private ShipAttributeLimits(double[] legacyLimits, Map<ResourceLocation, Double> namedLimits) {
        this.legacyLimits = legacyLimits.clone();
        this.namedLimits = Map.copyOf(namedLimits);
    }

    static ShipAttributeLimits unlimited() {
        double[] values = new double[LegacyShipAttributeBridge.LEGACY_LENGTH];
        Arrays.fill(values, NO_LIMIT);
        return new ShipAttributeLimits(values, Map.of());
    }

    static ShipAttributeLimits legacyOnly(List<? extends Double> legacyEntries, Consumer<String> warningSink) {
        return new ShipAttributeLimits(resolveLegacy(legacyEntries, warningSink), Map.of());
    }

    static ShipAttributeLimits resolve(List<? extends Double> legacyEntries,
                                       List<? extends String> namedEntries,
                                       ShipAttributeLayout layout,
                                       Consumer<String> warningSink) {
        Objects.requireNonNull(layout, "layout");
        Objects.requireNonNull(namedEntries, "namedEntries");
        Consumer<String> warnings = Objects.requireNonNull(warningSink, "warningSink");
        double[] legacy = resolveLegacy(legacyEntries, warnings);
        Map<ResourceLocation, Double> named = new LinkedHashMap<>();
        int count = Math.min(namedEntries.size(), MAX_NAMED_ENTRIES);
        if (namedEntries.size() > MAX_NAMED_ENTRIES) {
            warnings.accept("limitShipAttributesById contains " + namedEntries.size()
                    + " entries; ignoring entries after " + MAX_NAMED_ENTRIES);
        }
        for (int i = 0; i < count; i++) {
            parseNamedEntry(i, namedEntries.get(i), layout, named, warnings);
        }
        return new ShipAttributeLimits(legacy, named);
    }

    double maximum(ResourceLocation id) {
        Objects.requireNonNull(id, "id");
        Double named = this.namedLimits.get(id);
        if (named != null) {
            return named;
        }
        int legacyIndex = LegacyShipAttributeBridge.legacyIndex(id);
        return legacyIndex < 0 ? NO_LIMIT : this.legacyLimits[legacyIndex];
    }

    double[] legacyLimits() {
        return this.legacyLimits.clone();
    }

    Map<ResourceLocation, Double> namedLimits() {
        return this.namedLimits;
    }

    private static double[] resolveLegacy(List<? extends Double> entries, Consumer<String> warningSink) {
        Objects.requireNonNull(entries, "legacyEntries");
        Consumer<String> warnings = Objects.requireNonNull(warningSink, "warningSink");
        double[] result = LEGACY_DEFAULTS.clone();
        int count = Math.min(result.length, entries.size());
        for (int i = 0; i < count; i++) {
            Double value = entries.get(i);
            if (value == null || !Double.isFinite(value)) {
                warnings.accept("limitShipAttrs[" + i + "] is not finite; using default " + result[i]);
                continue;
            }
            result[i] = value;
        }
        return result;
    }

    private static void parseNamedEntry(int index, String rawEntry, ShipAttributeLayout layout,
                                        Map<ResourceLocation, Double> result, Consumer<String> warnings) {
        if (rawEntry == null) {
            warnings.accept("limitShipAttributesById[" + index + "] is null; ignoring it");
            return;
        }
        String entry = rawEntry.trim();
        if (entry.length() > MAX_ENTRY_LENGTH) {
            warnings.accept("limitShipAttributesById[" + index + "] exceeds " + MAX_ENTRY_LENGTH
                    + " characters; ignoring it");
            return;
        }
        int separator = entry.indexOf('=');
        if (separator <= 0 || separator == entry.length() - 1) {
            warnings.accept("limitShipAttributesById[" + index
                    + "] must use namespace:path=value; ignoring '" + entry + "'");
            return;
        }
        String idText = entry.substring(0, separator).trim();
        String valueText = entry.substring(separator + 1).trim();
        if (idText.length() > MAX_ID_LENGTH) {
            warnings.accept("limitShipAttributesById[" + index + "] attribute ID exceeds "
                    + MAX_ID_LENGTH + " characters; ignoring it");
            return;
        }
        ResourceLocation id = ResourceLocation.tryParse(idText);
        if (id == null) {
            warnings.accept("limitShipAttributesById[" + index + "] has invalid attribute ID '"
                    + idText + "'; ignoring it");
            return;
        }
        if (layout.type(id) == null) {
            warnings.accept("limitShipAttributesById[" + index + "] references unregistered attribute "
                    + id + "; ignoring it");
            return;
        }
        double value;
        try {
            value = Double.parseDouble(valueText);
        } catch (NumberFormatException error) {
            warnings.accept("limitShipAttributesById[" + index + "] has invalid value '"
                    + valueText + "'; ignoring it");
            return;
        }
        if (!Double.isFinite(value) || (value < 0D && value != NO_LIMIT)) {
            warnings.accept("limitShipAttributesById[" + index
                    + "] value must be -1 or a finite non-negative number; ignoring it");
            return;
        }
        if (result.containsKey(id)) {
            warnings.accept("limitShipAttributesById[" + index + "] duplicates " + id
                    + "; keeping the first value");
            return;
        }
        result.put(id, value);
    }
}
