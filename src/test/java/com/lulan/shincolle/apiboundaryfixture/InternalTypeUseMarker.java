package com.lulan.shincolle.apiboundaryfixture;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Stands in for an internal type annotation, in the API boundary self-test. */
@Target(ElementType.TYPE_USE)
@Retention(RetentionPolicy.RUNTIME)
public @interface InternalTypeUseMarker {
}
