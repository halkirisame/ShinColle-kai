package com.lulan.shincolle.reference.unitclass;

import com.lulan.shincolle.api.attribute.ShipAttributeLayer;
import com.lulan.shincolle.api.attribute.ShipAttributeValues;
import com.lulan.shincolle.api.attribute.CoreShipAttributes;
import com.lulan.shincolle.attribute.ShipAttributeLayerState;
import com.lulan.shincolle.reference.ID;
import com.lulan.shincolle.utility.FormationHelper;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;

/**
 * ship basic attributes + equip, potion, formation and morale buffs
 */
public class AttrsAdv extends Attrs {

    /**
     * formation effect, index: {@link ID.Attrs}
     */
    protected float[] AttrsFormation;
    /**
     * morale buff, index: {@link ID.Attrs}
     */
    protected float[] AttrsMorale;
    /**
     * min MOV in formation team
     */
    protected float MinMOV;

    public AttrsAdv() {
        super();
    }

    public AttrsAdv(int shipClass) {
        super(shipClass);
    }

    public static float[] getResetFormationValue() {
        return new float[]{0F, 1F, 1F, 1F, 1F,
                1F, 1F, 0F, 0F, 1F,
                1F, 1F, 1F, 1F, 1F,
                0F, 0F, 0F, 0F, 0F,
                0F};
    }

    public static float[] getResetMoraleValue() {
        return new float[]{0F, 1F, 1F, 1F, 1F,
                0F, 1F, 0F, 0F, 1F,
                1F, 1F, 1F, 1F, 1F,
                0F, 0F, 0F, 0F, 0F,
                0F};
    }

    /**
     * make a object copy
     */
    public static AttrsAdv copyAttrsAdv(AttrsAdv attrs) {
        AttrsAdv newattrs = new AttrsAdv();

        newattrs.setAttrsBonus(Arrays.copyOf(attrs.getAttrsBonus(), attrs.getAttrsBonus().length));
        newattrs.setAttrsType(Arrays.copyOf(attrs.getAttrsType(), attrs.getAttrsType().length));
        newattrs.setAttrsRaw(Arrays.copyOf(attrs.getAttrsRaw(), attrs.getAttrsRaw().length));
        newattrs.setAttrsEquip(Arrays.copyOf(attrs.getAttrsEquip(), attrs.getAttrsEquip().length));
        newattrs.setAttrsPotion(Arrays.copyOf(attrs.getAttrsPotion(), attrs.getAttrsPotion().length));
        newattrs.setAttrsBuffed(Arrays.copyOf(attrs.getAttrsBuffed(), attrs.getAttrsBuffed().length));
        newattrs.setAttrsMorale(Arrays.copyOf(attrs.getAttrsMorale(), attrs.getAttrsMorale().length));
        newattrs.setAttrsFormation(Arrays.copyOf(attrs.getAttrsFormation(), attrs.getAttrsFormation().length));
        newattrs.setMinMOV(attrs.getMinMOV());
        newattrs.copyDynamicLayersFrom(attrs);

        return newattrs;
    }

    @Override
    public void initValue() {
        super.initValue();
        this.resetAttrsMorale();
        this.resetAttrsFormation();
    }

    /* reset formation buff to zero */
    public void resetAttrsFormation() {
        this.AttrsFormation = getResetFormationValue();
        this.MinMOV = 0F;
        this.replaceDynamicLayerFromLegacy(ShipAttributeLayer.FORMATION, this.AttrsFormation);
    }

    public void resetAttrsMorale() {
        this.AttrsMorale = getResetMoraleValue();
        this.replaceDynamicLayerFromLegacy(ShipAttributeLayer.MORALE, this.AttrsMorale);
    }

    /**
     * getter
     */
    public float[] getAttrsFormation() {
        return this.AttrsFormation;
    }

    /**
     * setter
     */
    public void setAttrsFormation(float[] data) {
        this.AttrsFormation = data;
        this.replaceDynamicLayerFromLegacy(ShipAttributeLayer.FORMATION, data);
    }

    public float getAttrsFormation(int id) {
        return this.AttrsFormation[id];
    }

    public float getMinMOV() {
        return this.MinMOV;
    }

    public void setMinMOV(float data) {
        this.MinMOV = data;
    }

    public float[] getAttrsMorale() {
        return this.AttrsMorale;
    }

    public void setAttrsMorale(float[] data) {
        this.AttrsMorale = data;
        this.replaceDynamicLayerFromLegacy(ShipAttributeLayer.MORALE, data);
    }

    public float getAttrsMorale(int id) {
        return this.AttrsMorale[id];
    }

    public void setAttrsFormation(int id, float data) {
        this.AttrsFormation[id] = data;
    }

    /* set formation buff by formation id and slot */
    public void setAttrsFormation(int formatID, int formatSlot) {
        this.setAttrsFormation(FormationHelper.getFormationBuffValue(formatID, formatSlot));
    }

    /** Validates and atomically installs one decoded client synchronization snapshot. */
    public boolean applySyncedShipAttributes(long revision, byte[] bonus,
                                             Map<ShipAttributeLayer, ShipAttributeValues> layers,
                                             Float minMOV) {
        if (revision <= this.lastAppliedAttributeSyncRevision()) {
            return false;
        }
        if (revision < 0) {
            throw new IllegalArgumentException("Ship attribute sync revision must be non-negative");
        }
        if (bonus != null && bonus.length != this.AttrsBonus.length) {
            throw new IllegalArgumentException("Ship attribute bonus length does not match");
        }
        if (minMOV != null && !Float.isFinite(minMOV)) {
            throw new IllegalArgumentException("MinMOV must be finite");
        }

        ShipAttributeLayerState candidate = this.ensureDynamicLayers().copy();
        EnumMap<ShipAttributeLayer, float[]> legacy = new EnumMap<>(ShipAttributeLayer.class);
        for (Map.Entry<ShipAttributeLayer, ShipAttributeValues> entry : layers.entrySet()) {
            candidate.set(entry.getKey(), entry.getValue());
            float[] values = new float[AttrsLength];
            for (int i = 0; i < CoreShipAttributes.LEGACY_ORDER.size(); i++) {
                values[i] = entry.getValue().get(CoreShipAttributes.LEGACY_ORDER.get(i));
            }
            legacy.put(entry.getKey(), values);
        }

        if (bonus != null) {
            this.AttrsBonus = bonus.clone();
        }
        legacy.forEach(this::replaceLegacyLayer);
        if (minMOV != null) {
            this.MinMOV = minMOV;
        }
        this.installDynamicLayers(candidate);
        this.markAttributeSyncRevisionApplied(revision);
        return true;
    }

    @Override
    protected float[] legacyLayer(ShipAttributeLayer layer) {
        return switch (layer) {
            case MORALE -> this.AttrsMorale;
            case FORMATION -> this.AttrsFormation;
            default -> super.legacyLayer(layer);
        };
    }

    @Override
    protected void replaceLegacyLayer(ShipAttributeLayer layer, float[] values) {
        switch (layer) {
            case MORALE -> this.AttrsMorale = values;
            case FORMATION -> this.AttrsFormation = values;
            default -> super.replaceLegacyLayer(layer, values);
        }
    }

}
