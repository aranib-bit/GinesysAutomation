package com.gsl.testdata.annotations;

/**
 * Categorizes why a {@link Mandatory} field is required, for readability/documentation of test
 * DTOs (e.g. {@code ItemGeneral}/{@code ItemPOSBehaviour} tag some fields {@code UI_DEFAULT}).
 * TODO-confirm: {@link com.gsl.validation.MandatoryFieldValidator#validate(Object)} does not
 * currently branch on {@link Mandatory#type()} at all — every {@code @Mandatory} field is
 * enforced as non-null/non-blank the same way regardless of which constant is used, so
 * {@code UI_DEFAULT}'s "test data may be null" and {@code CONDITIONAL}'s "mandatory only when a
 * condition is satisfied" are not yet actually honored by the validator. Do not assume this is
 * dead/aspirational without checking for a newer validator overload first.
 */
public enum MandatoryType {

    /**
     * Field must be provided explicitly in test data
     * (Excel / DB / API). Validation always applies.
     */
    DATA,

    /**
     * Field is mandatory in UI but UI provides
     * a valid default value.
     * Test data may be null.
     */
    UI_DEFAULT,

    /**
     * Field is mandatory only when a condition
     * (defined separately) is satisfied.
     */
    CONDITIONAL
}
