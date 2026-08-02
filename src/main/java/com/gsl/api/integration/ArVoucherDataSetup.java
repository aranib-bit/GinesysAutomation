package com.gsl.api.integration;

import com.gsl.api.client.ArVoucherApiClient;
import com.gsl.api.model.arvoucher.ArVoucherCreateResponse;
import com.gsl.api.validator.ArVoucherApiValidator;
import com.gsl.reports.ReportLogger;

/**
 * Hybrid API+DB setup for AR Voucher: create via {@link ArVoucherApiClient}, validate the
 * response. Also the engine behind {@code ArVoucherApBackfillStrategy} — AP Voucher's
 * document-payment shortfall backfill for any clsCode whose clstype isn't Supplier.
 */
public final class ArVoucherDataSetup {

    private ArVoucherDataSetup() {}

    public static ArVoucherCreateResponse setupArVoucherForTest(
            ArVoucherApiClient client, long requiredClsCode, int ownerSiteCode, int ouCode,
            long empCode, int quantity, int rate) {
        ArVoucherCreateResponse response =
                client.createArVoucherWithDefaults(requiredClsCode, ownerSiteCode, ouCode, empCode, quantity, rate);
        ArVoucherApiValidator.validate(response);
        ReportLogger.business("[ArVoucherDataSetup] AR Voucher created via API — voucherCode={}, docScheme={}",
                response.getVoucherCode(), response.getDocScheme());
        return response;
    }
}
