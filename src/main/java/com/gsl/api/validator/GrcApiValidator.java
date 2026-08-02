package com.gsl.api.validator;

import com.gsl.api.model.grc.GrcCreateResponse;

/** Validates a GRC/Save response: success + non-blank scheme docno. */
public final class GrcApiValidator {

    private GrcApiValidator() {}

    public static void validate(GrcCreateResponse response) {
        ApiResponseValidator.requireSuccess(response.isSuccess(), response.getError(), "GRC/Save");
        ApiResponseValidator.requireNonBlank(response.getSchemeDocno(), "schemeDocno", "GRC/Save");
    }
}
