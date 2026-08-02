package com.gsl.api.validator;

import com.gsl.api.model.arvoucher.ArVoucherCreateResponse;

/** Validates an AR Voucher (Finance/api/vouchers/ar) response: success + doc scheme. */
public final class ArVoucherApiValidator {

    private ArVoucherApiValidator() {}

    public static void validate(ArVoucherCreateResponse response) {
        ApiResponseValidator.requireSuccess(response.isSuccess(), response.getError(), "Finance/api/vouchers/ar");
        ApiResponseValidator.requireNonBlank(response.getDocScheme(), "docScheme", "Finance/api/vouchers/ar");
    }
}
