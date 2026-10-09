package com.lulan.shincolle.ai.domain;

/**
 * What a raw legacy integer decodes to at the boundary. An unknown value is kept as it was read and is
 * never written back, so a value this version does not know survives a save.
 */
public sealed interface LegacyDecodeResult<T> {
    record Known<T>(T value) implements LegacyDecodeResult<T> {
    }

    record Unknown<T>(int raw) implements LegacyDecodeResult<T> {
    }
}
