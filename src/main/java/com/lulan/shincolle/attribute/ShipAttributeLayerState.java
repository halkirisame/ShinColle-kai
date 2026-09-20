package com.lulan.shincolle.attribute;

import com.lulan.shincolle.api.attribute.ShipAttributeLayer;
import com.lulan.shincolle.api.attribute.ShipAttributeLayout;
import com.lulan.shincolle.api.attribute.ShipAttributeValues;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/** Internal mutable holder whose externally visible layer values are immutable snapshots. */
public final class ShipAttributeLayerState {

    private final ShipAttributeLayout layout;
    private final EnumMap<ShipAttributeLayer, ShipAttributeValues> layers =
            new EnumMap<>(ShipAttributeLayer.class);

    public ShipAttributeLayerState(ShipAttributeLayout layout) {
        this.layout = Objects.requireNonNull(layout, "layout");
        for (ShipAttributeLayer layer : ShipAttributeLayer.values()) {
            ShipAttributeValues initial = layer == ShipAttributeLayer.BUFFED
                    ? ShipAttributeValues.defaults(layout, ShipAttributeLayer.RAW)
                    : ShipAttributeValues.defaults(layout, layer);
            this.layers.put(layer, initial);
        }
    }

    private ShipAttributeLayerState(ShipAttributeLayout layout,
                                    Map<ShipAttributeLayer, ShipAttributeValues> source) {
        this.layout = layout;
        this.layers.putAll(source);
    }

    public ShipAttributeLayout layout() {
        return this.layout;
    }

    public ShipAttributeValues get(ShipAttributeLayer layer) {
        return this.layers.get(Objects.requireNonNull(layer, "layer"));
    }

    public void set(ShipAttributeLayer layer, ShipAttributeValues values) {
        Objects.requireNonNull(layer, "layer");
        Objects.requireNonNull(values, "values");
        if (!this.layout.ids().equals(values.layout().ids())) {
            throw new IllegalArgumentException("Ship attribute layer layout does not match canonical layout");
        }
        ShipAttributeValues.Builder rebased = ShipAttributeValues.builder(this.layout);
        for (var id : this.layout.ids()) {
            rebased.set(id, values.get(id));
        }
        this.layers.put(layer, rebased.build());
    }

    public void reset(ShipAttributeLayer layer) {
        Objects.requireNonNull(layer, "layer");
        ShipAttributeValues reset = layer == ShipAttributeLayer.BUFFED
                ? ShipAttributeValues.defaults(this.layout, ShipAttributeLayer.RAW)
                : ShipAttributeValues.defaults(this.layout, layer);
        this.layers.put(layer, reset);
    }

    public ShipAttributeLayerState copy() {
        return new ShipAttributeLayerState(this.layout, this.layers);
    }
}
