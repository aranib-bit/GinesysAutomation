package com.gsl.api.validator;

import com.gsl.api.model.apvoucher.ApVoucherCreateResponse;

/**
 * Validates an AP Voucher ({@code Finance/api/vouchers/ap}) response using the shared
 * {@link ApiResponseValidator} pattern: the envelope's {@code success} flag must be {@code true},
 * and the response must carry a non-blank {@code docScheme} (the created voucher's document
 * scheme).
 */
public final class ApVoucherApiValidator {

    private ApVoucherApiValidator() {}

    /**
     * @throws com.gsl.api.core.ApiException if {@code response} reports {@code success=false} or
     *                                        has a blank {@code docScheme}
     */
    public static void validate(ApVoucherCreateResponse response) {
        ApiResponseValidator.requireSuccess(response.isSuccess(), response.getError(), "Finance/api/vouchers/ap");
        ApiResponseValidator.requireNonBlank(response.getDocScheme(), "docScheme", "Finance/api/vouchers/ap");
    }
}
