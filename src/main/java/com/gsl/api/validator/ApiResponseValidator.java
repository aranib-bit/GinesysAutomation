package com.gsl.api.validator;

import com.gsl.api.core.ApiException;

/** Base validation every flow validator builds on: success flag must be true, no error. */
public final class ApiResponseValidator {

    private ApiResponseValidator() {}

    public static void requireSuccess(boolean success, String error, String context) {
        if (!success) {
            throw new ApiException(context + " returned success=false: " + error, 0);
        }
    }

    public static void requireNonBlank(String value, String fieldName, String context) {
        if (value == null || value.isBlank()) {
            throw new ApiException(context + " — required field '" + fieldName + "' is blank", 0);
        }
    }
}
