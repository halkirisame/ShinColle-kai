package com.lulan.shincolle.equipdata;

/** Controls which automatic acquisition routes may select an equipment definition. */
public enum EquipAvailability {
    ANY("any", true, true, false),
    SHIPYARD_ONLY("shipyard_only", true, false, false),
    TREASURE_ONLY("treasure_only", false, true, false),
    UNOBTAINABLE("unobtainable", false, false, true);

    private final String jsonName;
    private final boolean developable;
    private final boolean lootable;
    private final boolean hidden;

    EquipAvailability(String jsonName, boolean developable, boolean lootable, boolean hidden) {
        this.jsonName = jsonName;
        this.developable = developable;
        this.lootable = lootable;
        this.hidden = hidden;
    }

    public String jsonName() {
        return jsonName;
    }

    public boolean canDevelop() {
        return developable;
    }

    public boolean canLoot() {
        return lootable;
    }

    public boolean isHidden() {
        return hidden;
    }

    public static EquipAvailability fromJsonName(String jsonName) {
        for (EquipAvailability availability : values()) {
            if (availability.jsonName.equals(jsonName)) {
                return availability;
            }
        }
        throw new IllegalArgumentException("unknown availability '" + jsonName + "'");
    }
}
