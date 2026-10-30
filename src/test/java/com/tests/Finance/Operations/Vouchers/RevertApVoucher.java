package com.tests.Finance.Operations.Vouchers;

import com.gsl.annotations.ApiFlow;
import com.gsl.annotations.FrameworkAnnotation;
import com.gsl.annotations.TestCaseId;
import com.gsl.database.DatabaseManager;
import com.gsl.database.DbQueryBuffer;
import com.gsl.database.dao.adm.AdmSiteDao;
import com.gsl.database.dao.finance.FinGlDao;
import com.gsl.database.dao.finance.FinVchDetDao;
import com.gsl.database.dao.finance.FinVchMainDao;
import com.gsl.driver.DriverManager;
import com.gsl.enums.AppOperation;
import com.gsl.enums.TestAuthor;
import com.gsl.enums.VoucherDomain;
import com.gsl.flow.Finance.Operations.Common.DocumentAdjustmentFlow;
import com.gsl.flow.Finance.Operations.Common.VoucherEditFlowSteps;
import com.gsl.model.adm.AdmSite;
import com.gsl.model.finance.FinGl;
import com.gsl.model.finance.FinVchDet;
import com.gsl.model.finance.FinVchMain;
import com.gsl.network.pattern.FinanceApiPattern;
import com.gsl.pages.Finance.Operations.ApVoucherListPage;
import com.gsl.pages.HomePage;
import com.gsl.pages.enums.Finance.MainMenuType;
import com.gsl.pages.enums.Finance.Operations.Common.VoucherRevertOutcome;
import com.gsl.pages.enums.Finance.Operations.OperationsMenuType;
import com.gsl.pages.enums.topmenucomponent.MainModuleType;
import com.gsl.reports.ApVoucherExtentDescriptionLogger;
import com.gsl.reports.DbGatheringExtentLogger;
import com.gsl.reports.ReportLogger;
import com.gsl.reports.StepExecutor;
import com.gsl.reports.StepSummaryBuffer;
import com.gsl.testdata.Finance.Operations.Vouchers.APVoucher.DocumentInformation;
import com.gsl.testdata.UserCredential;
import com.gsl.testdata.dto.MergedFinanceApVoucherData;
import com.gsl.utils.Log;
import com.gsl.utils.NavigationUtils;
import com.gsl.utils.dataprovider.MergedFinApVoucherDataSupplier;
import com.gsl.utils.dataprovider.fixture.finance.DocumentSearchStrategy;
import com.gsl.utils.dataprovider.source.finance.common.AccountingPeriodResolver;
import com.gsl.validation.snapshot.DocumentContext;
import com.gsl.validation.snapshot.ExpectedChangeSet;
import com.gsl.validation.snapshot.SnapshotService;
import com.gsl.validation.snapshot.VoucherSnapshot;
import com.gsl.validation.snapshot.VoucherSnapshotValidator;
import com.gsl.validation.snapshot.finance.APVoucherRevertChangeSets;
import com.gsl.validation.snapshot.finance.Aud2SnapshotDao;
import com.gsl.validation.snapshot.finance.VoucherDocumentContextResolver;
import com.tests.WebTest;
import org.jdbi.v3.core.Jdbi;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.math.BigDecimal;
import java.util.List;

