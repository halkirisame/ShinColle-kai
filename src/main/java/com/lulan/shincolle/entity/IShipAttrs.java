package com.lulan.shincolle.entity;

import com.lulan.shincolle.api.attribute.ShipAttributeAccess;
import com.lulan.shincolle.api.attribute.ShipAttributeLayer;
import com.lulan.shincolle.api.attribute.ShipAttributeValues;
import com.lulan.shincolle.reference.unitclass.Attrs;

public interface IShipAttrs extends ShipAttributeAccess {
    Attrs getAttrs();

    void setAttrs(Attrs data);

    @Override
    default ShipAttributeValues shipAttributes(ShipAttributeLayer layer) {
        Attrs attrs = getAttrs();
        if (attrs == null) {
            throw new IllegalStateException("Ship attributes are not initialized");
        }
        return attrs.shipAttributes(layer);
    }
}
