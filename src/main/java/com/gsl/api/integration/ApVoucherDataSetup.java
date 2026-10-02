package com.gsl.api.integration;

import com.gsl.api.client.ApVoucherApiClient;
import com.gsl.api.model.apvoucher.ApVoucherCreateResponse;
import com.gsl.api.model.arvoucher.SubLedgerLookupResult;
import com.gsl.api.validator.ApVoucherApiValidator;
import com.gsl.reports.ReportLogger;

/**
 * Hybrid API+DB setup for AP Voucher: create via {@link ApVoucherApiClient}, validate the
 * response. Also the engine behind {@code ApVoucherArBackfillStrategy} — AR Voucher's
 * document-payment shortfall backfill (Customer Advance, posted {@code onAccount}).
 */
public final class ApVoucherDataSetup {

    private ApVoucherDataSetup() {}

    /**
     * Creates an AP Voucher against {@code subLedger} via {@code client} and validates the response.
     *
     * @param subLedger      the sub-ledger to post against, already resolved via {@link
     *                       ApVoucherApiClient#resolveSubLedger}
     * @param ownerSiteCode  owner site for the created voucher
     * @param ouCode         organizational unit code for the created voucher
     * @param empCode        logged-in employee code used to create the voucher
     * @param tdsApplicable  whether TDS applies — resolved by the caller from the DB
     *                       ({@code finsl.tds_app}), since no API response field carries this flag
     * @throws com.gsl.api.core.ApiException if the response fails {@link ApVoucherApiValidator}'s checks
     */
    public static ApVoucherCreateResponse setupApVoucherForTest(
            ApVoucherApiClient client, SubLedgerLookupResult subLedger, int ownerSiteCode, int ouCode,
            long empCode, int quantity, int rate, boolean tdsApplicable) {
        ApVoucherCreateResponse response = client.createApVoucherWithDefaults(
                subLedger, ownerSiteCode, ouCode, empCode, quantity, rate, tdsApplicable);
        ApVoucherApiValidator.validate(response);
        ReportLogger.business("[ApVoucherDataSetup] AP Voucher created via API — voucherCode={}, docScheme={}, tdsApplicable={}",
                response.getVoucherCode(), response.getDocScheme(), tdsApplicable);
        return response;
    }
}
