package com.gsl.api.integration;

import com.gsl.api.client.ArVoucherApiClient;
import com.gsl.api.model.arvoucher.ArVoucherCreateResponse;
import com.gsl.api.validator.ArVoucherApiValidator;
import com.gsl.enums.VoucherDomain;
import com.gsl.reports.ReportLogger;

/**
 * Hybrid API+DB setup for AR Voucher: create via {@link ArVoucherApiClient}, validate the
 * response. Also the engine behind {@code ArVoucherApBackfillStrategy} — AP Voucher's
 * document-payment shortfall backfill for any clsCode whose clstype isn't Supplier.
 */
public final class ArVoucherDataSetup {

    private ArVoucherDataSetup() {}

    /**
     * Creates an AR Voucher against {@code requiredClsCode} via {@code client} and validates the response.
     *
     * @param requiredClsCode the {@code admcls.clscode} the voucher's sub-ledger must belong to
     * @param ownerSiteCode   owner site for the created voucher
     * @param ouCode          organizational unit code for the created voucher
     * @param empCode         logged-in employee code used to create the voucher
     * @throws com.gsl.api.core.ApiException if the response fails {@link ArVoucherApiValidator}'s checks
     */
    public static ArVoucherCreateResponse setupArVoucherForTest(
            ArVoucherApiClient client, long requiredClsCode, int ownerSiteCode, int ouCode,
            long empCode, int quantity, int rate) {
        // Scheme chosen from the DB so an exhausted one (e.g. a full "AR/AP Voucher") is never used.
        String docSchemeName = VoucherDocSchemeSupport.resolveSchemeName(
                VoucherDomain.AR.getMenuShortcode(), ownerSiteCode, empCode);
        ArVoucherCreateResponse response = new com.gsl.service.ArVoucherService(client).createArVoucherWithDefaults(
                requiredClsCode, ownerSiteCode, ouCode, empCode, quantity, rate, docSchemeName);
        ArVoucherApiValidator.validate(response);
        ReportLogger.business("[ArVoucherDataSetup] AR Voucher created via API — voucherCode={}, docScheme={}",
                response.getVoucherCode(), response.getDocScheme());
        return response;
    }
}
