package com.gsl.database.finance;

import com.gsl.database.BackfillStrategy;
import com.gsl.database.DatabaseManager;
import com.gsl.database.dao.finance.FinVchMainDao;
import com.gsl.driver.Driver;
import com.gsl.driver.SessionManager;
import com.gsl.enums.ApiPlatform;
import com.gsl.model.finance.FinVchMain;
import com.gsl.pages.Finance.Operations.GeneralVoucherListPage;
import com.gsl.pages.HomePage;
import com.gsl.pages.enums.Finance.MainMenuType;
import com.gsl.pages.enums.Finance.Operations.OperationsMenuType;
import com.gsl.reports.ReportLogger;
import com.gsl.reports.StepExecutor;
import com.gsl.testdata.UserCredential;
import com.gsl.testdata.UserPool;
import com.gsl.testdata.UserType;
import com.gsl.utils.NavigationUtils;
import org.jdbi.v3.core.Jdbi;

/**
 * {@code DbGeneralVoucherEditDataSource}'s shortfall backfill for the "Posted source, needs
 * revert" Edit variants (plan §4 point 2 / §7's Mandatory scenario 2 and REGRESSION_PHASE1
 * scenario 7): when no unclaimed {@code release_status='U'} General Voucher is available, this
 * claims one {@code release_status='P'} candidate (not already bank-reconciled) and reverts it
 * {@code P → U} through the UI as a data-preparation prerequisite.
 *
 * <p><b>Plan §16</b> — previously called a permanently-stubbed REST endpoint
 * ({@code GeneralVoucherApiClient#revertGeneralVoucher}) which unconditionally threw
 * {@code UnsupportedOperationException}, crashing this strategy's own {@code @Test} method's
 * ENTIRE {@code @DataProvider} (uncaught out of {@code DataAvailabilityGuard.selectOrBackfill}
 * → {@code claimCandidate} → {@code load()}) — confirmed live (report run {@code 20260821_144859})
 * to be the actual root cause of "GV Edit's second parallel thread never got any work" (that
 * method's provider crashed before producing a single row) AND of "Revert appears to use an API
 * and fail" (this class's own name/step label, unrelated to the real, already-UI-driven
 * {@code RevertGeneralVoucher} test). Per the user's explicit direction, this now drives the
 * SAME already-working UI revert flow {@code EditGeneralVoucher}'s own "Unpost if Posted" step
 * and the {@code RevertGeneralVoucher} test class already use — reusing existing, already-proven
 * session-lifecycle methods ({@code SessionManager.ensureLoggedIn}/{@code closeSessionAndReleaseUser})
 * and page-object calls ({@code GeneralVoucherListPage.revertSelectedDocument()}) — no new
 * page-object or session-lifecycle code needed.
 *
 * <p><b>Known, deliberate cost trade-off</b> (flagged, not hidden): this makes each backfill call
 * a real browser session (login + navigate + paginated grid search + revert) instead of a
 * sub-100ms REST call — this repo's own report data shows the paginated-search step alone taking
 * 7-16 minutes in one real run. A failed/timed-out UI attempt here is caught and logged, not
 * rethrown — {@link com.gsl.database.DataAvailabilityGuard#selectOrBackfill}'s own re-check after
 * {@code backfill()} returns is what turns a still-genuine shortfall into a graceful
 * {@code SkipException}, so this can never again crash the whole data provider the way the stub
 * API call did.
 */
public final class GeneralVoucherEditRevertBackfillStrategy implements BackfillStrategy {

    private static final String CLAIM_POSTED_SQL =
            "SELECT m.vchcode FROM finvchmain m " +
            "WHERE m.vchtype = 'G' AND m.release_status = 'P' " +
            "AND NOT EXISTS (SELECT 1 FROM finvchdet d2 WHERE d2.vchcode = m.vchcode AND d2.brsdt IS NOT NULL) " +
            "ORDER BY m.vchcode";

