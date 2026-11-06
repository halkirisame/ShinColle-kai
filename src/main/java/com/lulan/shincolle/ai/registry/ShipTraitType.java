package com.lulan.shincolle.ai.registry;

import com.lulan.shincolle.api.attribute.CoreShipAttributes;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Objects;

/** Internal immutable trait metadata. Registration does not apply any attribute effects. */
public record ShipTraitType(Attribute attribute, double multiplier) {
    public ShipTraitType {
        Objects.requireNonNull(attribute, "attribute");
        if (!Double.isFinite(multiplier) || multiplier <= 0) {
            throw new IllegalArgumentException("trait multiplier must be finite and positive");
        }
    }

    /** Base-table columns; attack covers all four existing attack attribute IDs. */
    public enum Attribute {
        HP, ATK, DEF, SPD, MOV, HIT;

        public List<ResourceLocation> attributeIds() {
            return switch (this) {
                case HP -> List.of(CoreShipAttributes.HP);
                case ATK -> List.of(CoreShipAttributes.ATK_L, CoreShipAttributes.ATK_H,
                        CoreShipAttributes.ATK_AL, CoreShipAttributes.ATK_AH);
                case DEF -> List.of(CoreShipAttributes.DEF);
                case SPD -> List.of(CoreShipAttributes.SPD);
                case MOV -> List.of(CoreShipAttributes.MOV);
                case HIT -> List.of(CoreShipAttributes.HIT);
            };
        }
    }
}
