package com.lulan.shincolle.ai.domain;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.regex.Pattern;

/** Immutable, structured explanation emitted by pure AI decisions. */
public record AiDebugReason(String code, Map<String, String> attributes) {

    private static final Pattern NAME_PATTERN =
            Pattern.compile("[a-z][a-z0-9]*(?:_[a-z0-9]+)*");

    public AiDebugReason {
        requireValidName(code, "code");
        Objects.requireNonNull(attributes, "attributes");

        TreeMap<String, String> copy = new TreeMap<>();
        for (Map.Entry<String, String> entry : attributes.entrySet()) {
            String key = entry.getKey();
            requireValidName(key, "attribute key");
            copy.put(key, Objects.requireNonNull(entry.getValue(), "attribute value"));
        }
        attributes = Collections.unmodifiableMap(copy);
    }

    public static AiDebugReason of(String code) {
        return new AiDebugReason(code, Map.of());
    }

    private static void requireValidName(String value, String label) {
        Objects.requireNonNull(value, label);
        if (!NAME_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException(label + " must be lower snake case");
        }
    }
}
