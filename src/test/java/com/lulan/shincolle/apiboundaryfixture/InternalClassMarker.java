package com.lulan.shincolle.apiboundaryfixture;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/** Stands in for an internal annotation kept only in the class file, in the API boundary self-test. */
@Retention(RetentionPolicy.CLASS)
public @interface InternalClassMarker {
}
