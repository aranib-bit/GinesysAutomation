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

    /**
     * Creates an "Others"-classified Sub Ledger linked to {@code targetGlcode} via {@code client},
     * throwing if the response reports failure.
     *
     * @param targetGlcode   the already-resolved {@code fingl.glcode} the new Sub Ledger is linked to
     * @param targetGlname   {@code targetGlcode}'s GL name, used to drive the create flow
     * @param targetSiteCode site to narrow the new {@code finsl_gl} row to; {@code null} for a non-Site-Applicable GL
     * @throws com.gsl.api.core.ApiException if the response's {@code success}/{@code result} flags indicate failure
     */
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
