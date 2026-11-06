package com.lulan.shincolle.visualdata;

/** Deterministic head-tilt schedule and easing, independent of rendering frequency and entity flags. */
public final class ShipHeadTilt {
    public record Cycle(long number, int age) { }

    private ShipHeadTilt() { }

    public static boolean tilted(int shipUid, long cycle) {
        return cycle > 0 && ShipVisualRandom.sample(shipUid, cycle, 0x54494C54L, 10) > 4;
    }

    /** Five intervals, 71..75 ticks in a seeded permutation, total 365: bounded random access at any tick. */
    public static Cycle cycle(int tickCount, int shipUid) {
        int tick = Math.max(0, tickCount);
        long block = tick / 365;
        int age = tick % 365;
        int offset = ShipVisualRandom.sample(shipUid, block, 0x504552494F44L, 5);
        int index = 0;
        while (index < 4) {
            int length = 71 + (index + offset) % 5;
            if (age < length) break;
            age -= length;
            index++;
        }
        return new Cycle(block * 5 + index, age);
    }

    public static float angle(int tickCount, int shipUid, float partialTick) {
        Cycle cycle = cycle(tickCount, shipUid);
        boolean target = tilted(shipUid, cycle.number());
        boolean previous = tilted(shipUid, cycle.number() - 1);
        return ease(target, previous, cycle.age() + Math.max(0, Math.min(1, partialTick)));
    }

    public static float ease(boolean target, boolean previous, float partTick) {
        float maxAngle = -0.27F;
        if (target) {
            if (previous) return maxAngle;
            float f = (float) Math.sin(partTick * 0.1F * 1.5708F) * maxAngle;
            return f - 0.03F < maxAngle || partTick > 10F ? maxAngle : f;
        }
        if (!previous) return 0;
        float f = (1F - (float) Math.sin(partTick * 0.2F * 1.5708F)) * maxAngle;
        // Once the initial return reaches zero it stays there, even when frames were skipped.
        return f + 0.03F > 0F || partTick >= 4F ? 0 : f;
    }
}
