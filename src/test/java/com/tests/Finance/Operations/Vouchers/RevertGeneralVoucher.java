package com.tests.Finance.Operations.Vouchers;

import com.gsl.annotations.ApiFlow;
import com.gsl.annotations.FrameworkAnnotation;
import com.gsl.annotations.TestCaseId;
import com.gsl.database.DatabaseManager;
import com.gsl.database.DbQueryBuffer;
import com.gsl.database.dao.adm.AdmSiteDao;
import com.gsl.database.dao.finance.FinGlDao;
import com.gsl.database.dao.finance.FinVchMainDao;
import com.gsl.driver.DriverManager;
import com.gsl.enums.AppOperation;
import com.gsl.enums.TestAuthor;
import com.gsl.enums.VoucherDomain;
import com.gsl.model.adm.AdmSite;
import com.gsl.model.finance.FinGl;
import com.gsl.model.finance.FinVchMain;
import com.gsl.network.pattern.FinanceApiPattern;
import com.gsl.pages.Finance.Operations.GeneralVoucherListPage;
import com.gsl.pages.HomePage;
import com.gsl.pages.enums.Finance.MainMenuType;
import com.gsl.pages.enums.Finance.Operations.GeneralVoucher.GeneralVoucherRevertOutcome;
import com.gsl.pages.enums.Finance.Operations.OperationsMenuType;
import com.gsl.pages.enums.topmenucomponent.MainModuleType;
import com.gsl.reports.DbGatheringExtentLogger;
import com.gsl.reports.GeneralVoucherExtentDescriptionLogger;
import com.gsl.reports.ReportLogger;
import com.gsl.reports.StepExecutor;
import com.gsl.testdata.Finance.Operations.Vouchers.GeneralVoucher.DocumentInformation;
import com.gsl.testdata.UserCredential;
import com.gsl.testdata.dto.MergedFinanceGeneralVoucherData;
import com.gsl.utils.Log;
import com.gsl.utils.NavigationUtils;
import com.gsl.utils.dataprovider.MergedFinGeneralVoucherDataSupplier;
import com.gsl.utils.dataprovider.fixture.finance.DocumentSearchStrategy;
import com.gsl.validation.snapshot.DocumentContext;
import com.gsl.validation.snapshot.ExpectedChangeSet;
import com.gsl.validation.snapshot.SnapshotService;
import com.gsl.validation.snapshot.VoucherSnapshot;
import com.gsl.validation.snapshot.VoucherSnapshotValidator;
import com.gsl.validation.snapshot.finance.Aud2SnapshotDao;
import com.gsl.validation.snapshot.finance.GeneralVoucherRevertChangeSets;
import com.gsl.validation.snapshot.finance.VoucherDocumentContextResolver;
import com.tests.WebTest;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;

/**
 * Reverts an existing General Voucher from the Finance → Operations → General Voucher list
 * page — mirrors {@code DeleteArVoucher}'s {@code StepExecutor.step(...)} structure. Handles
 * all three starting states the live app actually distinguishes: a currently Posted document
 * (Revert shows a Yes/No "will be un-posted" confirmation), a document already un-posted (Revert
 * shows a single-button "already in un-posted state" info message, no-op), and a document whose
 * line(s) are already bank-reconciled (BRS) (Revert shows a single-button "cannot be reverted"
 * info message, no-op, release_status stays unchanged).
 *
 * <h3>Flow (list page)</h3>
 * <ol>
 *   <li>DB pre-check — verify the document exists in {@code finvchmain} and capture its
 *       {@code release_status} before touching the UI. Hard-fails immediately if missing.</li>
 *   <li>Clear Filter + Refresh the list grid, so no leftover filter/stale page hides the
 *       target document.</li>
 *   <li>Select the document's row checkbox — {@link GeneralVoucherListPage#selectDocumentInListPage}
 *       (searches the current page via the proven scroll/jump/focusRow fallback, then pages
 *       forward via the footer's Next Page control if not found, until found or the last page
 *       is exhausted).</li>
 *   <li>Click Actions — {@link GeneralVoucherListPage#clickActions}.</li>
 *   <li>DB check — re-read {@code release_status} immediately before the Revert click, since the
 *       first DB pre-check (above) ran before login/navigation and can go stale if another
 *       concurrent process touches the same document in between. Log-only, no assertion.</li>
 *   <li>Click Revert — {@link GeneralVoucherListPage#clickRevert}.</li>
 *   <li>Confirm whichever dialog appears — {@link GeneralVoucherListPage#confirmRevert} — and
 *       capture which of the two live-confirmed outcomes occurred.</li>
 * </ol>
 *
 * <h3>Pre-conditions</h3>
 * <ul>
 *   <li>{@code testdata.source=EXCEL} — a {@code RevertVoucherInfo} sheet row plus a matching
 *       {@code MasterTestData} row with {@code testcasename=revertGeneralVoucher}.</li>
 *   <li>{@code testdata.source=DB} — {@code DbGeneralVoucherRevertDataSource} runs the same
 *       Mandatory/Regression-phase combination matrix {@code CreateGeneralVoucher} uses (see
 *       {@code GeneralVoucherCombinations}, filtered by {@code gv.group}/{@code gv.tag}), claiming
 *       one Posted ({@code release_status='P'}) General Voucher per combination whose GL account
 *       type matches that combination's type, backfilling one via WebApi
 *       ({@code GeneralVoucherApiBackfillStrategy}) when none is left to claim.</li>
 *   <li>{@code existingVoucherNo} must reference an existing General Voucher — Posted or
 *       already un-posted are both legal starting states; the DB pre-check and the DB
 *       validation at the end both adapt to whichever it actually is.</li>
 * </ul>
 *
 * <p>Revert from the Document View page is a separate, later phase — out of scope here. The
 * "select all documents" list-page checkbox is also a separate, later feature.
 */
