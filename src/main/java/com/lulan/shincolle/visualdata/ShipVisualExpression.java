package com.lulan.shincolle.visualdata;

import com.lulan.shincolle.visualdata.ShipVisualPresentation.Expression;

/** Read-only timed faces. Emotion age is supplied by the client animation instance, not an entity timer. */
public final class ShipVisualExpression {
    public record Input(int tickCount, int shipUid, int emotion, int emotionAge, boolean happyMorale,
                        boolean boredMood, ShipVisualEvent posture, boolean attacking, float partialTick) { }

    private ShipVisualExpression() { }

    public static boolean blinkCycle(int shipUid, long cycle) {
        return ShipVisualRandom.sample(shipUid, cycle, 0x424C494E4BL, 10) > 7;
    }

    public static int phase(int tickCount, int shipUid, int mask) {
        return (tickCount + (shipUid << 7)) & mask;
    }

    public static int duration(int emotion) {
        return switch (emotion) {
            case 2, 7 -> 80;
            case 3 -> 45;
            case 6 -> 40;
            case 8 -> 60;
            default -> -1;
        };
    }

    public static Expression select(Input input) {
        int t = phase(input.tickCount(), input.shipUid(), 511);
        if (input.posture() == ShipVisualEvent.NO_FUEL) return face(4, 2, input.happyMorale());
        if (input.posture() == ShipVisualEvent.CARESS) return shy(t & 255);
        if (input.attacking()) return attack(t, input.happyMorale());
        if ((input.posture() == ShipVisualEvent.SIT || input.posture() == ShipVisualEvent.RIDE) && input.emotion() == 4) {
            Expression bored = bored(t, input.happyMorale());
            return (input.tickCount() & 127) < 64 ? face(3, bored.mouth(), bored.flush()) : bored;
        }
        Expression result = emotion(input, t);
        return input.posture() == ShipVisualEvent.SPRINT ? face(3, result.mouth(), result.flush()) : result;
    }

    private static Expression emotion(Input input, int phase) {
        int emotion = input.emotion();
        int duration = duration(emotion);
        if (duration >= 0 && input.emotionAge() > duration) emotion = 0;
        boolean flush = input.happyMorale();
        int t = phase & 255;
        return switch (emotion) {
            case 1 -> face(blink(input.emotionAge()) ? 1 : 0, 0, flush);
            case 2 -> face(t < 128 ? 6 : 7, t < 64 ? 5 : 2, flush);
            case 3 -> (input.tickCount() & 2047) > 1024 ? damaged(phase, flush) : face(2, 1, flush);
            case 4 -> bored(phase, flush);
            case 5 -> face(4, 2, flush);
            case 6 -> face(t < 128 ? 1 : 2, t < 64 ? 0 : t < 170 ? 1 : 2, flush);
            case 7 -> shy(t);
            case 8 -> face(t < 140 ? 3 : 8, t < 80 ? 0 : 4, true);
            case 9 -> ShipVisualPresentation.emotion(9, flush);
            default -> face(blinkCycle(input.shipUid(), Math.floorDiv(input.tickCount(), 128))
                            && blink(input.tickCount() & 127) ? 1 : 0,
                    input.boredMood() && (input.tickCount() & 255) > 160 ? 3 : 0, flush);
        };
    }

    private static boolean blink(int age) {
        return age >= 0 && (age <= 10 || age > 20 && age <= 25);
    }

    private static Expression attack(int t, boolean flush) {
        if (t < 128) return face(1, t < 64 ? 0 : 2, flush);
        if (t < 256) return face(2, t < 180 ? 0 : 1, flush);
        if (t < 384) return face(3, t < 320 ? 0 : 4, flush);
        return face(8, t < 450 ? 0 : 1, flush);
    }

    private static Expression damaged(int t, boolean flush) {
        if (t < 200) return face(6, t < 60 ? 5 : 2, flush);
        if (t < 400) return face(3, t < 250 ? 0 : 4, flush);
        return face(9, t < 450 ? 0 : 1, flush);
    }

    private static Expression bored(int t, boolean flush) {
        if (t < 170) return face(5, t < 80 ? 0 : 4, flush);
        return face(t < 340 ? 8 : 0, 0, flush);
    }

    private static Expression shy(int t) {
        return face(t < 140 ? 0 : 8, t < 80 ? 3 : t < 140 ? 2 : 0, true);
    }

    private static Expression face(int face, int mouth, boolean flush) {
        return new Expression(face, mouth, flush);
    }
}
