package com.lulan.shincolle.attribute;

import com.lulan.shincolle.api.attribute.ShipAttributeCombineContext;
import com.lulan.shincolle.api.attribute.ShipAttributeLayout;
import com.lulan.shincolle.api.attribute.ShipAttributeScaleGroup;
import com.lulan.shincolle.api.attribute.ShipAttributeType;
import com.lulan.shincolle.api.attribute.ShipAttributeValues;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;

/** Authoritative, layout-driven combination of all ship attribute layers. */
public final class ShipAttributeLayerEngine {

    private ShipAttributeLayerEngine() {
    }

    public static ShipAttributeValues combine(ShipAttributeValues raw, ShipAttributeValues equipment,
                                              ShipAttributeValues morale, ShipAttributeValues potion,
                                              ShipAttributeValues formation,
                                              Function<ShipAttributeScaleGroup, Float> scaleResolver,
                                              ToDoubleFunction<ResourceLocation> maximumResolver,
                                              BiConsumer<ResourceLocation, RuntimeException> errorHandler) {
        ShipAttributeLayout layout = requireSameLayout(List.of(raw, equipment, morale, potion, formation));
        Objects.requireNonNull(scaleResolver, "scaleResolver");
        Objects.requireNonNull(maximumResolver, "maximumResolver");
        Objects.requireNonNull(errorHandler, "errorHandler");

        ShipAttributeValues.Builder result = ShipAttributeValues.builder(layout);
        for (ResourceLocation id : layout.ids()) {
            ShipAttributeType type = layout.type(id);
            try {
                float scale = Objects.requireNonNull(scaleResolver.apply(type.scaleGroup()),
                        "resolved attribute scale");
                ShipAttributeCombineContext context = new ShipAttributeCombineContext(
                        raw.get(id), equipment.get(id), morale.get(id), potion.get(id), formation.get(id), scale);
                float value = type.combineUnbounded(context);
                double maximum = maximumResolver.applyAsDouble(id);
                if (!Double.isFinite(maximum)) {
                    throw new IllegalArgumentException("Ship attribute maximum must be finite");
                }
                if (maximum >= 0D && value > maximum) {
                    value = (float) maximum;
                }
                result.set(id, type.constrain(value));
            } catch (RuntimeException error) {
                errorHandler.accept(id, error);
                result.set(id, safeFallback(type));
            }
        }
        return result.build();
    }

    private static ShipAttributeLayout requireSameLayout(List<ShipAttributeValues> layers) {
        ShipAttributeLayout layout = Objects.requireNonNull(layers.get(0), "raw").layout();
        for (ShipAttributeValues layer : layers) {
            Objects.requireNonNull(layer, "attribute layer");
            if (!layout.ids().equals(layer.layout().ids())) {
                throw new IllegalArgumentException("Ship attribute layers use different layouts");
            }
        }
        return layout;
    }

    private static float safeFallback(ShipAttributeType type) {
        float fallback = type.defaultValue(com.lulan.shincolle.api.attribute.ShipAttributeLayer.RAW);
        try {
            return type.constrain(fallback);
        } catch (RuntimeException ignored) {
            return 0F;
        }
    }
}
