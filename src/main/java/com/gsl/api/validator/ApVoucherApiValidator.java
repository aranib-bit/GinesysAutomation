package com.gsl.api.validator;

import com.gsl.api.model.apvoucher.ApVoucherCreateResponse;

/** Validates an AP Voucher (Finance/api/vouchers/ap) response: success + doc scheme. */
public final class ApVoucherApiValidator {

    private ApVoucherApiValidator() {}

    public static void validate(ApVoucherCreateResponse response) {
        ApiResponseValidator.requireSuccess(response.isSuccess(), response.getError(), "Finance/api/vouchers/ap");
        ApiResponseValidator.requireNonBlank(response.getDocScheme(), "docScheme", "Finance/api/vouchers/ap");
    }
}
