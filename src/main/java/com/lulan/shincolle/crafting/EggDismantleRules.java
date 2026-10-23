package com.lulan.shincolle.crafting;

import java.util.Objects;

/**
 * Pure decision for dismantling a ship egg into instant construction material.
 * <p>
 * Knows nothing about Minecraft: the adapter reads an item stack into an
 * {@link EggSnapshot}, and this class only decides what the snapshot may become.
 * The rule is closed: anything the schema does not explicitly describe is rejected,
 * so a saved ship (inventory, equipment, owner) can never be consumed by mistake.
 */
public final class EggDismantleRules {

    /** What the egg says about its construction size. */
    public enum BuildTypeField {
        MISSING, SMALL, LARGE, INVALID
    }

    /** What the egg says about a specific ship class. */
    public enum ShipClassField {
        ABSENT, VALID_FRIENDLY, INVALID
    }

    /** Result of the decision and the amount of material it yields. */
    public enum EggDismantleKind {
        REJECTED(0), SMALL(8), LARGE(64);

        private final int yield;

        EggDismantleKind(int yield) {
            this.yield = yield;
        }

        /** Number of construction materials produced; 0 when rejected. */
        public int yield() {
            return this.yield;
        }
    }

    /**
     * Immutable reading of an egg.
     *
     * @param buildType     construction size marker
     * @param shipClass     specific ship class marker
     * @param schemaValid   every key and value type is one the closed schema allows
     * @param extraPayload  anything outside the schema is present (saved ship data, unknown keys,
     *                      serialized capabilities)
     */
    public record EggSnapshot(BuildTypeField buildType, ShipClassField shipClass, boolean schemaValid,
                              boolean extraPayload) {

        public EggSnapshot {
            Objects.requireNonNull(buildType, "buildType");
            Objects.requireNonNull(shipClass, "shipClass");
        }
    }

    private EggDismantleRules() {
    }

    public static EggDismantleKind classify(EggSnapshot egg) {
        Objects.requireNonNull(egg, "egg");
        if (!egg.schemaValid() || egg.extraPayload()) {
            return EggDismantleKind.REJECTED;
        }
        if (egg.shipClass() == ShipClassField.INVALID) {
            return EggDismantleKind.REJECTED;
        }
        return switch (egg.buildType()) {
            case SMALL -> EggDismantleKind.SMALL;
            case LARGE -> EggDismantleKind.LARGE;
            case MISSING, INVALID -> EggDismantleKind.REJECTED;
        };
    }
}
