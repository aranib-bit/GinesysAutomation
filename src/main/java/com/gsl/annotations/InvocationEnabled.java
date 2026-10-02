package com.gsl.annotations;

import java.lang.annotation.*;

/**
 * Type-level marker with no members, presumably intended to flag a test class as
 * invocation-count/data-driven aware. As of this writing it is imported in
 * {@code CreateItem} but not actually applied to any class, and no framework code reads it via
 * {@code isAnnotationPresent}/{@code getAnnotation} — it has no observable runtime effect yet.
 *
 * <p>TODO-confirm: intended behavior/consumer unclear from current usage; confirm with the author
 * before relying on this annotation or removing it as dead code.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface InvocationEnabled {
}
