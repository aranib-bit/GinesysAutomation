package com.gsl.api.integration;

import com.gsl.api.client.SubLedgerOthersApiClient;
import com.gsl.api.core.ApiException;
import com.gsl.api.model.subledgerothers.SubLedgerOthersCreationOutcome;
import com.gsl.reports.ReportLogger;

/**
 * Hybrid API+DB setup for Sub Ledger Others creation: create via {@link SubLedgerOthersApiClient},
 * validate the response. The engine behind {@code SubLedgerOthersBackfillStrategy} — General
 * Voucher's Type3 ({@code finsl_gl}) shortfall backfill. Mirrors {@code LedgerDataSetup}'s shape.
 */
public final class SubLedgerOthersDataSetup {

    private SubLedgerOthersDataSetup() {}

    public static SubLedgerOthersCreationOutcome createSubLedgerOthersForTest(
            SubLedgerOthersApiClient client, long targetGlcode, String targetGlname, Long targetSiteCode) {
        SubLedgerOthersCreationOutcome outcome =
                client.createSubLedgerOthersWithDefaults(targetGlcode, targetGlname, targetSiteCode);
        if (!outcome.getResponse().isSuccess() || !outcome.getResponse().isResult()) {
            throw new ApiException("CreateFinanceSubLedgerOther returned success=" + outcome.getResponse().isSuccess()
                    + ", result=" + outcome.getResponse().isResult() + ", error=" + outcome.getResponse().getError(), 0);
        }
        ReportLogger.business("[SubLedgerOthersDataSetup] Sub Ledger Others created via API — slname='{}', slid='{}', targetGlcode={}",
                outcome.getRequest().getSlname(), outcome.getRequest().getSlid(), targetGlcode);
        return outcome;
    }
}