    @Override
    public void backfill(ApiPlatform platform, int neededQuantity) {
        for (int i = 0; i < neededQuantity; i++) {
            Long vchcode = claimOnePostedCandidate();
            if (vchcode == null) {
                ReportLogger.dataBackfillWarn(
                        "[GeneralVoucherEditRevertBackfillStrategy] No unclaimed Posted (release_status='P') "
                        + "General Voucher found to revert — record {}/{} could not be prepared.",
                        i + 1, neededQuantity);
                continue;
            }
            long claimedVchcode = vchcode;
            ReportLogger.dataBackfill(
                    "[GeneralVoucherEditRevertBackfillStrategy] Reverting record {}/{} via UI — vchcode={}",
                    i + 1, neededQuantity, claimedVchcode);

            try {
                StepExecutor.call("Revert General Voucher via UI", () -> {
                    revertViaUi(claimedVchcode);
                    return null;
                });
            } catch (RuntimeException e) {
                // A UI revert that fails/times out must not crash the whole data provider (plan
                // §16) — log and move on; DataAvailabilityGuard's own re-check after backfill()
                // returns will surface a clear SkipException if this leaves a genuine shortfall.
                ReportLogger.dataBackfillWarn(
                        "[GeneralVoucherEditRevertBackfillStrategy] UI-driven revert failed for vchcode={} — {}",
                        claimedVchcode, e.getMessage());
                continue;
            }

            StepExecutor.call("Verify General Voucher Landed Unposted in DB", () -> {
                verifyLandedUnposted(claimedVchcode);
                return null;
            });
        }
    }

    /**
     * Drives the exact same UI revert sequence {@code EditGeneralVoucher}'s "Unpost if Posted"
     * step and {@code RevertGeneralVoucher} already use, on a throwaway browser session created
     * and torn down entirely within this call — safe to run at {@code @DataSupplier} time since
     * {@code load()} runs single-threaded, before this thread's own {@code @Test}/
     * {@code SessionPool.borrow()} ever touches {@code DriverManager}'s thread-local driver slot.
     */
    private void revertViaUi(long vchcode) {
        String schemeDocno = DatabaseManager.getJdbi().onDemand(FinVchMainDao.class).findByVoucherCode(vchcode)
                .orElseThrow(() -> new IllegalStateException("Claimed vchcode=" + vchcode
                        + " vanished before its scheme_docno could be read for the UI revert."))
                .getSchemeDocno();

        UserCredential cred = UserPool.acquireSet1User(UserType.UI,
                "GeneralVoucherEditRevertBackfillStrategy.revertViaUi");
        try {
            Driver.initDriver();
            HomePage home = SessionManager.ensureLoggedIn(cred);
            GeneralVoucherListPage listPage = NavigationUtils.navigateToFinanceSideMenu(
                    home, MainMenuType.OPERATIONS, OperationsMenuType.CATEGORY, GeneralVoucherListPage.class);
            listPage.clearFilterAndRefreshList().resetToFirstPageIfEnabled();
            listPage.selectDocumentInListPage(schemeDocno);
            listPage.revertSelectedDocument();
        } finally {
            SessionManager.closeSessionAndReleaseUser(
                    "GeneralVoucherEditRevertBackfillStrategy UI revert backfill");
        }
    }

    private Long claimOnePostedCandidate() {
        Jdbi jdbi = DatabaseManager.getJdbi();
        return jdbi.withHandle(handle ->
                handle.createQuery(CLAIM_POSTED_SQL)
                        .mapTo(Long.class)
                        .findFirst()
                        .orElse(null));
    }

    /** Reads back the just-reverted row and confirms it actually landed {@code release_status='U'}. */
    private void verifyLandedUnposted(long vchcode) {
        Jdbi jdbi = DatabaseManager.getJdbi();
        FinVchMain reverted = jdbi.onDemand(FinVchMainDao.class).findByVoucherCode(vchcode).orElse(null);
        if (reverted == null) {
            ReportLogger.dataBackfillWarn(
                    "[GeneralVoucherEditRevertBackfillStrategy] No finvchmain row found for vchcode={} after the "
                            + "revert call — it may not have actually persisted.", vchcode);
            return;
        }
        if (!"U".equals(reverted.getReleaseStatus())) {
            ReportLogger.dataBackfillWarn(
                    "[GeneralVoucherEditRevertBackfillStrategy] vchcode={} landed with release_status='{}', not "
                            + "'U' — the retrying claim query (release_status='U') will not find it.",
                    vchcode, reverted.getReleaseStatus());
            return;
        }
        ReportLogger.dataBackfill(
                "[GeneralVoucherEditRevertBackfillStrategy] Confirmed Unposted finvchmain row — vchcode={}, "
                        + "scheme_docno='{}'", vchcode, reverted.getSchemeDocno());
    }

    @Override
    public String k6ScriptKey() {
        // No k6 script exists for this path — quantities here are always 1, so DataSourceRouter
        // never actually routes this to K6BULK.
        return "generalVoucherEditRevertBackfill";
    }
}
