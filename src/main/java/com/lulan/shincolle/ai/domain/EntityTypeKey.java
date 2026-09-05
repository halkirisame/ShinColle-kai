package com.lulan.shincolle.ai.domain;

import java.util.Objects;
import java.util.regex.Pattern;

public record EntityTypeKey(String namespace, String path) {
    private static final Pattern NAMESPACE = Pattern.compile("[a-z0-9_.-]+");
    private static final Pattern PATH = Pattern.compile("[a-z0-9/._-]+");

    public EntityTypeKey {
        Objects.requireNonNull(namespace, "namespace");
        Objects.requireNonNull(path, "path");
        if (!NAMESPACE.matcher(namespace).matches()) {
            throw new IllegalArgumentException("Invalid entity type namespace: " + namespace);
        }
        if (!PATH.matcher(path).matches()) {
            throw new IllegalArgumentException("Invalid entity type path: " + path);
        }
    }

    @Override
    public String toString() {
        return this.namespace + ":" + this.path;
    }
}
