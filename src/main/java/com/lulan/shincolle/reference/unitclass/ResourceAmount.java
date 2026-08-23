package com.lulan.shincolle.reference.unitclass;

import java.util.Objects;

/**
 * Immutable amount of the four shipyard resources.
 *
 * @param grudge    grudge amount
 * @param abyssium  abyssal metal amount
 * @param ammo      ammunition amount
 * @param polymetal polymetal amount
 */
public record ResourceAmount(int grudge, int abyssium, int ammo, int polymetal) {

    public static final ResourceAmount ZERO = new ResourceAmount(0, 0, 0, 0);

    /**
     * Convert the legacy resource array order into an immutable amount.
     *
     * @param values values ordered as grudge, abyssium, ammo, polymetal
     * @return immutable resource amount
     */
    public static ResourceAmount fromArray(int[] values) {
        if (values == null || values.length != 4) {
            throw new IllegalArgumentException("Resource values must contain exactly four entries.");
        }
        return new ResourceAmount(values[0], values[1], values[2], values[3]);
    }

    /**
     * Convert to the legacy resource array order.
     *
     * @return a new array ordered as grudge, abyssium, ammo, polymetal
     */
    public int[] toArray() {
        return new int[]{this.grudge, this.abyssium, this.ammo, this.polymetal};
    }

    /**
     * Add another amount, rejecting integer overflow.
     */
    public ResourceAmount plus(ResourceAmount other) {
        Objects.requireNonNull(other, "other");
        return new ResourceAmount(
                Math.addExact(this.grudge, other.grudge),
                Math.addExact(this.abyssium, other.abyssium),
                Math.addExact(this.ammo, other.ammo),
                Math.addExact(this.polymetal, other.polymetal));
    }

    /**
     * Multiply every resource, rejecting integer overflow.
     */
    public ResourceAmount times(int multiplier) {
        return new ResourceAmount(
                Math.multiplyExact(this.grudge, multiplier),
                Math.multiplyExact(this.abyssium, multiplier),
                Math.multiplyExact(this.ammo, multiplier),
                Math.multiplyExact(this.polymetal, multiplier));
    }

    /**
     * Check that every resource amount can be accepted by a shipyard.
     */
    public boolean isNonNegative() {
        return this.grudge >= 0 && this.abyssium >= 0 && this.ammo >= 0 && this.polymetal >= 0;
    }
}
