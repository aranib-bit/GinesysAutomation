package com.gsl.api.integration;

import com.gsl.api.client.ApVoucherApiClient;
import com.gsl.api.model.apvoucher.ApVoucherCreateResponse;
import com.gsl.api.model.arvoucher.SubLedgerLookupResult;
import com.gsl.api.validator.ApVoucherApiValidator;
import com.gsl.reports.ReportLogger;

public final class ApVoucherDataSetup {

    private ApVoucherDataSetup() {}

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
