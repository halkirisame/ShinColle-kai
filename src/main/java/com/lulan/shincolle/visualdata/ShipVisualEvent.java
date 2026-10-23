package com.lulan.shincolle.visualdata;

/** Semantic presentation events, independent of rendering libraries and clip names. */
public enum ShipVisualEvent {
    IDLE("idle"), MOVE("move"), SIT("sit"), LIGHT_ATTACK("light_attack"), HEAVY_ATTACK("heavy_attack"),
    SPRINT("sprint"), RIDE("ride"), CARESS("caress"), NO_FUEL("no_fuel");

    private final String key;

    ShipVisualEvent(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public boolean isAttack() {
        return this == LIGHT_ATTACK || this == HEAVY_ATTACK;
    }

    public static ShipVisualEvent locomotion(boolean sitting, boolean moving) {
        return sitting ? SIT : moving ? MOVE : IDLE;
    }

    public static ShipVisualEvent attack(int attackType) {
        return attackType == 2 ? HEAVY_ATTACK : LIGHT_ATTACK;
    }
}
