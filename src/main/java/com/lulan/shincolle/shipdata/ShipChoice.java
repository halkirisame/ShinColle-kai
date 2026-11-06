package com.lulan.shincolle.shipdata;

import java.util.Objects;

/** Internal acquisition result: numeric built-in identity or file-derived ship identity. */
public sealed interface ShipChoice {
    record BuiltIn(int shipClass) implements ShipChoice {
    }

    record Defined(ShipDefinitionId id) implements ShipChoice {
        public Defined {
            Objects.requireNonNull(id, "id");
        }
    }
}
