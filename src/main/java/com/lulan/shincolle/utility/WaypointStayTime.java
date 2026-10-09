package com.lulan.shincolle.utility;

/** Converts the persisted 0..16 waypoint stay setting into server ticks. */
public final class WaypointStayTime {

    private WaypointStayTime() {
    }

    public static int toTicks(int rawStay) {
        return switch (rawStay) {
            case 1, 2, 3, 4, 5 -> rawStay * 100;
            case 6, 7, 8, 9, 10 -> (rawStay - 5) * 1200;
            case 11, 12, 13, 14, 15, 16 -> (rawStay - 10) * 12000;
            default -> 0;
        };
    }

    /**
     * What the stay setting is shown as: whole seconds below a minute, whole minutes from a minute on.
     * The unit is a translation key, so the text is the language file's, not the code's.
     */
    public record Display(int amount, boolean minutes) {
        public String translationKey() {
            return minutes ? "gui.shincolle_kai.time.minutes" : "gui.shincolle_kai.time.seconds";
        }
    }

    public static Display display(int rawStay) {
        int seconds = toTicks(rawStay) / 20;
        return seconds >= 60 ? new Display(seconds / 60, true) : new Display(seconds, false);
    }
}
