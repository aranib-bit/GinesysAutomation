package com.gsl.api.validator;

import com.gsl.api.model.grc.GrcCreateResponse;

/**
 * Validates a {@code GRC/Save} response using the shared {@link ApiResponseValidator} pattern:
 * the envelope's {@code success} flag must be {@code true}, and the response must carry a
 * non-blank {@code schemeDocno} (the created GRC's document number).
 */
public final class GrcApiValidator {

    private GrcApiValidator() {}

    /**
     * @throws com.gsl.api.core.ApiException if {@code response} reports {@code success=false} or
     *                                        has a blank {@code schemeDocno}
     */
    public static void validate(GrcCreateResponse response) {
        ApiResponseValidator.requireSuccess(response.isSuccess(), response.getError(), "GRC/Save");
        ApiResponseValidator.requireNonBlank(response.getSchemeDocno(), "schemeDocno", "GRC/Save");
    }
}
