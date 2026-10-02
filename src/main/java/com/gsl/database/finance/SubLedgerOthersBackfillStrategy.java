package com.gsl.database.finance;

import com.gsl.api.core.ApiSessionBundle;
import com.gsl.api.core.ApiSessionFactory;
import com.gsl.api.integration.SubLedgerOthersDataSetup;
import com.gsl.api.model.subledgerothers.SubLedgerOthersCreationOutcome;
import com.gsl.database.BackfillStrategy;
import com.gsl.database.DatabaseManager;
import com.gsl.database.dao.finance.FinSlDao;
import com.gsl.database.dao.finance.FinSlGlDao;
import com.gsl.enums.ApiPlatform;
import com.gsl.model.finance.FinSl;
import com.gsl.model.finance.FinSlGl;
import com.gsl.reports.ReportLogger;
import com.gsl.reports.StepExecutor;
import org.jdbi.v3.core.Jdbi;

/**
 * General Voucher's Type3 ({@code finsl_gl}) shortfall backfill: creates a "Others"-classified
 * Sub Ledger permitted against a specific, already-resolved GL that
 * {@code DbGeneralVoucherDataSource} couldn't find a {@code finsl_gl} row for. See
 * {@link com.gsl.api.client.SubLedgerOthersApiClient} for the full HAR-derived flow and the
 * shared UDF handling ({@code FINSL}, via {@code UdfValueBuilder}).
 */
public final class SubLedgerOthersBackfillStrategy implements BackfillStrategy {

    private final long targetGlcode;
    private final String targetGlname;
    private final Long targetSiteCode;

    /**
     * @param targetGlcode   the already-resolved {@code fingl.glcode} the new Sub Ledger must be linked to
     * @param targetGlname   {@code targetGlcode}'s GL name, used to drive the Create UI/API flow
     * @param targetSiteCode the {@code admsite.code} to narrow the new {@code finsl_gl} row's
     *                       {@code finsl_gl_site} to — required (non-null) whenever
     *                       {@code targetGlcode}'s GL is Site-Applicable ({@code fingl.costapp =
     *                       'Y'}); omitting it for such a GL is what produces "Site selection is
     *                       required for the Ledgers - ...". Pass {@code null} for a non-Site-
     *                       Applicable GL.
     *
     *                       <p><b>Must be one of the calling test's logged-in user's assigned
     *                       sites</b> (resolved live via
     *                       {@code DbGeneralVoucherDataSource.fetchAssignedSiteCodesLive}) — the
     *                       caller ({@code DbGeneralVoucherDataSource.loadFreshForScenario}) passes
     *                       the specific assigned site actually found attached to {@code
     *                       targetGlcode}'s GL, not an arbitrary derived value. Attaching a site
     *                       here that isn't one of the user's assigned sites is exactly what
     *                       produced "No site is assigned to this ledger" in run 20260810_135033 — see
     *                       {@code LedgerApiClient#createLedgerWithDefaults}'s Javadoc for the full
     *                       root-cause writeup.
     */
    public SubLedgerOthersBackfillStrategy(long targetGlcode, String targetGlname, Long targetSiteCode) {
        this.targetGlcode = targetGlcode;
        this.targetGlname = targetGlname;
        this.targetSiteCode = targetSiteCode;
    }

    /** Creates {@code neededQuantity} "Others"-classified Sub Ledgers linked to {@link #targetGlcode} via {@code platform}, verifying each landing row's {@code finsl_gl} linkage. */
    @Override
    public void backfill(ApiPlatform platform, int neededQuantity) {
        ApiSessionBundle session = ApiSessionFactory.create(platform);
        try {
            for (int i = 0; i < neededQuantity; i++) {
                ReportLogger.dataBackfill(
                        "[SubLedgerOthersBackfillStrategy] Creating record {}/{} — targetGlcode={}, targetGlname='{}', targetSiteCode={}",
                        i + 1, neededQuantity, targetGlcode, targetGlname, targetSiteCode);

                SubLedgerOthersCreationOutcome outcome = StepExecutor.call("Create Sub Ledger Others", () ->
                        SubLedgerOthersDataSetup.createSubLedgerOthersForTest(
                                session.getSubLedgerOthersApiClient(), targetGlcode, targetGlname, targetSiteCode));

                StepExecutor.call("Verify Sub Ledger Others Landed in DB", () -> {
                    verifyLanded(outcome.getRequest().getSlname(), outcome.getRequest().getSlid());
                    return null;
                });
            }
        } finally {
            session.logout();
        }
    }

    /**
     * Reads back the just-created row via {@link FinSlDao#findBySlnameAndSlid} (slname/slid are
     * the only identifiers available — the Create response carries no generated code) then
     * confirms the {@code finsl_gl} linkage row actually landed via
     * {@link FinSlGlDao#findByGlcodeAndSlcode} — the exact join
     * {@code DbGeneralVoucherDataSource}'s Type3 branch queries.
     */
    private void verifyLanded(String slname, String slid) {
        if (slname == null) return;
        Jdbi jdbi = DatabaseManager.getJdbi();
        FinSlDao finSlDao = jdbi.onDemand(FinSlDao.class);

        FinSl created = finSlDao.findBySlnameAndSlid(slname, slid);
        long deadline = System.currentTimeMillis() + 30_000L;
        while (created == null && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(1_000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            created = finSlDao.findBySlnameAndSlid(slname, slid);
        }
        if (created == null) {
            throw new com.gsl.api.core.DataSetupException("CreateFinanceSubLedgerOther reported success but no finsl row "
                    + "with slname='" + slname + "', slid='" + slid + "' appeared within 30 s. Check that "
                    + "webapi.baseuri (api.properties) points at the same deployment as the UI url "
                    + "(config.properties) and the database the tests read.");
        }
        ReportLogger.dataBackfill(
                "[SubLedgerOthersBackfillStrategy] Confirmed finsl row — slcode={}, slname='{}', slid='{}'",
                created.getSlcode(), created.getSlname(), created.getSlid());

        FinSlGlDao finSlGlDao = jdbi.onDemand(FinSlGlDao.class);
        FinSlGl linkage = finSlGlDao.findByGlcodeAndSlcode(targetGlcode, created.getSlcode());
        if (linkage == null) {
            ReportLogger.dataBackfillWarn(
                    "[SubLedgerOthersBackfillStrategy] finsl row landed (slcode={}) but no finsl_gl row links it "
                            + "to targetGlcode={} — the retry will still fail.", created.getSlcode(), targetGlcode);
        } else {
            ReportLogger.dataBackfill(
                    "[SubLedgerOthersBackfillStrategy] Confirmed finsl_gl linkage — code={}, glcode={}, slcode={}",
                    linkage.getCode(), linkage.getGlcode(), linkage.getSlcode());
        }
    }

    /** {@inheritDoc} Always {@code "subLedgerOthersBackfill"} — see the inline comment below for why {@code K6BULK} never actually reaches this path. */
    @Override
    public String k6ScriptKey() {
        // No k6 script exists for this path — quantities here are always 1 (one Sub Ledger per
        // missing Type3 combination), so DataSourceRouter never actually routes this to K6BULK.
        return "subLedgerOthersBackfill";
    }
}