/**
 * Reverts an existing AP Voucher from the Finance → Operations → AP Voucher list page — mirrors
 * {@code RevertGeneralVoucher}'s exact {@code StepExecutor.step(...)} structure (DB pre-check →
 * login → nav → clear filter → select → <b>untag if tagged (new)</b> → immediate-before DB check →
 * resolve context + BEFORE snapshot → click Actions → click Revert → confirm Revert → logout
 * (deferred) → AFTER snapshot → DB validation), with one new step inserted: a Posted+tagged AP
 * Voucher cannot be Reverted until its {@code fintag} row(s) are removed via the Document
 * Adjustment "untag" API (see {@link DocumentAdjustmentFlow#untagIfNeeded}) — that step runs right
 * after the document is selected in the list and before the immediate-before DB read, so the
 * untag (if any) happens before any state-changing UI click.
 *
 * <h3>Pre-conditions</h3>
 * <ul>
 *   <li>{@code testdata.source=EXCEL} — an {@code ApRevertVoucherInfo} sheet row plus a matching
 *       {@code MasterTestData} row with {@code testcasename=revertApVoucher}.</li>
 *   <li>{@code testdata.source=DB} — {@code DbApVoucherRevertDataSource} claims one live Posted
 *       AP Voucher per {@code TAGGED}/{@code UNTAGGED} combination cell (see that class's Javadoc),
 *       filtered by {@code apv.group}.</li>
 *   <li>{@code existingVoucherNo} must reference an existing AP Voucher — Posted or already
 *       un-posted are both legal starting states; the DB pre-check and the DB validation at the
 *       end both adapt to whichever it actually is.</li>
 * </ul>
 *
 * <p><b>Flagged risk</b>: {@code APVoucherMainGrid.confirmRevert()}'s dialog-text matching is
 * unconfirmed against AP's own live DOM — see that method's own Javadoc.
 */
@ApiFlow(FinanceApiPattern.AP_VOUCHER)
public class RevertApVoucher extends WebTest {

