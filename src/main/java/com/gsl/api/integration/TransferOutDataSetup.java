package com.gsl.api.integration;

import com.gsl.api.client.TransferOutApiClient;
import com.gsl.api.model.transferout.TransferOutCreateResponse;
import com.gsl.api.validator.TransferOutApiValidator;
import com.gsl.reports.ReportLogger;

/**
 * Hybrid API+DB setup for Transfer Out (a stock transfer raised against an existing DC): create
 * via {@link TransferOutApiClient}, validate the response. DB-side row confirmation deferred
 * pending live-schema access — see {@code ItemDataSetup}.
 *
 * <p>Would-be fourth link of the Item &rarr; GRC &rarr; DC &rarr; Transfer Out chain: {@code
 * dcCode} is the code returned by {@link DcDataSetup#setupDcForTest}. Unlike the other three
 * links, no caller of this method exists anywhere in this codebase yet — it is not reachable from
 * any backfill strategy or test at the time of writing.
 */
public final class TransferOutDataSetup {

    private TransferOutDataSetup() {}

    /**
     * Creates a single-line Transfer Out for {@code itemCode} against {@code dcCode} via
     * {@code client} and validates the response.
     *
     * @param dcCode   the DC code returned by an earlier {@link DcDataSetup#setupDcForTest} call
     * @param itemCode the item code the DC itself was created for
     * @throws com.gsl.api.core.ApiException if the response fails {@link TransferOutApiValidator}'s checks
     */
    public static TransferOutCreateResponse setupTransferOutForTest(TransferOutApiClient client,
                                                                       String dcCode, String itemCode,
                                                                       int quantity, int rate) {
        TransferOutCreateResponse response = new com.gsl.service.TransferOutService(client).createTransferOut(dcCode, itemCode, quantity, rate);
        TransferOutApiValidator.validate(response);
        ReportLogger.business("[TransferOutDataSetup] Transfer Out created via API — siCode={}, schemeDocno={}",
                response.getSiCode(), response.getSchemeDocno());
        return response;
    }
}
