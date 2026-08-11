package com.gsl.database.finance;

import com.gsl.api.core.ApiSessionBundle;
import com.gsl.api.core.ApiSessionFactory;
import com.gsl.api.integration.LedgerDataSetup;
import com.gsl.api.model.ledger.LedgerCreationOutcome;
import com.gsl.database.BackfillStrategy;
import com.gsl.database.DatabaseManager;
import com.gsl.database.dao.finance.FinGlDao;
import com.gsl.enums.ApiPlatform;
import com.gsl.enums.GlAccountType;
import com.gsl.model.finance.FinGl;
import com.gsl.reports.ReportLogger;
import com.gsl.reports.StepExecutor;
import org.jdbi.v3.core.Jdbi;

import java.util.Collection;
import java.util.List;

/**
 * General Voucher's {@code fingl} shortfall backfill: creates a General Ledger (GL) master
 * (always {@code srctype='G'}) matching a required (type, Sub-Ledger-Applicable,
 * Site-Applicable) combination that {@code DbGeneralVoucherDataSource} couldn't find in the DB.
 * See {@link com.gsl.api.client.LedgerApiClient} for the full HAR-derived flow and the
 * confirmed business rules (Expense/Income must be Site applicable; never extinct; grpcode must
 * be a leaf {@code fingrp} node).
 */
public final class LedgerMasterBackfillStrategy implements BackfillStrategy {

    private final GlAccountType type;
    private final boolean subLedgerApplicable;
    private final boolean siteApplicable;
    private final Collection<Long> assignedSiteCodes;

    /**
     * @param assignedSiteCodes the calling test's logged-in user's assigned sites
     *                          ({@code admsite.code} values, resolved live via
     *                          {@code DbGeneralVoucherDataSource.fetchAssignedSiteCodesLive} —
     *                          an already-resolved plain value; this class never fetches it
     *                          itself, since it runs under a separate, browser-less WEBAPI
     *                          session — see that method's Javadoc on caller discipline). A
     *                          Site-applicable ledger's {@code ledgerSiteInfo} must carry a site
     *                          from this collection. See
     *                          {@link com.gsl.api.client.LedgerApiClient#createLedgerWithDefaults}'s
     *                          Javadoc for why (run 20260810_135033's "No site is assigned to
     *                          this ledger" root-cause). Ignored when {@code siteApplicable} is
     *                          false.
     */
    public LedgerMasterBackfillStrategy(GlAccountType type, boolean subLedgerApplicable, boolean siteApplicable,
            Collection<Long> assignedSiteCodes) {
        this.type = type;
        this.subLedgerApplicable = subLedgerApplicable;
        this.siteApplicable = siteApplicable;
        this.assignedSiteCodes = assignedSiteCodes;
    }

    @Override
    public void backfill(ApiPlatform platform, int neededQuantity) {
        ApiSessionBundle session = ApiSessionFactory.create(platform);
        try {
            for (int i = 0; i < neededQuantity; i++) {
                ReportLogger.dataBackfill(
                        "[LedgerMasterBackfillStrategy] Creating record {}/{} — type={}, slapp={}, costapp={}, assignedSiteCodes={}",
                        i + 1, neededQuantity, type, subLedgerApplicable ? "Y" : "N", siteApplicable ? "Y" : "N", assignedSiteCodes);

                LedgerCreationOutcome outcome = StepExecutor.call("Create Ledger", () ->
                        LedgerDataSetup.createLedgerForTest(session.getLedgerApiClient(), type, subLedgerApplicable,
                                siteApplicable, assignedSiteCodes));

                StepExecutor.call("Verify Ledger Landed in DB", () -> {
                    verifyLanded(outcome.getRequest().getGlname());
                    return null;
                });
            }
        } finally {
            session.logout();
        }
    }

    /**
     * Reads back the just-created row via {@link FinGlDao#findByGlName} (glname is the only
     * identifier available — see {@code LedgerCreationOutcome}'s Javadoc for why) and re-runs the
     * exact query that triggered the backfill ({@link FinGlDao#findByTypeAndApplicability}, which
     * already filters {@code ext='N'}) to confirm the new row is genuinely visible to it — a
     * successful Create response with the row landing extinct for any reason would otherwise pass
     * silently and still leave the original combination unresolved.
     */
    private void verifyLanded(String glname) {
        if (glname == null) return;
        Jdbi jdbi = DatabaseManager.getJdbi();
        FinGlDao finGlDao = jdbi.onDemand(FinGlDao.class);

        FinGl created = finGlDao.findByGlName(glname);
        if (created == null) {
            ReportLogger.dataBackfillWarn(
                    "[LedgerMasterBackfillStrategy] No fingl row found yet for glname='{}' — Ledger/Create "
                            + "may not have actually persisted.", glname);
            return;
        }
        ReportLogger.dataBackfill(
                "[LedgerMasterBackfillStrategy] Confirmed fingl row — glcode={}, glname='{}', type={}, slapp={}, costapp={}",
                created.getGlcode(), created.getGlname(), created.getType(), created.getSlapp(), created.getCostapp());

        String slapp = subLedgerApplicable ? "Y" : "N";
        String costapp = siteApplicable ? "Y" : "N";
        List<FinGl> visibleToTriggeringQuery = finGlDao.findByTypeAndApplicability(type.code(), slapp, costapp);
        boolean visible = visibleToTriggeringQuery.stream().anyMatch(gl -> gl.getGlcode() == created.getGlcode());
        if (!visible) {
            ReportLogger.dataBackfillWarn(
                    "[LedgerMasterBackfillStrategy] glcode={} exists in fingl but is NOT returned by the "
                            + "triggering query (type={}, slapp={}, costapp={}, ext='N') — the retry will still fail.",
                    created.getGlcode(), type.code(), slapp, costapp);
        }
    }

    @Override
    public String k6ScriptKey() {
        // No k6 script exists for this path — quantities here are always 1 (one GL per missing
        // combination), so DataSourceRouter never actually routes this to K6BULK.
        return "ledgerMasterBackfill";
    }
}
