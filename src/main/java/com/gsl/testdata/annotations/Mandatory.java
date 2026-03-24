package com.gsl.testdata.annotations;

import java.lang.annotation.*;

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
