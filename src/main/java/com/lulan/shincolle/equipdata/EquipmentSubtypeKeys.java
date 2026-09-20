package com.lulan.shincolle.equipdata;

/** Produces JEI subtype keys without placing optional JEI types on the test classpath. */
public final class EquipmentSubtypeKeys {

    private EquipmentSubtypeKeys() {
    }

    /**
     * Returns the variant key for an ingredient, or JEI's no-subtype key for a recipe.
     *
     * <p>The empty string is hard-coded because it is the verified value of
     * {@code IIngredientSubtypeInterpreter.NONE}. Keeping the JEI type out of this pure
     * function permits unit tests to run without the compile-only JEI API. A future JEI change
     * to that value would make recipe subtype handling diverge from JEI's no-subtype contract.</p>
     */
    public static String subtypeKey(int variant, boolean distinguishVariants) {
        return distinguishVariants ? Integer.toString(variant) : "";
    }

    /** Returns a different variant number for checking whether JEI separates subtype keys. */
    public static int probeVariant(int variant) {
        return variant == 0 ? 1 : 0;
    }
}
