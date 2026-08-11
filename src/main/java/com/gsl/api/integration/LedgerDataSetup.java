package com.gsl.api.integration;

import com.gsl.api.client.LedgerApiClient;
import com.gsl.api.core.ApiException;
import com.gsl.api.model.ledger.LedgerCreationOutcome;
import com.gsl.enums.GlAccountType;
import com.gsl.reports.ReportLogger;

import java.util.Collection;

/**
 * Hybrid API+DB setup for Ledger (GL) master creation: create via {@link LedgerApiClient},
 * validate the response. The engine behind {@code LedgerMasterBackfillStrategy} — General
 * Voucher's {@code fingl} shortfall backfill. Mirrors {@code ArVoucherDataSetup}'s shape; no
 * separate validator class exists yet for this shape (unlike AR Voucher's
 * {@code ArVoucherApiValidator}) since the response carries no generated code to validate beyond
 * its own {@code success}/{@code result} flags.
 */
public final class LedgerDataSetup {

    private LedgerDataSetup() {}

    public static LedgerCreationOutcome createLedgerForTest(
            LedgerApiClient client, GlAccountType type, boolean subLedgerApplicable, boolean siteApplicable,
            Collection<Long> assignedSiteCodes) {
        LedgerCreationOutcome outcome = client.createLedgerWithDefaults(
                type, subLedgerApplicable, siteApplicable, assignedSiteCodes);
        if (!outcome.getResponse().isSuccess() || !outcome.getResponse().isResult()) {
            throw new ApiException("Ledger/Create returned success=" + outcome.getResponse().isSuccess()
                    + ", result=" + outcome.getResponse().isResult() + ", error=" + outcome.getResponse().getError(), 0);
        }
        ReportLogger.business("[LedgerDataSetup] Ledger created via API — type={}, slapp={}, costapp={}, glname='{}'",
                type, subLedgerApplicable ? "Y" : "N", siteApplicable ? "Y" : "N", outcome.getRequest().getGlname());
        return outcome;
    }
}
