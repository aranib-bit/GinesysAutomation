package com.gsl.api.validator;

import com.gsl.api.core.ApiException;
import com.gsl.api.model.dc.DcCreateResponse;

/** Validates a DC/Save response: success + non-blank DC code, and no silent negative-stock allowance. */
public final class DcApiValidator {

    private DcApiValidator() {}

    public static void validate(DcCreateResponse response) {
        ApiResponseValidator.requireSuccess(response.isSuccess(), response.getError(), "DC/Save");
        ApiResponseValidator.requireNonBlank(response.getDcCode(), "code", "DC/Save");
    }

    /** Fails loudly if a negative-stock dispatch was silently allowed — per plan's negative-stock guard. */
    public static void validateNoSilentNegativeStock(boolean ignoreNegativeStockCheck, boolean negativeStockDetected) {
        if (negativeStockDetected && ignoreNegativeStockCheck) {
            throw new ApiException("DC/Save allowed a negative-stock dispatch — ignoreNegativeStockCheck must stay false for functional tests", 0);
        }
    }
}
