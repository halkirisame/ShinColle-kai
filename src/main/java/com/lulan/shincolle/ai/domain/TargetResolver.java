package com.lulan.shincolle.ai.domain;

import java.util.Optional;

/** Resolves a stable target identity without prescribing a runtime entity representation. */
@FunctionalInterface
public interface TargetResolver<T> {

    Optional<T> resolve(TargetHandle handle);
}
