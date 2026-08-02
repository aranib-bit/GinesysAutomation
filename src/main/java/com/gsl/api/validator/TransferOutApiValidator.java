package com.gsl.api.validator;

import com.gsl.api.model.transferout.TransferOutCreateResponse;

/** Validates a Transfer Out (SI/Save) response: success + non-blank scheme docno. */
public final class TransferOutApiValidator {

    private TransferOutApiValidator() {}

    public static void validate(TransferOutCreateResponse response) {
        ApiResponseValidator.requireSuccess(response.isSuccess(), response.getError(), "SI/Save");
        ApiResponseValidator.requireNonBlank(response.getSchemeDocno(), "schemeDocno", "SI/Save");
    }
}
