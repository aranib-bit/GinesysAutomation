package com.gsl.api.integration;

import com.gsl.api.client.TransferOutApiClient;
import com.gsl.api.model.transferout.TransferOutCreateResponse;
import com.gsl.api.validator.TransferOutApiValidator;
import com.gsl.reports.ReportLogger;

/**
 * Hybrid API+DB setup for Transfer Out: create via {@link TransferOutApiClient}, validate the
 * response. DB-side row confirmation deferred pending live-schema access — see {@code ItemDataSetup}.
 */
public final class TransferOutDataSetup {

    private TransferOutDataSetup() {}

    public static TransferOutCreateResponse setupTransferOutForTest(TransferOutApiClient client,
                                                                       String dcCode, String itemCode,
                                                                       int quantity, int rate) {
        TransferOutCreateResponse response = client.createTransferOut(dcCode, itemCode, quantity, rate);
        TransferOutApiValidator.validate(response);
        ReportLogger.business("[TransferOutDataSetup] Transfer Out created via API — siCode={}, schemeDocno={}",
                response.getSiCode(), response.getSchemeDocno());
        return response;
    }
}