@ApiFlow(FinanceApiPattern.GENERAL_VOUCHER)
public class RevertGeneralVoucher extends WebTest {

    @FrameworkAnnotation(value = TestAuthor.ARANI, module = MainModuleType.FINANCE, subModule = "FinanceGenVoucher", operation = AppOperation.REVERT)
    @TestCaseId("FIN-GV-REVERT")
    @Test(description = "Revert an existing General Voucher from the List Page",
            dataProvider = "FinanceGeneralVoucherData", dataProviderClass = MergedFinGeneralVoucherDataSupplier.class)
    public void revertGeneralVoucher(UserCredential user, MergedFinanceGeneralVoucherData data) {

        Log.info("Revert General Voucher (List Page): {}", data.getExistingVoucherNo());
        Log.info("Release status: {}", data.getReleaseStatus());

        final MergedFinanceGeneralVoucherData testData = data;

        // Cross-step object holders
        final HomePage[]                     homeRef               = new HomePage[1];
        final GeneralVoucherListPage[]        listRef               = new GeneralVoucherListPage[1];
        final String[]                        beforeReleaseStatusRef = new String[1];
        final String[]                        immediateBeforeReleaseStatusRef = new String[1];
        final GeneralVoucherRevertOutcome[]   revertOutcomeRef      = new GeneralVoucherRevertOutcome[1];
        final DocumentContext[]               contextRef            = new DocumentContext[1];
        final VoucherSnapshot[]               beforeSnapshotRef     = new VoucherSnapshot[1];
        final VoucherSnapshot[]               afterSnapshotRef      = new VoucherSnapshot[1];

        // ── Step 1: DB Pre-Check — Verify Document Exists ────────────────────────────
        StepExecutor.step("DB Pre-Check — Verify Document Exists", () -> {
            DbQueryBuffer.start();
            FinVchMain header;
            try {
                header = DatabaseManager.getJdbi().onDemand(FinVchMainDao.class)
                        .findBySchemeDocno(testData.getExistingVoucherNo())
                        .orElse(null);
            } finally {
                List<String> queries = DbQueryBuffer.flush();
                queries.forEach(ReportLogger::db);
            }
            Assert.assertNotNull(header,
                    "[DB-PreCheck] finvchmain row not found for scheme_docno=" + testData.getExistingVoucherNo()
                    + " — target document does not exist in DB; cannot proceed with Revert.");
            beforeReleaseStatusRef[0] = header.getReleaseStatus();
            ReportLogger.business("Captured release_status='" + beforeReleaseStatusRef[0]
                    + "' (before Revert) for voucher: " + testData.getExistingVoucherNo());

            // Builds a report-safe DocumentInformation from this live header — Revert changes
            // none of these fields, so the Header table simply mirrors the document as found.
            // Populated here (not in either data source) since this live read, not the
            // @DataSupplier-time row, is this class's sole source of truth for document state
            // (see class Javadoc) — covers DB-mode and Excel-mode rows identically.
            AdmSite site = DatabaseManager.getJdbi().onDemand(AdmSiteDao.class).findByCode(header.getAdmsiteCodeOwner());
            FinGl cashBankGl = DatabaseManager.getJdbi().onDemand(FinGlDao.class).findByGlCode(header.getGlcode());
            DocumentInformation docInfo = new DocumentInformation(
                    testData.getScenarioTypeLabel(),
                    header.getSchemeDocno(),
                    header.getVchdt(),
                    header.getRefno(),
                    cashBankGl != null ? cashBankGl.getGlname() : null,
                    header.getNartext());
            docInfo.setOwnersite(site != null ? site.getName() : null);
            testData.setDocumentInformation(docInfo);

            com.gsl.reports.StepSummaryBuffer.record("Pre-Check summary — voucher=" + testData.getExistingVoucherNo()
                    + ", found=Yes, release_status(before)='" + beforeReleaseStatusRef[0]
                    + "', owner_site='" + (site != null ? site.getName() : "—")
                    + "', cash/bank GL='" + (cashBankGl != null ? cashBankGl.getGlname() : "—") + "'");
        });

        // ── Report: Test Data — Header / Expected Results / Income-Expense Detail ────────
        DbGatheringExtentLogger.log(testData);
        GeneralVoucherExtentDescriptionLogger.log(testData);

        // ── Step 2: Login ─────────────────────────────────────────────────────────────
        StepExecutor.step("Login to Application", () -> {
            UserCredential u = DriverManager.getCurrentUser();
            homeRef[0] = com.gsl.driver.SessionManager.ensureLoggedIn(u);
            ReportLogger.business("Logged in as: " + u.username());
        });

        // ── Step 3: Navigate ──────────────────────────────────────────────────────────
        // Business log + "Navigated: ..." summary are now recorded inside
        // NavigationUtils.navigateToFinanceSideMenu itself.
        StepExecutor.step("Open Finance → General Voucher List", () -> listRef[0] =
                NavigationUtils.navigateToFinanceSideMenu(
                        homeRef[0],
                        MainMenuType.OPERATIONS,
                        OperationsMenuType.CATEGORY,
                        GeneralVoucherListPage.class));

        // ── Step 4: Clear Filter + Select Document (tiered fast search, falling back to
        // page-by-page) — consolidated into one call via VoucherListPageBase.selectDocumentForAction,
        // which composes clearFilterAndRefreshList().resetToFirstPageIfEnabled().selectDocumentFast(...)
        // (previously 2 separate steps/calls, now the one common function every Revert/Delete/Edit
        // test across GV/AR/AP shares) ──────────────────────────────────────────────────
        StepExecutor.step("Select General Voucher in List Page", () -> {
            DocumentSearchStrategy strategy = testData.getSearchStrategy() == null
                    ? DocumentSearchStrategy.EXACT_MATCH
                    : testData.getSearchStrategy();
            listRef[0].selectDocumentForAction(testData.getExistingVoucherNo(), strategy);
            ReportLogger.business("Filter cleared, list refreshed, selected voucher: "
                    + testData.getExistingVoucherNo());
        });

        // ── Step 6: DB Check — Release Status Immediately Before Revert ──────────────────
        // A fresh, log-only read taken right before the state-changing click — Step 1's precheck
        // was captured before login/navigation/selection, leaving a race window in which another
        // concurrent process can legitimately change the voucher's live release_status. This
        // narrower read is the true pre-action snapshot; it drives diagnostics only, never
        // assertions, and its divergence from the Step-1 value (when it occurs) explains an
        // otherwise-confusing later mismatch instead of masquerading as a product defect.
        //
        // MOVED (was Step 7, after "Click Actions") — confirmed live, reports/runs/20260913_173951
        // FIN-GV-REVERT-03: opening the Actions dropdown and then running this DB read plus the
        // snapshot capture below (Step 7b) left the dropdown idle for several seconds before the
        // Revert click ever ran, long enough for an async grid event to invalidate the row
        // selection/close the dropdown — the reselect-and-retry overload on clickRevert (Round 10
        // pattern) then had to recover, but was itself racing the same window on its own retry.
        // Both DB-only steps now run BEFORE Actions is opened, so the dropdown opens immediately
        // ahead of the click with no idle gap in between.
        StepExecutor.step("DB Check — Release Status Immediately Before Revert", () -> {
            DbQueryBuffer.start();
            FinVchMain header;
            try {
                header = DatabaseManager.getJdbi().onDemand(FinVchMainDao.class)
                        .findBySchemeDocno(testData.getExistingVoucherNo())
                        .orElse(null);
            } finally {
                List<String> queries = DbQueryBuffer.flush();
                queries.forEach(ReportLogger::db);
            }
            Assert.assertNotNull(header,
                    "[DB-Check] finvchmain row not found for scheme_docno=" + testData.getExistingVoucherNo()
                    + " — target document disappeared before Revert could be clicked.");
            immediateBeforeReleaseStatusRef[0] = header.getReleaseStatus();
            ReportLogger.business("Release status immediately before Revert action: '"
                    + immediateBeforeReleaseStatusRef[0] + "' for voucher: " + testData.getExistingVoucherNo());
            com.gsl.reports.StepSummaryBuffer.record("Immediate-before release_status='"
                    + immediateBeforeReleaseStatusRef[0] + "' for voucher " + testData.getExistingVoucherNo());
        });

        // ── Step 7b: Resolve Document Context + Capture BEFORE Snapshot ──────────────────
        // Full snapshot (not just finvchmain) — brings Revert's DB validation up to the same
        // snapshot-diff-engine coverage Edit/Create already have (see
        // GeneralVoucherRevertChangeSets' Javadoc): finvchdet/fincosttag get NO explicit overrides
        // at Step 11, so the persistence map's own table-wide MUST_NOT_CHANGE default is what
        // actually asserts they stay untouched by this Revert — coverage this test never had
        // before. Captured here (immediately before the state-changing click) rather than at
        // Step 1's precheck, for the same staleness reason Step 7's own re-read exists.
        StepExecutor.step("Resolve Document Context + Capture BEFORE Snapshot", () -> {
            contextRef[0] = new VoucherDocumentContextResolver()
                    .resolve(testData.getExistingVoucherNo(), VoucherDomain.GV);
            beforeSnapshotRef[0] = new SnapshotService(new Aud2SnapshotDao()).capture(contextRef[0]);
            ReportLogger.business("Document context resolved and BEFORE snapshot captured for voucher: "
                    + testData.getExistingVoucherNo());
            recordSnapshotTableSummary("BEFORE Snapshot", beforeSnapshotRef[0]);
            // Item U: Revert previously had NO audit-state visibility at all, before or after —
            // both sets are already resolved on contextRef[0], so this is just rendering them in
            // the same dedicated shape the AFTER-snapshot audit check elsewhere in the codebase
            // uses, ahead of the state-changing Revert click.
            new com.gsl.validation.snapshot.finance.AuditStateSummary(
                    contextRef[0].getAuditActiveTables(), contextRef[0].getAuditTriggerDisabledTables())
                    .logTable("BEFORE Revert — Audit State");
        });

        // ── Step 7c: Click Actions ─────────────────────────────────────────────────────
        // MOVED here (was Step 6, before the two DB-only steps above) — see their Javadoc for
        // why: this now opens the dropdown immediately ahead of the Revert click, with no idle
        // DB-round-trip gap in between for an async grid event to invalidate the selection.
        StepExecutor.step("Click Actions", () -> {
            listRef[0].clickActions();
            ReportLogger.business("Actions dropdown opened");
        });

        // ── Step 8: Click Revert ───────────────────────────────────────────────────────
        // Document-number-aware overload (see GeneralVoucherListPage#clickRevert(String,
        // DocumentSearchStrategy)'s Javadoc): on a confirmed "Actions dropdown not open" failure —
        // seen live in reports/runs/20260913_133337 (FIN-GV-REVERT-02) — re-selects the row and
        // re-opens Actions once before giving up, instead of failing outright on the first check.
        StepExecutor.step("Click Revert", () -> {
            DocumentSearchStrategy strategy = testData.getSearchStrategy() == null
                    ? DocumentSearchStrategy.EXACT_MATCH
                    : testData.getSearchStrategy();
            listRef[0].clickRevert(testData.getExistingVoucherNo(), strategy);
            ReportLogger.business("Revert clicked");
        });

        // ── Step 9: Confirm Revert ─────────────────────────────────────────────────────
        StepExecutor.step("Confirm Revert", () -> {
            revertOutcomeRef[0] = listRef[0].confirmRevert();
            ReportLogger.pass("Revert confirmed for voucher: " + testData.getExistingVoucherNo()
                    + " — outcome: " + revertOutcomeRef[0]);

            // A row deliberately claimed to hit a specific non-success outcome (e.g. a closed-
            // accounting-period candidate) must actually land on that exact outcome — not just
            // whatever happened to occur — otherwise a broken period-lock guard upstream (or in
            // the live app itself) would silently pass as "reverted successfully".
            if (testData.getExpectedRevertOutcome() != null) {
                Assert.assertEquals(revertOutcomeRef[0], testData.getExpectedRevertOutcome(),
                        "Expected Revert outcome=" + testData.getExpectedRevertOutcome()
                        + " for voucher " + testData.getExistingVoucherNo()
                        + " (deliberately claimed to exercise that outcome), but got "
                        + revertOutcomeRef[0] + " instead.");
                ReportLogger.pass("Negative outcome correctly triggered: " + revertOutcomeRef[0]
                        + " for voucher: " + testData.getExistingVoucherNo());
            }
            com.gsl.reports.StepSummaryBuffer.record("Revert confirmed — voucher=" + testData.getExistingVoucherNo()
                    + ", outcome=" + revertOutcomeRef[0]
                    + (testData.getExpectedRevertOutcome() != null
                            ? " (Revert correctly blocked/handled — matches expected outcome)"
                            : ""));
        });

        // ── Step 10: Logout (deferred — session may be reused by a later test on this thread;
        // real logout+driver-quit happens via RecoveryManager or end-of-block cleanup only) ────
        StepExecutor.step("Logout (deferred)", () ->
                ReportLogger.session("Logout deferred — session may be reused by a later test on this thread."));

        // ── Step 10b: Capture AFTER Snapshot ──────────────────────────────────────────
        StepExecutor.step("Capture AFTER Snapshot", () -> {
            afterSnapshotRef[0] = new SnapshotService(new Aud2SnapshotDao()).capture(contextRef[0]);
            ReportLogger.business("AFTER snapshot captured for voucher: " + testData.getExistingVoucherNo());
            recordSnapshotTableSummary("AFTER Snapshot", afterSnapshotRef[0]);
        });

        // ── Step 11: Database Validation ──────────────────────────────────────────────
        // All per-table checks (release_status transition on finvchmain/finpost, plus the
        // previously-uncovered "finvchdet/fincosttag must stay unchanged" checks) are asserted
        // SOFTLY through one VoucherSnapshotValidator/assertAll() pass — every table's failures are
        // collected first and surface together in ONE consolidated failure report at the end of
        // this step, rather than a hard assertion on release_status alone stopping the test before
        // the snapshot-diff engine ever got to check the other tables. This replaces the previous
        // hard Assert.assertEquals release_status checks AND the single-column
        // AuditAssertion.assertAuditUpdated call — GeneralVoucherRevertChangeSets' finvchmain/
        // finpost release-trio rules already assert the exact same release_status transition (with
        // an equally descriptive per-column failure message), and DiffEngine's own Pass 2
        // subsumes the audit-trail check for every active table, not just finvchmain.
        StepExecutor.step("Database Validation", () -> {
            FinVchMain afterHeader = DatabaseManager.getJdbi().onDemand(FinVchMainDao.class)
                    .findBySchemeDocno(testData.getExistingVoucherNo())
                    .orElseThrow(() -> new IllegalStateException(
                            "[DB-Validate] finvchmain row not found for scheme_docno="
                            + testData.getExistingVoucherNo()));

            String before          = beforeReleaseStatusRef[0];
            String immediateBefore = immediateBeforeReleaseStatusRef[0];
            String after           = afterHeader.getReleaseStatus();
            Log.info("[DB-Validate] scheme_docno='{}', release_status precheck-before='{}', immediate-before='{}', "
                    + "after='{}', dialogOutcome={}",
                    testData.getExistingVoucherNo(), before, immediateBefore, after, revertOutcomeRef[0]);

            // The ABSOLUTE target release_status this Revert attempt should have produced —
            // GeneralVoucherRevertChangeSets derives MUST_CHANGE-vs-MUST_NOT_CHANGE purely by
            // comparing the BEFORE snapshot's own captured value against this, so a single builder
            // call covers every outcome uniformly.
            String expectedNewStatus;
            String outcomeNote;
            if (revertOutcomeRef[0] == GeneralVoucherRevertOutcome.CANNOT_REVERT_BRS_DONE) {
                // BRS (bank reconciliation) blocks Revert entirely — the live app dismisses with a
                // single-OK "cannot be reverted" info dialog and never changes release_status.
                expectedNewStatus = immediateBefore;
                outcomeNote = "BRS correctly blocked Revert";
            } else if (revertOutcomeRef[0] == GeneralVoucherRevertOutcome.CANNOT_REVERT_MONTH_CLOSED) {
                // Same shape as the BRS branch: the document's own accounting year/financial month
                // is closed, the live app dismisses with a single-OK "already closed" info dialog.
                expectedNewStatus = immediateBefore;
                outcomeNote = "closed period correctly blocked Revert";
            } else {
                // Revert's only legal end-state is Unposted ('U') — whether the document started
                // Posted (dialog outcome UNPOSTED, 'P' → 'U') or was already Unposted (dialog
                // outcome ALREADY_UNPOSTED, a no-op that leaves it at 'U').
                expectedNewStatus = "U";
                outcomeNote = "Revert completed: " + (immediateBefore == null ? "?" : immediateBefore) + " -> " + expectedNewStatus;

                // Surface a precheck/immediate-before divergence prominently — this is exactly the
                // race condition that previously caused false failures (e.g. VG/00303 in run
                // 20260812_230836). Informational only — the snapshot-diff engine below is what
                // actually asserts correctness, against the real immediate-before/after DB values.
                if (immediateBefore != null && !immediateBefore.equals(before)) {
                    ReportLogger.warn("release_status changed between Step-1 precheck ('" + before
                            + "') and the immediate-before read ('" + immediateBefore + "') for voucher "
                            + testData.getExistingVoucherNo() + " — a concurrent process modified this "
                            + "document mid-test; this is expected to be benign as long as 'after' ended at 'U'.");
                }
            }

            // (The "deliberately claimed to exercise a specific outcome" hard assertion already ran
            // in Step 9/Confirm Revert, before this step — a wrong OUTCOME there means every
            // downstream expectedNewStatus/changeSet decision above would be built on the wrong
            // premise, so it stays a hard, fail-fast check rather than folded into this step's soft
            // snapshot-diff pass.)

            ExpectedChangeSet changeSet = GeneralVoucherRevertChangeSets.revert(
                    contextRef[0], beforeSnapshotRef[0], expectedNewStatus);

            VoucherSnapshotValidator validator = new VoucherSnapshotValidator();
            validator.validate("General Voucher Revert — Snapshot Validation — [" + testData.getExistingVoucherNo() + "]",
                    contextRef[0], beforeSnapshotRef[0], afterSnapshotRef[0], changeSet);
            validator.assertAll();

            ReportLogger.pass("DB validated — " + outcomeNote + "; precheck-before='" + before
                    + "', immediate-before='" + immediateBefore + "', after='" + after
                    + "', expected='" + expectedNewStatus + "', dialog outcome=" + revertOutcomeRef[0]
                    + " for voucher: " + testData.getExistingVoucherNo());
        });
    }

    /**
     * Point 9 summary helper — names the tables/row counts actually captured in a BEFORE/AFTER
     * snapshot (plan §Point 9 Revert steps 8/12: "the data is already in scope, no new
     * instrumentation needed"), instead of the previous blind one-line "captured" marker. One
     * line per table (not one long comma-joined line) — matches the report's established
     * "Level 1 — finvchmain field checks" house style for readability.
     */
    private static void recordSnapshotTableSummary(String label, VoucherSnapshot snapshot) {
        if (snapshot == null || snapshot.getTables() == null || snapshot.getTables().isEmpty()) {
            com.gsl.reports.StepSummaryBuffer.record(label + ": no tables captured");
            return;
        }
        for (var entry : snapshot.getTables().entrySet()) {
            com.gsl.reports.StepSummaryBuffer.record(
                    label + " — " + entry.getKey() + ": " + entry.getValue().rowCount() + " row(s)");
        }
    }
}
