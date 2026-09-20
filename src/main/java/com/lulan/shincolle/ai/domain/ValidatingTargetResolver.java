package com.lulan.shincolle.ai.domain;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;

/** Pure resolver that validates dimension, loaded identity, and runtime validity. */
public final class ValidatingTargetResolver<T> implements TargetResolver<T> {

    private final DimensionKey dimension;
    private final Function<UUID, Optional<T>> lookup;
    private final Function<T, UUID> identity;
    private final Predicate<T> valid;

    public ValidatingTargetResolver(
            DimensionKey dimension,
            Function<UUID, Optional<T>> lookup,
            Function<T, UUID> identity,
            Predicate<T> valid) {
        this.dimension = Objects.requireNonNull(dimension, "dimension");
        this.lookup = Objects.requireNonNull(lookup, "lookup");
        this.identity = Objects.requireNonNull(identity, "identity");
        this.valid = Objects.requireNonNull(valid, "valid");
    }

    @Override
    public Optional<T> resolve(TargetHandle handle) {
        Objects.requireNonNull(handle, "handle");
        if (!this.dimension.equals(handle.dimension())) {
            return Optional.empty();
        }
        Optional<T> resolved = Objects.requireNonNull(this.lookup.apply(handle.uuid()), "lookup result");
        return resolved.filter(value -> handle.uuid().equals(this.identity.apply(value)))
                .filter(this.valid);
    }
}
