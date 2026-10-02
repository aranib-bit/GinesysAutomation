package com.gsl.database.finance;

import com.gsl.database.BackfillStrategy;
import com.gsl.database.DatabaseManager;
import com.gsl.database.DocumentReservationRegistry;
import com.gsl.database.dao.finance.FinVchMainDao;
import com.gsl.driver.Driver;
import com.gsl.driver.SessionManager;
import com.gsl.enums.ApiPlatform;
import com.gsl.enums.PeriodDomain;
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
import com.gsl.utils.dataprovider.fixture.finance.DocumentSearchStrategy;
import com.gsl.utils.dataprovider.fixture.finance.GeneralVoucherEditVariant;
import com.gsl.utils.dataprovider.source.finance.DbGeneralVoucherEditDataSource;
import com.gsl.utils.dataprovider.source.finance.common.AccountingPeriodCache;
import org.jdbi.v3.core.Jdbi;

import java.util.List;

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
 *
 * <p><b>Shape-aware claim (fixed after a confirmed live chronic failure)</b> — this strategy is
 * shared across every {@code SourceStatus#UNPOSTED} Edit variant's shortfall path, most of which
 * (header-only edits) genuinely don't care what shape the reverted voucher has. But {@code
 * DETAIL_LINE} variants with {@link GeneralVoucherEditVariant#isRequireSharedGlcodeSibling()} set
 * need a voucher with two {@code finvchdet} rows sharing a {@code glcode} with different {@code
 * slcode} — a claim query that ignores this reverts an arbitrary Posted voucher with no chance of
 * matching, so {@code DbGeneralVoucherEditDataSource#claimOne}'s own shape-filtered retry
 * (release_status='U') keeps finding nothing regardless of whether this backfill's UI step
 * succeeds. Confirmed live (reports/runs/20260916_190711, `FIN-GV-EDIT-DETAIL`): the UI revert
 * completed cleanly, yet the scenario still skipped 5/5 runs on 2026-09-16 — and a direct DB check
 * (2026-09-17) confirmed 9 Posted vouchers already exist with this exact shape
 * (47173/597288/597292/34062/26867/562638/27152/24031/26842), so the fix is picking one of THOSE
 * via the optional {@link #variant}, not generating new data. Reuses the identical {@code EXISTS}
 * clause {@code DbGeneralVoucherEditDataSource#claimOne} already applies for the same flag, so the
 * two queries can never disagree about what "the required shape" means.
 */
public final class GeneralVoucherEditRevertBackfillStrategy implements BackfillStrategy {

    /**
     * Base claim query: every Posted (release_status='P') General Voucher not already
     * bank-reconciled ({@code finvchdet.brsdt IS NULL} on all its lines) — candidates are scanned
     * in {@link #claimOnePostedCandidate}, oldest {@code vchcode} first.
     *
     * <p>Finding A (Round 12): {@code SELECT m.*} (not just {@code m.vchcode}) — {@code
     * claimOnePostedCandidate} below used to re-fetch each candidate's full row individually
     * inside its scan loop; bulk-fetching the whole row up front removes that per-candidate round
     * trip (the dominant cost when this scan has to search deep before finding one of a small
     * number of matching-shape vouchers).
     */
    private static final String CLAIM_POSTED_SQL_BASE =
            "SELECT m.* FROM finvchmain m " +
            "WHERE m.vchtype = 'G' AND m.release_status = 'P' " +
            "AND NOT EXISTS (SELECT 1 FROM finvchdet d2 WHERE d2.vchcode = m.vchcode AND d2.brsdt IS NOT NULL) ";

    /**
     * Appended to {@link #CLAIM_POSTED_SQL_BASE} only when {@link #variant}'s
     * {@link GeneralVoucherEditVariant#isRequireSharedGlcodeSibling()} is set — narrows candidates
     * to vouchers with at least two {@code finvchdet} rows sharing a {@code glcode} but differing
     * {@code slcode}, the exact shape {@code DbGeneralVoucherEditDataSource#claimOne} requires for
     * {@code DETAIL_LINE} variants. See the class Javadoc's "Shape-aware claim" section.
     */
    private static final String SHARED_GLCODE_SIBLING_CLAUSE =
            "AND EXISTS (SELECT 1 FROM finvchdet d, finvchdet d3 WHERE d.vchcode = m.vchcode " +
            "AND d3.vchcode = m.vchcode AND d3.glcode = d.glcode AND d3.slcode <> d.slcode) ";

    /** The variant this backfill is filling a shortfall for — {@code null} only for a caller that
     *  doesn't need shape-awareness (kept optional via {@link #GeneralVoucherEditRevertBackfillStrategy()}
     *  so existing non-shape-sensitive call sites need no change). Used solely to decide whether
     *  {@link #CLAIM_POSTED_SQL_BASE} needs {@link #SHARED_GLCODE_SIBLING_CLAUSE} appended. */
    private final GeneralVoucherEditVariant variant;

    /** Default constructor for callers whose shortfall genuinely doesn't care about voucher shape
     *  (e.g. header-only Edit variants) — equivalent to the pre-existing generic behavior. */
    public GeneralVoucherEditRevertBackfillStrategy() {
        this(null);
    }

    /** @param variant the Edit variant this backfill is filling a shortfall for; see {@link #variant}'s own Javadoc */
    public GeneralVoucherEditRevertBackfillStrategy(GeneralVoucherEditVariant variant) {
        this.variant = variant;
    }

    /** Builds {@link #CLAIM_POSTED_SQL_BASE}, appending {@link #SHARED_GLCODE_SIBLING_CLAUSE} only when {@link #variant} requires the shared-glcode-sibling shape. */
    private String claimPostedSql() {
        return variant != null && variant.isRequireSharedGlcodeSibling()
                ? CLAIM_POSTED_SQL_BASE + SHARED_GLCODE_SIBLING_CLAUSE + "ORDER BY m.vchcode"
                : CLAIM_POSTED_SQL_BASE + "ORDER BY m.vchcode";
    }

    /**
     * Claims and UI-reverts {@code neededQuantity} Posted General Vouchers to
     * {@code release_status='U'}; a claim or revert failure for one record is logged and skipped
     * rather than thrown, so a genuine remaining shortfall surfaces through
     * {@link com.gsl.database.DataAvailabilityGuard}'s own re-check instead of crashing the whole
     * data provider — see the class Javadoc's "Plan §16" section.
     */
    @Override
    public void backfill(ApiPlatform platform, int neededQuantity) {
        for (int i = 0; i < neededQuantity; i++) {
            // Finding A (Round 12): claimOnePostedCandidate now returns the already-fetched
            // FinVchMain directly, not a bare vchcode.
            FinVchMain claimedHeader = claimOnePostedCandidate();
            if (claimedHeader == null) {
                ReportLogger.dataBackfillWarn(
                        "[GeneralVoucherEditRevertBackfillStrategy] No unclaimed Posted (release_status='P') "
                        + "General Voucher found to revert — record {}/{} could not be prepared.",
                        i + 1, neededQuantity);
                continue;
            }
            long claimedVchcode = claimedHeader.getVchcode();
            // Finding B (Round 12 continued): schemeDocno is already in hand from the header above
            // — revertViaUi no longer re-fetches the same row just to read it.
            String claimedSchemeDocno = claimedHeader.getSchemeDocno();
            ReportLogger.dataBackfill(
                    "[GeneralVoucherEditRevertBackfillStrategy] Reverting record {}/{} via UI — vchcode={}",
                    i + 1, neededQuantity, claimedVchcode);

            try {
                StepExecutor.call("Revert General Voucher via UI", () -> {
                    revertViaUi(claimedVchcode, claimedSchemeDocno);
                    return null;
                });
            } catch (RuntimeException e) {
                // A UI revert that fails/times out must not crash the whole data provider (plan
                // §16) — log and move on; DataAvailabilityGuard's own re-check after backfill()
                // returns will surface a clear SkipException if this leaves a genuine shortfall.
                ReportLogger.dataBackfillWarn(
                        "[GeneralVoucherEditRevertBackfillStrategy] UI-driven revert failed for vchcode={} — {}",
                        claimedVchcode, e.getMessage());
                // Release even on failure — claimOnePostedCandidate() reserved this vchcode via
                // DocumentReservationRegistry (see its own Javadoc); a failed attempt must not
                // permanently strand the document as "reserved forever" with nothing ever using it.
                DocumentReservationRegistry.release(String.valueOf(claimedVchcode));
                continue;
            }

            StepExecutor.call("Verify General Voucher Landed Unposted in DB", () -> {
                verifyLandedUnposted(claimedVchcode);
                return null;
            });

            // Release now that THIS strategy's own use of the document (the live UI revert) is
            // complete — deliberately the single-key overload, not releaseCurrentThread(): this
            // method runs mid-load(), on the same thread that may already hold other, unrelated
            // successful reservations from earlier variants in the same pass, which must NOT be
            // released early. Freeing this specific key now lets DbGeneralVoucherEditDataSource
            // .claimOne's own retry (which runs right after backfill(), looking for the
            // now-Unposted document) legitimately re-reserve it — if it stayed held here, that
            // retry would find it "already reserved" and skip straight past it, defeating the
            // whole point of this backfill.
            DocumentReservationRegistry.release(String.valueOf(claimedVchcode));
        }
    }

    /**
     * Drives the exact same UI revert sequence {@code EditGeneralVoucher}'s "Unpost if Posted"
     * step and {@code RevertGeneralVoucher} already use, on a throwaway browser session created
     * and torn down entirely within this call — safe to run at {@code @DataSupplier} time since
     * {@code load()} runs single-threaded, before this thread's own {@code @Test}/
     * {@code SessionPool.borrow()} ever touches {@code DriverManager}'s thread-local driver slot.
     */
    private void revertViaUi(long vchcode, String schemeDocno) {
        UserCredential cred = UserPool.acquireSet1User(UserType.UI,
                "GeneralVoucherEditRevertBackfillStrategy.revertViaUi");
        try {
            Driver.initDriver();
            HomePage home = SessionManager.ensureLoggedIn(cred);
            GeneralVoucherListPage listPage = NavigationUtils.navigateToFinanceSideMenu(
                    home, MainMenuType.OPERATIONS, OperationsMenuType.CATEGORY, GeneralVoucherListPage.class);
            listPage.clearFilterAndRefreshList().resetToFirstPageIfEnabled();
            // Round 20: was selectDocumentInListPage(schemeDocno) — the raw page-by-page finder,
            // with no filter attempt at all. This strategy's candidates are deliberately old,
            // low-vchcode Posted vouchers (see claimOnePostedCandidate's own Javadoc), so a
            // page-by-page scan here reliably has to walk deep into the grid (confirmed live:
            // still on page 20 after 6 minutes hunting VG/00225/23-24, an FY 23-24 record behind
            // thousands of newer FY 26-27 rows). selectDocumentFast tries the fast exact-match
            // filter first (a single server-side query, indifferent to how old/deep the record
            // is), falling back to partial-match then this exact same page-by-page path only if
            // both fail — strictly a superset of the old behavior, matching every other
            // document-selection call site in this codebase (EditGeneralVoucher/
            // RevertGeneralVoucher/DeleteGeneralVoucher all already use this cascade).
            listPage.selectDocumentFast(schemeDocno, DocumentSearchStrategy.EXACT_MATCH);
            listPage.revertSelectedDocument();
        } finally {
            SessionManager.closeSessionAndReleaseUser(
                    "GeneralVoucherEditRevertBackfillStrategy UI revert backfill");
        }
    }

    /**
     * Closed-period guard (mirrors {@code DbGeneralVoucherRevertDataSource#claimOnePostedVoucher}'s
     * own guard): a document sitting in a closed accounting year or closed financial month can't
     * be reverted at all in the live app — driving {@link #revertViaUi} against one would just hit
     * the same "Finance month is already closed" rejection this whole strategy exists to avoid.
     * {@link #CLAIM_POSTED_SQL} can't cheaply filter on {@code admou_year}/{@code admou_month}
     * itself, so each candidate's header is re-fetched and checked via
     * {@code AccountingPeriodCache#isEditable} (a per-scan memoizing wrapper around
     * {@code AccountingPeriodResolver#isEditable} — avoids re-querying the same OU/year/month rows
     * for every rejected candidate) before being returned.
     *
     * <p><b>{@link DocumentReservationRegistry} guard, closed gap:</b> this claim previously
     * skipped the registry entirely — not mutually exclusive with any other concurrently-running
     * thread's own claim (`DbGeneralVoucherRevertDataSource`/`DbGeneralVoucherEditDataSource`
     * both reserve everything they claim), so a different thread could start using the exact same
     * document this strategy is independently reverting via its own live browser session.
     * Reserved here via {@link DocumentReservationRegistry#tryReserve}; released by
     * {@link #backfill} once this strategy's own use (the live UI revert) is complete — see that
     * method's own comment for why release, not hold, is required.
     */
    private FinVchMain claimOnePostedCandidate() {
        Jdbi jdbi = DatabaseManager.getJdbi();
        // With a variant, reuse the Edit data source's OWN shape filters and acceptance test so a
        // voucher is only reverted if that variant will accept it once Unposted (first-row Cheque/
        // NEFT mode + chqno for PAYMENT_INFO, Cash/Bank ledger type, shared-glcode sibling,
        // GL-line-type join, open period and fiscal-year room for FUTURE_DATE). Without this the
        // backfill reverted vouchers (e.g. 7467, 9340) that claimOne then rejected -> test skipped.
        // No variant = the old generic behaviour.
        List<FinVchMain> candidates = variant != null
                ? DbGeneralVoucherEditDataSource.fetchCandidates(jdbi, "P", variant)
                : jdbi.withHandle(handle ->
                handle.createQuery(claimPostedSql()).mapToBean(FinVchMain.class).list());

        // Memoizes the OU/year/month lookups isEditable below would otherwise re-query per
        // rejected candidate — see AccountingPeriodCache's own Javadoc.
        AccountingPeriodCache periodCache = new AccountingPeriodCache();
        java.util.function.Predicate<FinVchMain> acceptable = variant != null
                ? DbGeneralVoucherEditDataSource.acceptanceFilter(jdbi, variant, periodCache)
                : header -> periodCache.isEditable(jdbi, header, PeriodDomain.FINANCE);
        int rejected = 0;
        for (FinVchMain header : candidates) {
            if (!acceptable.test(header)) { rejected++; continue; }
            if (!DocumentReservationRegistry.tryReserve(String.valueOf(header.getVchcode()))) continue;
            ReportLogger.dataBackfill(
                    "[GeneralVoucherEditRevertBackfillStrategy] Chose Posted vchcode={} for variant {} "
                            + "({} shape-matching candidate(s), {} rejected by period/future-date rules first)",
                    header.getVchcode(), variant == null ? "<generic>" : variant, candidates.size(), rejected);
            return header;
        }
        return null;
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

    /** {@inheritDoc} Always {@code "generalVoucherEditRevertBackfill"} — see the inline comment below for why {@code K6BULK} never actually reaches this path. */
    @Override
    public String k6ScriptKey() {
        // No k6 script exists for this path — quantities here are always 1, so DataSourceRouter
        // never actually routes this to K6BULK.
        return "generalVoucherEditRevertBackfill";
    }
}
