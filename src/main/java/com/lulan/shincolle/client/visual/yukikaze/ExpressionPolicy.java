package com.lulan.shincolle.client.visual.yukikaze;

/** Animation names and ticks are provided by GeckoLib, not Minecraft internals. */
public final class ExpressionPolicy {
    private ExpressionPolicy() { }

    public record Expression(int face, int mouth, boolean flush, boolean exhausted) { }

    public static Expression select(int face, int mouth, boolean flush, String clip, double tick, long seed) {
        // Native ShipModelBaseAdv.setFaceHungry: Face4 plus Mouth2.
        if (clip.endsWith(".no_fuel")) return new Expression(4, 2, false, true);
        if (clip.endsWith(".caress")) return new Expression(3, 1, true, false);
        if (clip.endsWith(".light_attack") || clip.endsWith(".heavy_attack")) {
            return new Expression(2, 1, flush, false);
        }
        if (clip.endsWith(".sprint")) return new Expression(2, 0, flush, false);
        double phase = (tick + Math.floorMod(seed * 37, 120)) % 120;
        if (face == 0 && phase >= 116) face = 1;
        return new Expression(face, mouth, flush, false);
    }
}
