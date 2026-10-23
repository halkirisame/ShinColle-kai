package com.lulan.shincolle.apiboundaryfixture;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/** Stands in for an internal annotation whose value names a class, in the API boundary self-test. */
@Retention(RetentionPolicy.RUNTIME)
public @interface InternalMarker {

    Class<?> value() default Object.class;
}
