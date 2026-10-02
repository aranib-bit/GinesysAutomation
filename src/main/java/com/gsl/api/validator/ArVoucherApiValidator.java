package com.gsl.api.validator;

import com.gsl.api.model.arvoucher.ArVoucherCreateResponse;

/**
 * Validates an AR Voucher ({@code Finance/api/vouchers/ar}) response using the shared
 * {@link ApiResponseValidator} pattern: the envelope's {@code success} flag must be {@code true},
 * and the response must carry a non-blank {@code docScheme} (the created voucher's document
 * scheme).
 */
public final class ArVoucherApiValidator {

    private ArVoucherApiValidator() {}

    /**
     * @throws com.gsl.api.core.ApiException if {@code response} reports {@code success=false} or
     *                                        has a blank {@code docScheme}
     */
    public static void validate(ArVoucherCreateResponse response) {
        ApiResponseValidator.requireSuccess(response.isSuccess(), response.getError(), "Finance/api/vouchers/ar");
        ApiResponseValidator.requireNonBlank(response.getDocScheme(), "docScheme", "Finance/api/vouchers/ar");
    }
}
