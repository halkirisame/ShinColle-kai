package com.lulan.shincolle.ai.domain;

public record SourceGate(
        boolean autoEnabled, boolean sitting, boolean craneActive, boolean noFuel, boolean antiAirEnabled) {
    public SourceGate(boolean autoEnabled, boolean sitting, boolean craneActive, boolean noFuel) {
        this(autoEnabled, sitting, craneActive, noFuel, false);
    }
}
