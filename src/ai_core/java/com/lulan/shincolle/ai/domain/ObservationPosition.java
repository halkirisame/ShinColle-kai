package com.lulan.shincolle.ai.domain;

public record ObservationPosition(double x, double y, double z) {
    public ObservationPosition {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            throw new IllegalArgumentException("Observed position must be finite");
        }
    }
}
