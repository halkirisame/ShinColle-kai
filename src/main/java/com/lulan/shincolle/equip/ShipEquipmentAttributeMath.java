package com.lulan.shincolle.equip;

import com.lulan.shincolle.api.attribute.ShipAttributeLayout;
import com.lulan.shincolle.api.attribute.ShipAttributeScaleGroup;
import com.lulan.shincolle.api.attribute.ShipAttributeValues;
import com.lulan.shincolle.handler.ConfigHandler;
import com.lulan.shincolle.reference.ID;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/** Finite, whole-stack arithmetic shared by native and Curios equipment aggregation. */
public final class ShipEquipmentAttributeMath {

    private ShipEquipmentAttributeMath() {
    }

    public static ShipAttributeValues add(ShipAttributeValues base, ShipAttributeValues contribution) {
        ShipAttributeLayout layout = requireSameLayout(base, contribution);
        ShipAttributeValues.Builder result = ShipAttributeValues.builder(layout);
        for (ResourceLocation id : layout.ids()) {
            float value = base.get(id) + contribution.get(id);
            if (!Float.isFinite(value)) {
                throw new IllegalArgumentException("Equipment attribute addition overflow for " + id);
            }
            result.set(id, value);
        }
        return result.build();
    }

    public static ShipAttributeValues scale(ShipAttributeValues values) {
        ShipAttributeLayout layout = values.layout();
        ShipAttributeValues.Builder result = ShipAttributeValues.builder(layout);
        for (ResourceLocation id : layout.ids()) {
            float value = values.get(id) * resolveScale(layout.type(id).scaleGroup());
            if (!Float.isFinite(value)) {
                throw new IllegalArgumentException("Equipment attribute scaling overflow for " + id);
            }
            result.set(id, value);
        }
        return result.build();
    }

    public static ShipAttributeValues addScaled(ShipAttributeValues base, ShipAttributeValues contribution) {
        return add(base, scale(contribution));
    }

    public static ShipAttributeValues withMinimum(ShipAttributeValues values, ResourceLocation id, float minimum) {
        if (!Float.isFinite(minimum)) {
            throw new IllegalArgumentException("Equipment attribute minimum must be finite");
        }
        return values.toBuilder().set(id, Math.max(values.get(id), minimum)).build();
    }

    public static float resolveScale(ShipAttributeScaleGroup group) {
        return switch (Objects.requireNonNull(group, "group")) {
            case HP -> (float) ConfigHandler.scaleShip[ID.AttrsBase.HP];
            case ATK -> (float) ConfigHandler.scaleShip[ID.AttrsBase.ATK];
            case DEF -> (float) ConfigHandler.scaleShip[ID.AttrsBase.DEF];
            case SPD -> (float) ConfigHandler.scaleShip[ID.AttrsBase.SPD];
            case MOV -> (float) ConfigHandler.scaleShip[ID.AttrsBase.MOV];
            case HIT -> (float) ConfigHandler.scaleShip[ID.AttrsBase.HIT];
            case NONE -> 1F;
        };
    }

    private static ShipAttributeLayout requireSameLayout(ShipAttributeValues first,
                                                         ShipAttributeValues second) {
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");
        if (!first.layout().ids().equals(second.layout().ids())) {
            throw new IllegalArgumentException("Equipment attribute layouts do not match");
        }
        return first.layout();
    }
}
