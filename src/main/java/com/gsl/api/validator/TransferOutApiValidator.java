package com.gsl.api.validator;

import com.gsl.api.model.transferout.TransferOutCreateResponse;

/**
 * Validates a Transfer Out ({@code SI/Save}) response using the shared
 * {@link ApiResponseValidator} pattern: the envelope's {@code success} flag must be {@code true},
 * and the response must carry a non-blank {@code schemeDocno} (the created transfer's document
 * number).
 */
public final class TransferOutApiValidator {

    private TransferOutApiValidator() {}

    /**
     * @throws com.gsl.api.core.ApiException if {@code response} reports {@code success=false} or
     *                                        has a blank {@code schemeDocno}
     */
    public static void validate(TransferOutCreateResponse response) {
        ApiResponseValidator.requireSuccess(response.isSuccess(), response.getError(), "SI/Save");
        ApiResponseValidator.requireNonBlank(response.getSchemeDocno(), "schemeDocno", "SI/Save");
    }
}
