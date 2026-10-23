package com.lulan.shincolle.api;

/**
 * Stable identifiers shared by ShinColle-kai and third-party integrations.
 */
public final class ShinColleApi {

    public static final String MOD_ID = "shincolle_kai";

    /**
     * Version of the stable API in {@code com.lulan.shincolle.api}, for checks at run time. Declare the
     * dependency itself with the mod version range; this number does not replace it.
     *
     * <p>It goes up only when a stable API changes incompatibly: a public type or method is removed, a
     * signature changes, or the meaning of an existing contract changes. Additions do not raise it, and
     * nothing under {@code com.lulan.shincolle.api.experimental} is covered by it. Versions of ShinColle-kai
     * before 1.0 do not fix the API.</p>
     */
    public static final int API_VERSION = 1;

    private ShinColleApi() {
    }
}
