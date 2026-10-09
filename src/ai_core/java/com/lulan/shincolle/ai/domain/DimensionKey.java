package com.lulan.shincolle.ai.domain;

import java.util.Objects;
import java.util.regex.Pattern;

public record DimensionKey(String namespace, String path) implements Comparable<DimensionKey> {
    private static final Pattern NAMESPACE = Pattern.compile("[a-z0-9_.-]+");
    private static final Pattern PATH = Pattern.compile("[a-z0-9/._-]+");

    public DimensionKey {
        Objects.requireNonNull(namespace, "namespace");
        Objects.requireNonNull(path, "path");
        if (!NAMESPACE.matcher(namespace).matches()) {
            throw new IllegalArgumentException("Invalid dimension namespace: " + namespace);
        }
        if (!PATH.matcher(path).matches()) {
            throw new IllegalArgumentException("Invalid dimension path: " + path);
        }
    }

    @Override
    public int compareTo(DimensionKey other) {
        Objects.requireNonNull(other, "other");
        int namespaceOrder = this.namespace.compareTo(other.namespace);
        return namespaceOrder != 0 ? namespaceOrder : this.path.compareTo(other.path);
    }

    @Override
    public String toString() {
        return this.namespace + ":" + this.path;
    }
}
