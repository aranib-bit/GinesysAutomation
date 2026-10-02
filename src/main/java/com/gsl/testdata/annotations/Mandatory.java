package com.gsl.testdata.annotations;

import java.lang.annotation.*;

/**
 * Marks a test-data DTO field as required — enforced by
 * {@link com.gsl.validation.MandatoryFieldValidator#validate(Object)}, which throws
 * {@link IllegalStateException} with {@link #message()} if the field is null (or blank, for a
 * {@code String}) once the DTO is fully assembled. Call {@code validate()} yourself after
 * building/merging a DTO; the annotation alone does not trigger validation. Note
 * {@link com.gsl.validation.MandatoryFieldValidator#validate(Object)} currently enforces every
 * {@code @Mandatory} field identically regardless of {@link #type()} — see {@link MandatoryType}'s
 * Javadoc for that gap. See {@link AtLeastOneMandatory} for a type-level "one of these fields"
 * alternative.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Mandatory {

    /**
     * Type of mandatory behavior.
     * Default is DATA mandatory.
     */
    MandatoryType type() default MandatoryType.DATA;

    /**
     * Custom error message shown when validation fails.
     */
    String message() default "Mandatory field missing";
}
