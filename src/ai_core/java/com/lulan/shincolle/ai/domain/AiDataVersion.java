package com.lulan.shincolle.ai.domain;

/**
 * The version mark of a ship's saved AI data, and what a stored mark means to this build.
 *
 * <p>Version 0 is a save without the mark. Version 1 has the same contents as version 0 plus the
 * mark itself, so both are read the same way.
 *
 * <p>Rules for raising {@link #CURRENT}:
 * <ul>
 *   <li>A change to the meaning, the order or the keys of the saved AI data raises the version in
 *       the same change, together with a migration function and a test that loads a save of the
 *       older version.</li>
 *   <li>Migrations run in order from version 0 up to the current one, so every older save stays
 *       loadable. The tests that load a version 0 save are not removed.</li>
 *   <li>A migration is chosen by the stored version only, never by guessing from which keys are
 *       present or how long an array is.</li>
 *   <li>A save always carries the version of the build that wrote it; the version that was read
 *       is not written back.</li>
 * </ul>
 */
public final class AiDataVersion {

    /** The key the mark is stored under, beside the ship's state arrays. */
    public static final String KEY = "AiDataVersion";

    /** The version this build writes. */
    public static final int CURRENT = 1;

    private AiDataVersion() {
    }

    /** What a stored mark means to this build. */
    public sealed interface Read {
    }

    /** No mark: a save from before the mark existed. */
    public record Unmarked() implements Read {
    }

    /** A version this build knows how to read. */
    public record Known() implements Read {
    }

    /** A version written by a newer build. Its known keys are still read. */
    public record Future(int stored) implements Read {
    }

    /** A mark that cannot be a version. It is read as a save without a mark. */
    public record Invalid(String stored) implements Read {
    }

    /**
     * Classifies the mark found in a save.
     *
     * @param present whether the key exists at all
     * @param integer whether the key holds an integer
     * @param value   the integer, when there is one
     */
    public static Read read(boolean present, boolean integer, int value) {
        if (!present) {
            return new Unmarked();
        }
        if (!integer) {
            return new Invalid("not an integer");
        }
        if (value <= 0) {
            return new Invalid(Integer.toString(value));
        }
        if (value > CURRENT) {
            return new Future(value);
        }
        return new Known();
    }
}
