package com.gsl.testdata.annotations;

import java.lang.annotation.*;

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