    @FrameworkAnnotation(value = TestAuthor.ARANI, module = MainModuleType.FINANCE, subModule = "FinanceAPVoucher", operation = AppOperation.REVERT)
    @TestCaseId("FIN-AP-REVERT")
    @Test(description = "Revert an existing AP Voucher from the List Page",
            dataProvider = "FinanceApVoucherData", dataProviderClass = MergedFinApVoucherDataSupplier.class)
    public void revertApVoucher(UserCredential user, MergedFinanceApVoucherData data) {

        Log.info("Revert AP Voucher (List Page): {}", data.getExistingVoucherNo());
        Log.info("Release status: {}", data.getReleaseStatus());

        final MergedFinanceApVoucherData testData = data;

        // Cross-step object holders
        final HomePage[]                    homeRef                = new HomePage[1];
        final ApVoucherListPage[]           listRef                = new ApVoucherListPage[1];
        final String[]                      beforeReleaseStatusRef = new String[1];
        final String[]                      immediateBeforeReleaseStatusRef = new String[1];
        final VoucherRevertOutcome[]      revertOutcomeRef       = new VoucherRevertOutcome[1];
        final DocumentContext[]             contextRef             = new DocumentContext[1];
        final VoucherSnapshot[]             beforeSnapshotRef      = new VoucherSnapshot[1];
        final VoucherSnapshot[]             afterSnapshotRef       = new VoucherSnapshot[1];

        // db.available=no: the finvchmain reads and the snapshot diff are replaced by rootview status reads through the API, and the
        // voucher's identity comes from the API data source that created it (ApiVoucherRef).
        final boolean noDb = !com.gsl.database.DbAvailability.isAvailable();

        // ── Step 1: DB Pre-Check — Verify Document Exists ────────────────────────────
        StepExecutor.step(noDb ? "API Pre-Check — Verify Document Exists" : "DB Pre-Check — Verify Document Exists", () -> {
            if (noDb) {
                String status = readStatusViaApi(testData);
                Assert.assertNotNull(status, "[API-PreCheck] voucher " + testData.getExistingVoucherNo()
                        + " not found through the API — target document does not exist; cannot proceed with Revert.");
                beforeReleaseStatusRef[0] = status;
                testData.setDocumentInformation(new DocumentInformation(
                        testData.getScenarioTypeLabel(), null, testData.getExistingVoucherNo(), null, null, null, null, null));
                ReportLogger.business("Captured release status='" + status + "' (before Revert) for voucher: " + testData.getExistingVoucherNo());
                return;
            }
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

            AdmSite site = DatabaseManager.getJdbi().onDemand(AdmSiteDao.class).findByCode(header.getAdmsiteCodeOwner());
            FinGl cashBankGl = DatabaseManager.getJdbi().onDemand(FinGlDao.class).findByGlCode(header.getGlcode());
            DocumentInformation docInfo = new DocumentInformation(
                    testData.getScenarioTypeLabel(),
                    site != null ? site.getName() : null,
                    header.getSchemeDocno(),
                    cashBankGl != null ? cashBankGl.getGlname() : null,
                    header.getNartext(),
                    header.getVchdt(),
                    header.getRefno(),
                    null);
            testData.setDocumentInformation(docInfo);

            StepSummaryBuffer.record("Pre-Check summary — voucher=" + testData.getExistingVoucherNo()
                    + ", found=Yes, release_status(before)='" + beforeReleaseStatusRef[0]
                    + "', owner_site='" + (site != null ? site.getName() : "—")
                    + "', cash/bank GL='" + (cashBankGl != null ? cashBankGl.getGlname() : "—") + "'");
        });

        // ── Report: Test Data ─────────────────────────────────────────────────────
        DbGatheringExtentLogger.log(testData);
        ApVoucherExtentDescriptionLogger.log(testData);

        // ── Step 2: Login ─────────────────────────────────────────────────────────────
        StepExecutor.step("Login to Application", () -> {
            UserCredential u = DriverManager.getCurrentUser();
            homeRef[0] = com.gsl.driver.SessionManager.ensureLoggedIn(u);
            ReportLogger.business("Logged in as: " + u.username());
        });

        // ── Step 3: Navigate ──────────────────────────────────────────────────────────
        StepExecutor.step("Open Finance → AP Voucher List", () -> listRef[0] =
                NavigationUtils.navigateToFinanceSideMenu(
                        homeRef[0],
                        MainMenuType.OPERATIONS,
                        OperationsMenuType.APVOUCHER,
                        ApVoucherListPage.class));

        // ── Step 4: Clear Filter + Select Document — consolidated via
        // VoucherListPageBase.selectDocumentForAction (see RevertGeneralVoucher's own equivalent
        // step for the rationale) ─────────────────────────────────────────────────
        StepExecutor.step("Select AP Voucher in List Page", () -> {
            DocumentSearchStrategy strategy = testData.getSearchStrategy() == null
                    ? DocumentSearchStrategy.EXACT_MATCH
                    : testData.getSearchStrategy();
            listRef[0].selectDocumentForAction(testData.getExistingVoucherNo(), strategy);
            ReportLogger.business("Filter cleared, list refreshed, selected voucher: "
                    + testData.getExistingVoucherNo());
        });

        // ── Step 5b: Untag Document Adjustments if Tagged (new) ──────────────────────
        // A Posted+tagged document cannot be Reverted until untagged — this must run BEFORE any
        // state-changing click (Actions→Revert), same precondition-ordering principle
        // DeleteApVoucher/EditApVoucher's own untag steps follow. Resolves this document's own
        // slcode (first finvchdet line — logs a warning if more than one distinct slcode exists
        // across lines, per the plan's explicit "use the first" convention) and actual amount
        // (camount or damount, whichever is non-zero), then delegates the fintag-both-directions
        // check + single untag call to the shared, domain-agnostic
        // DocumentAdjustmentFlow.untagIfNeeded (also used by DeleteApVoucher/EditApVoucher and,
        // for AR, DeleteArVoucher/EditArVoucher).
        VoucherEditFlowSteps.untagIfTaggedRegardlessOfStatus(
                testData.getExistingVoucherNo(), new java.util.ArrayList<>(), testData.getApiVoucherRef());

        // ── Step 6: DB Check — Release Status Immediately Before Revert ──────────────────
        StepExecutor.step(noDb ? "API Check — Release Status Immediately Before Revert" : "DB Check — Release Status Immediately Before Revert", () -> {
            if (noDb) {
                immediateBeforeReleaseStatusRef[0] = readStatusViaApi(testData);
                Assert.assertNotNull(immediateBeforeReleaseStatusRef[0], "[API-Check] voucher " + testData.getExistingVoucherNo()
                        + " disappeared before Revert could be clicked.");
                ReportLogger.business("Release status immediately before Revert action: '" + immediateBeforeReleaseStatusRef[0]
                        + "' for voucher: " + testData.getExistingVoucherNo());
                return;
            }
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
            StepSummaryBuffer.record("Immediate-before release_status='"
                    + immediateBeforeReleaseStatusRef[0] + "' for voucher " + testData.getExistingVoucherNo());
        });

        // ── Step 7: Resolve Document Context + Capture BEFORE Snapshot ──────────────────
        if (!noDb) StepExecutor.step("Resolve Document Context + Capture BEFORE Snapshot", () -> {
            contextRef[0] = new VoucherDocumentContextResolver()
                    .resolve(testData.getExistingVoucherNo(), VoucherDomain.AP);
            beforeSnapshotRef[0] = new SnapshotService(new Aud2SnapshotDao()).capture(contextRef[0]);
            ReportLogger.business("Document context resolved and BEFORE snapshot captured for voucher: "
                    + testData.getExistingVoucherNo());
        });

        // ── Step 8: Click Actions ─────────────────────────────────────────────────────
        StepExecutor.step("Click Actions", () -> {
            listRef[0].clickActions();
            ReportLogger.business("Actions dropdown opened");
        });

        // ── Step 9: Click Revert ───────────────────────────────────────────────────────
        StepExecutor.step("Click Revert", () -> {
            DocumentSearchStrategy strategy = testData.getSearchStrategy() == null
                    ? DocumentSearchStrategy.EXACT_MATCH
                    : testData.getSearchStrategy();
            listRef[0].clickRevert(testData.getExistingVoucherNo(), strategy);
            ReportLogger.business("Revert clicked");
        });

        // ── Step 10: Confirm Revert ─────────────────────────────────────────────────────
        StepExecutor.step("Confirm Revert", () -> {
            revertOutcomeRef[0] = listRef[0].confirmRevert();
            ReportLogger.pass("Revert confirmed for voucher: " + testData.getExistingVoucherNo()
                    + " — outcome: " + revertOutcomeRef[0]);

            if (testData.getExpectedRevertOutcome() != null) {
                Assert.assertEquals(revertOutcomeRef[0], testData.getExpectedRevertOutcome(),
                        "Expected Revert outcome=" + testData.getExpectedRevertOutcome()
                        + " for voucher " + testData.getExistingVoucherNo()
                        + " (deliberately claimed to exercise that outcome), but got "
                        + revertOutcomeRef[0] + " instead.");
                ReportLogger.pass("Negative outcome correctly triggered: " + revertOutcomeRef[0]
                        + " for voucher: " + testData.getExistingVoucherNo());
            }
            StepSummaryBuffer.record("Revert confirmed — voucher=" + testData.getExistingVoucherNo()
                    + ", outcome=" + revertOutcomeRef[0]
                    + (testData.getExpectedRevertOutcome() != null
                            ? " (Revert correctly blocked/handled — matches expected outcome)"
                            : ""));
        });

        // ── Step 11: Logout (deferred) ────────────────────────────────────────────────
        StepExecutor.step("Logout (deferred)", () ->
                ReportLogger.session("Logout deferred — session may be reused by a later test on this thread."));

        // ── Step 12: Capture AFTER Snapshot ──────────────────────────────────────────
        if (!noDb) StepExecutor.step("Capture AFTER Snapshot", () -> {
            afterSnapshotRef[0] = new SnapshotService(new Aud2SnapshotDao()).capture(contextRef[0]);
            ReportLogger.business("AFTER snapshot captured for voucher: " + testData.getExistingVoucherNo());
        });

        // ── Step 13: Database Validation ──────────────────────────────────────────────
        StepExecutor.step(noDb ? "API Validation" : "Database Validation", () -> {
            if (noDb) {
                if (revertOutcomeRef[0] == VoucherRevertOutcome.CANNOT_REVERT_BRS_DONE
                        || revertOutcomeRef[0] == VoucherRevertOutcome.CANNOT_REVERT_MONTH_CLOSED) {
                    ReportLogger.business("API validation: Revert was blocked as expected (" + revertOutcomeRef[0] + ").");
                    return;
                }
                String after = readStatusViaApi(testData);
                Assert.assertEquals(after, "U", "[API-Validate] voucher " + testData.getExistingVoucherNo()
                        + " should be Unposted after Revert (status before='" + immediateBeforeReleaseStatusRef[0] + "').");
                ReportLogger.pass("API validated — Revert completed: " + immediateBeforeReleaseStatusRef[0] + " -> U for voucher: "
                        + testData.getExistingVoucherNo());
                return;
            }
            if (!com.gsl.validation.ValidationSwitches.run(com.gsl.validation.ValidationSwitches.Area.DB)) {
                com.gsl.reports.ExtentLogger.skip("DB validation skipped — validation.db.enabled=no");
                return;
            }
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

            String expectedNewStatus;
            String outcomeNote;
            if (revertOutcomeRef[0] == VoucherRevertOutcome.CANNOT_REVERT_BRS_DONE) {
                expectedNewStatus = immediateBefore;
                outcomeNote = "BRS correctly blocked Revert";
            } else if (revertOutcomeRef[0] == VoucherRevertOutcome.CANNOT_REVERT_MONTH_CLOSED) {
                expectedNewStatus = immediateBefore;
                outcomeNote = "closed period correctly blocked Revert";
            } else {
                expectedNewStatus = "U";
                outcomeNote = "Revert completed: " + (immediateBefore == null ? "?" : immediateBefore) + " -> " + expectedNewStatus;

                if (immediateBefore != null && !immediateBefore.equals(before)) {
                    ReportLogger.warn("release_status changed between Step-1 precheck ('" + before
                            + "') and the immediate-before read ('" + immediateBefore + "') for voucher "
                            + testData.getExistingVoucherNo() + " — a concurrent process modified this "
                            + "document mid-test; this is expected to be benign as long as 'after' ended at 'U'.");
                }
            }

            ExpectedChangeSet changeSet = APVoucherRevertChangeSets.revert(
                    contextRef[0], beforeSnapshotRef[0], expectedNewStatus);

            VoucherSnapshotValidator validator = new VoucherSnapshotValidator();
            validator.validate("AP Voucher Revert — Snapshot Validation — [" + testData.getExistingVoucherNo() + "]",
                    contextRef[0], beforeSnapshotRef[0], afterSnapshotRef[0], changeSet);
            validator.assertAll();

            ReportLogger.pass("DB validated — " + outcomeNote + "; precheck-before='" + before
                    + "', immediate-before='" + immediateBefore + "', after='" + after
                    + "', expected='" + expectedNewStatus + "', dialog outcome=" + revertOutcomeRef[0]
                    + " for voucher: " + testData.getExistingVoucherNo());
        });
    }

    /** Release status ("P"/"U") of the row's voucher through the API; null when it cannot be read. */
    private static String readStatusViaApi(MergedFinanceApVoucherData testData) {
        com.gsl.testdata.dto.ApiVoucherRef ref = testData.getApiVoucherRef();
        Assert.assertNotNull(ref, "[API] no API voucher reference on the row — cannot read the voucher without the DB.");
        return com.gsl.api.core.ApiSessionFactory.withWebApiSession(session ->
                session.getApVoucherApiClient().fetchDocumentStatus(ref.getVchcode(), ref.getOuCode(), Long.parseLong(session.getUserId())));
    }
}
