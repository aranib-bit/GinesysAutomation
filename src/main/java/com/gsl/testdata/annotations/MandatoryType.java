package com.gsl.testdata.annotations;

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
