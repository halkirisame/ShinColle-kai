package com.lulan.shincolle.visualdata;

import java.util.Optional;
import java.util.Set;

/** Reads presentation state only; absent clips fall through to the next active posture. */
public final class ShipVisualPosture {
    public record Input(boolean noFuel, boolean caressed, boolean riding, boolean sitting,
                        boolean sprinting, float movementAmount, boolean moving) { }

    private static final ShipVisualEvent[] PRIORITY = {
        ShipVisualEvent.NO_FUEL, ShipVisualEvent.CARESS, ShipVisualEvent.RIDE, ShipVisualEvent.SIT,
        ShipVisualEvent.SPRINT, ShipVisualEvent.MOVE, ShipVisualEvent.IDLE
    };

    private ShipVisualPosture() { }

    public static Optional<ShipVisualEvent> select(Input input, Set<ShipVisualEvent> available) {
        for (ShipVisualEvent event : PRIORITY) {
            boolean active = switch (event) {
                case NO_FUEL -> input.noFuel();
                case CARESS -> input.caressed();
                case RIDE -> input.riding();
                case SIT -> input.sitting();
                case SPRINT -> input.sprinting() || input.movementAmount() > 0.95F;
                case MOVE -> input.moving();
                case IDLE -> true;
                default -> false;
            };
            if (active && available.contains(event)) return Optional.of(event);
        }
        return Optional.empty();
    }
}
