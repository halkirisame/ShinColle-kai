package com.lulan.shincolle.ai.domain.movement;

public record MovementPoint(double x, double y, double z) {
    public MovementPoint {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            throw new IllegalArgumentException("Movement point must be finite");
        }
    }

    public double distanceSq(MovementPoint other) {
        double dx = other.x - this.x;
        double dy = other.y - this.y;
        double dz = other.z - this.z;
        return dx * dx + dy * dy + dz * dz;
    }
}
