package com.gsl.testdata.annotations;

import java.lang.annotation.*;

/**
 * Type-level companion to {@link Mandatory}: declares a group of field names on a test-data DTO
 * where at least one (not necessarily all) must be populated. Enforced by
 * {@link com.gsl.validation.MandatoryFieldValidator#validate(Object)}, which reads {@link #fields()}
 * via reflection and throws {@link IllegalStateException} with {@link #message()} if every named
 * field is null/blank. Use this for "either A or B, but not neither" data shapes that a plain
 * per-field {@code @Mandatory} can't express.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface AtLeastOneMandatory {

    /**
     * List of field names where at least one must be non-null / non-empty
     */
    String[] fields();

    /**
     * Error message if validation fails
     */
    String message() default "At least one field must be provided";
}
