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
import com.gsl.validation.snapshot.DocumentContext;
import com.gsl.validation.snapshot.TableRule;
import com.gsl.validation.snapshot.audit.AuditAssertion;
import com.gsl.validation.snapshot.finance.Aud2SnapshotDao;
import com.gsl.validation.snapshot.finance.VoucherDocumentContextResolver;
import com.tests.WebTest;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

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
        StepExecutor.step("Open Finance → General Voucher List", () -> {
            listRef[0] = NavigationUtils.navigateToFinanceSideMenu(
                    homeRef[0],
                    MainMenuType.OPERATIONS,
                    OperationsMenuType.CATEGORY,
                    GeneralVoucherListPage.class);
            ReportLogger.business("General Voucher list page opened");
        });

        // ── Step 4: Clear Filter + Refresh ────────────────────────────────────────────
        StepExecutor.step("Clear Filter and Refresh List", () -> {
            listRef[0].clearFilterAndRefreshList().resetToFirstPageIfEnabled();
            ReportLogger.business("Filter cleared, list refreshed, reset to first page (if not already there)");
        });

        // ── Step 5: Select document (paginated search) ────────────────────────────────
        StepExecutor.step("Select General Voucher in List Page", () -> {
            listRef[0].selectDocumentInListPage(testData.getExistingVoucherNo());
            ReportLogger.business("Selected voucher: " + testData.getExistingVoucherNo());
        });

        // ── Step 6: Click Actions ──────────────────────────────────────────────────────
        StepExecutor.step("Click Actions", () -> {
            listRef[0].clickActions();
            ReportLogger.business("Actions dropdown opened");
        });

        // ── Step 7: DB Check — Release Status Immediately Before Revert ──────────────────
        // A fresh, log-only read taken right before the state-changing click — Step 1's precheck
        // was captured before login/navigation/selection, leaving a race window in which another
        // concurrent process can legitimately change the voucher's live release_status. This
        // narrower read is the true pre-action snapshot; it drives diagnostics only, never
        // assertions, and its divergence from the Step-1 value (when it occurs) explains an
        // otherwise-confusing later mismatch instead of masquerading as a product defect.
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
        });

        // ── Step 8: Click Revert ───────────────────────────────────────────────────────
        StepExecutor.step("Click Revert", () -> {
            listRef[0].clickRevert();
            ReportLogger.business("Revert clicked");
        });

        // ── Step 9: Confirm Revert ─────────────────────────────────────────────────────
        StepExecutor.step("Confirm Revert", () -> {
            revertOutcomeRef[0] = listRef[0].confirmRevert();
            ReportLogger.pass("Revert confirmed for voucher: " + testData.getExistingVoucherNo()
                    + " — outcome: " + revertOutcomeRef[0]);
        });

        // ── Step 10: Logout (deferred — session may be reused by a later test on this thread;
        // real logout+driver-quit happens via RecoveryManager or end-of-block cleanup only) ────
        StepExecutor.step("Logout (deferred)", () ->
                ReportLogger.session("Logout deferred — session may be reused by a later test on this thread."));

        // ── Step 11: Database Validation ──────────────────────────────────────────────
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

            if (revertOutcomeRef[0] == GeneralVoucherRevertOutcome.CANNOT_REVERT_BRS_DONE) {
                // BRS (bank reconciliation) blocks Revert entirely — the live app dismisses with a
                // single-OK "cannot be reverted" info dialog and never changes release_status, so
                // 'after' must equal whatever it was immediately before the (rejected) Revert click.
                Assert.assertEquals(after, immediateBefore,
                        "Expected release_status to remain unchanged ('" + immediateBefore
                        + "') when Revert is blocked by BRS (bank reconciliation) for voucher "
                        + testData.getExistingVoucherNo() + ", but found '" + after + "'.");
                ReportLogger.pass("DB validated — BRS correctly blocked Revert; release_status unchanged at '"
                        + after + "' for voucher: " + testData.getExistingVoucherNo());
                return;
            }

            // Revert's only legal end-state is Unposted ('U') — whether the document started Posted
            // (dialog outcome UNPOSTED, 'P' → 'U') or was already Unposted (dialog outcome
            // ALREADY_UNPOSTED, a no-op that leaves it at 'U'). Asserting the absolute target value
            // directly (rather than comparing 'after' against the possibly-stale precheck 'before')
            // avoids false failures from the precheck/click race described above, while still hard-
            // failing on a genuine defect (e.g. landing on 'P' or 'N' instead of 'U').
            Assert.assertEquals(after, "U",
                    "Expected release_status to be 'U' (Unposted) after Revert for voucher "
                    + testData.getExistingVoucherNo() + " — dialog outcome=" + revertOutcomeRef[0]
                    + ", precheck-before='" + before + "', immediate-before='" + immediateBefore
                    + "', but found '" + after + "'.");

            // Audit-trail assertion — the live re-read above only proves the business table
            // transitioned; this proves the audit trail's own record of that transition exists too.
            // Skipped for the ALREADY_UNPOSTED no-op case (before/immediateBefore was already 'U' —
            // there is no genuine transition for the audit trail to have recorded).
            if (!"U".equals(immediateBefore)) {
                DocumentContext auditContext = new VoucherDocumentContextResolver()
                        .resolve(testData.getExistingVoucherNo(), VoucherDomain.GV);
                TableRule finvchmainRule = auditContext.getPersistenceMap().findByTableName("finvchmain");
                Map<String, Object> beforeRow = Map.of("vchcode", auditContext.getVchcode(),
                        "release_status", immediateBefore);
                AuditAssertion.assertAuditUpdated(auditContext, new Aud2SnapshotDao(), finvchmainRule,
                        beforeRow, "release_status", immediateBefore, "U");
                ReportLogger.pass("Audit trail validated — release_status UPDATE ('" + immediateBefore
                        + "' → 'U') recorded for voucher: " + testData.getExistingVoucherNo());
            }

            // No more Excel-declared "Release Status" cross-check here (plan §16 — the column was
            // removed entirely from RevertVoucherInfo, not merely downgraded to a soft cross-check).
            // Step 1's live-read 'before' value above is, and remains, the sole driver of every
            // downstream decision/assertion — there is nothing declarative left to compare it against.

            // Surface a precheck/immediate-before divergence prominently — this is exactly the race
            // condition that previously caused false failures (e.g. VG/00303 in run 20260812_230836).
            if (immediateBefore != null && !immediateBefore.equals(before)) {
                ReportLogger.warn("release_status changed between Step-1 precheck ('" + before
                        + "') and the immediate-before read ('" + immediateBefore + "') for voucher "
                        + testData.getExistingVoucherNo() + " — a concurrent process modified this document "
                        + "mid-test; this is expected to be benign as long as 'after' ended at 'U'.");
            }

            ReportLogger.pass("DB validated — precheck-before='" + before + "', immediate-before='" + immediateBefore
                    + "', after='" + after + "', dialog outcome=" + revertOutcomeRef[0]
                    + " for voucher: " + testData.getExistingVoucherNo());
        });
    }
}
