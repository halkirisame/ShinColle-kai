package com.lulan.shincolle.visualdata;

/** Drawing-size class of a held item; the factors multiply the configured held-item scale. */
public enum ShipHeldItemKind {
    TOOL(0.5F, 1F, 1F),
    BLOCK(0.75F, 0.75F, 0.75F),
    OTHER(1F, 1F, 1F);

    private final float x;
    private final float y;
    private final float z;

    ShipHeldItemKind(float x, float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public float scaleX(double configured) {
        return (float) configured * x;
    }

    public float scaleY(double configured) {
        return (float) configured * y;
    }

    public float scaleZ(double configured) {
        return (float) configured * z;
    }
}
