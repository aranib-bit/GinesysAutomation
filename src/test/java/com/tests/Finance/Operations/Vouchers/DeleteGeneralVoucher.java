package com.tests.Finance.Operations.Vouchers;

import com.gsl.annotations.ApiFlow;
import com.gsl.annotations.FrameworkAnnotation;
import com.gsl.annotations.TestCaseId;
import com.gsl.database.DatabaseManager;
import com.gsl.database.DbQueryBuffer;
import com.gsl.database.dao.finance.FinVchMainDao;
import com.gsl.driver.DriverManager;
import com.gsl.enums.AppOperation;
import com.gsl.enums.TestAuthor;
import com.gsl.enums.VoucherDomain;
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
import com.gsl.testdata.UserCredential;
import com.gsl.testdata.dto.MergedFinanceGeneralVoucherData;
import com.gsl.utils.Log;
import com.gsl.utils.NavigationUtils;
import com.gsl.utils.dataprovider.MergedFinGeneralVoucherDataSupplier;
import com.gsl.utils.dataprovider.fixture.finance.DocumentSearchStrategy;
import com.gsl.validation.snapshot.DocumentContext;
import com.gsl.validation.snapshot.ExpectedChangeSet;
import com.gsl.validation.snapshot.TableRule;
import com.gsl.validation.snapshot.TableSnapshot;
import com.gsl.validation.snapshot.SnapshotService;
import com.gsl.validation.snapshot.VoucherSnapshot;
import com.gsl.validation.snapshot.VoucherSnapshotValidator;
import com.gsl.validation.snapshot.audit.AuditAssertion;
import com.gsl.validation.snapshot.finance.Aud2SnapshotDao;
import com.gsl.validation.snapshot.finance.GeneralVoucherDeleteChangeSets;
import com.gsl.validation.snapshot.finance.VoucherDocumentContextResolver;
import com.tests.WebTest;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

/**
 * Deletes an existing General Voucher from the Finance → Operations → General Voucher list page
 * — mirrors {@code DeleteArVoucher}'s {@code StepExecutor.step(...)} structure and audit-trail
 * validation (see the "GV Revert picks documents in a now-closed accounting period,
 * + closed-period negative-test coverage" plan, Part C), adapted to GV's own conventions
 * (Clear Filter + Refresh, paginated {@code selectDocumentFast}, and the real, outcome-returning
 * {@code revertSelectedDocument()}/{@code confirmRevert()} un-post mechanism GV Edit/Revert
 * already use — unlike AR's un-outcome-checked {@code clickUnpost()}).
 *
 * <h3>Two scenario rows (DB mode)</h3>
 * <ol>
 *   <li><b>Positive</b> — an existing General Voucher in an OPEN accounting period: Unpost if
 *       Posted, delete, and verify the audit trail recorded the DELETE.</li>
 *   <li><b>Negative</b> — a General Voucher whose own accounting year/financial month is already
 *       CLOSED: the live app is expected to refuse even the "Unpost if Posted" prep step (the
 *       exact {@code CANNOT_REVERT_MONTH_CLOSED} rejection {@code RevertGeneralVoucher}'s own
 *       negative scenario confirms) — Delete is never actually attempted.</li>
 * </ol>
 *
 * <h3>Delete click/confirm — confirmed live 2026-09-01</h3>
 * {@code VoucherMainGridBase.clickDeleteForVoucher()}/{@code confirmDelete()} previously used
 * unconfirmed {@code //TODO-} placeholder locators (the same pre-existing gap that still blocks
 * {@code DeleteArVoucher} — see that class's own Javadoc) and were missing a checkbox
 * re-selection after Unpost/Revert clears it (see Step 6c below). Both are now wired to the real,
 * live-confirmed flow: {@code clickDeleteForVoucher()} reuses the same {@code clickGridAction}
 * mechanism {@code clickEdit()}/{@code clickRevert()} already prove out, and
 * {@code confirmDelete()} handles the real two-dialog Yes→Result/OK sequence (see
 * {@link com.gsl.enums.Finance.Operations.Common.DocumentMessageBoxMessage#DELETE_CONFIRMATION}/
 * {@link com.gsl.enums.Finance.Operations.Common.DocumentMessageBoxMessage#DELETE_SUCCESS_RESULT}).
 */
@ApiFlow(FinanceApiPattern.GENERAL_VOUCHER)
public class DeleteGeneralVoucher extends WebTest {

    @FrameworkAnnotation(value = TestAuthor.ARANI, module = MainModuleType.FINANCE, subModule = "FinanceGenVoucher", operation = AppOperation.DELETE)
    @TestCaseId("FIN-GV-DELETE")
    @Test(description = "Delete an existing General Voucher from the List Page",
            dataProvider = "FinanceGeneralVoucherData", dataProviderClass = MergedFinGeneralVoucherDataSupplier.class)
    public void deleteGeneralVoucher(UserCredential user, MergedFinanceGeneralVoucherData data) {

        Log.info("Delete General Voucher: {}", data.getExistingVoucherNo());
        Log.info("Release status: {}", data.getReleaseStatus());

        final MergedFinanceGeneralVoucherData testData = data;

        final HomePage[]              homeRef    = new HomePage[1];
        final GeneralVoucherListPage[] listRef    = new GeneralVoucherListPage[1];
        final DocumentContext[]        contextRef = new DocumentContext[1];
        final Map<String, Object>[]    beforeFinvchmainRowRef = new Map[1];
        final VoucherSnapshot[]        beforeSnapshotRef = new VoucherSnapshot[1];
        final VoucherSnapshot[]        afterSnapshotRef = new VoucherSnapshot[1];
        final GeneralVoucherRevertOutcome[] unpostOutcomeRef = new GeneralVoucherRevertOutcome[1];
        final String[]                 beforeReleaseStatusRef = new String[1];
        final String[]                 immediateBeforeReleaseStatusRef = new String[1];

        Aud2SnapshotDao auditDao = new Aud2SnapshotDao();

        DbGatheringExtentLogger.log(testData);
        GeneralVoucherExtentDescriptionLogger.log(testData);

        // ── Step 1: DB Pre-Check — Verify Document Exists & Release Status ───────────
        // Mirrors RevertGeneralVoucher's own Step 1 — same position, before Login. Data can
        // change between @DataSupplier time (when testData.getReleaseStatus() was captured) and
        // this test actually running; this live read is the true starting point, and Step 4b
        // below re-reads it again immediately before the state-changing click to catch any drift
        // in between (see the "GV Revert picks documents in a now-closed accounting period,
        // + closed-period negative-test coverage" plan, follow-up round 3).
        StepExecutor.step("DB Pre-Check — Verify Document Exists & Release Status", () -> {
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
                    + " — target document does not exist in DB; cannot proceed with Delete.");
            beforeReleaseStatusRef[0] = header.getReleaseStatus();
            ReportLogger.business("Captured release_status='" + beforeReleaseStatusRef[0]
                    + "' (before Delete) for voucher: " + testData.getExistingVoucherNo());
            com.gsl.reports.StepSummaryBuffer.record("Pre-Check summary — voucher=" + testData.getExistingVoucherNo()
                    + ", found=Yes, release_status(before)='" + beforeReleaseStatusRef[0] + "'");
        });

        // ── Step 2: Login ─────────────────────────────────────────────────────────
        StepExecutor.step("Login to Application", () -> {
            UserCredential u = DriverManager.getCurrentUser();
            homeRef[0] = com.gsl.driver.SessionManager.ensureLoggedIn(u);
            ReportLogger.business("Logged in as: " + u.username());
        });

        // ── Step 3: Navigate ──────────────────────────────────────────────────────
        // Business log + "Navigated: ..." summary are now recorded inside
        // NavigationUtils.navigateToFinanceSideMenu itself.
        StepExecutor.step("Open Finance → General Voucher List", () -> listRef[0] =
                NavigationUtils.navigateToFinanceSideMenu(
                        homeRef[0],
                        MainMenuType.OPERATIONS,
                        OperationsMenuType.CATEGORY,
                        GeneralVoucherListPage.class));

        // ── Step 4: Clear Filter + Select Document — consolidated via
        // VoucherListPageBase.selectDocumentForAction (see RevertGeneralVoucher's own equivalent
        // step for the rationale) ─────────────────────────────────────────────────
        StepExecutor.step("Select General Voucher in List Page", () -> {
            listRef[0].selectDocumentForAction(testData.getExistingVoucherNo(), DocumentSearchStrategy.EXACT_MATCH);
            ReportLogger.business("Filter cleared, list refreshed, selected voucher: "
                    + testData.getExistingVoucherNo());
        });

        // ── Step 5b: DB Check — Release Status Immediately Before Unpost ──────────────
        // A fresh, log-only read taken right before the state-changing click — Step 1's precheck
        // ran before login/navigation/selection, leaving a race window in which another
        // concurrent process can legitimately change the voucher's live release_status. This
        // narrower read is the true pre-action snapshot; it drives Step 6's actual branch below
        // (not testData.getReleaseStatus(), the stale @DataSupplier-time value), and its
        // divergence from Step 1's value (when it occurs) explains an otherwise-confusing later
        // mismatch instead of masquerading as a product defect. Mirrors RevertGeneralVoucher's
        // own Step 7 exactly.
        StepExecutor.step("DB Check — Release Status Immediately Before Unpost", () -> {
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
                    + " — target document disappeared before Unpost/Delete could be attempted.");
            immediateBeforeReleaseStatusRef[0] = header.getReleaseStatus();
            ReportLogger.business("Release status immediately before Unpost: '"
                    + immediateBeforeReleaseStatusRef[0] + "' for voucher: " + testData.getExistingVoucherNo());
            if (!immediateBeforeReleaseStatusRef[0].equals(beforeReleaseStatusRef[0])) {
                ReportLogger.warn("release_status changed between Step-1 precheck ('" + beforeReleaseStatusRef[0]
                        + "') and the immediate-before read ('" + immediateBeforeReleaseStatusRef[0] + "') for "
                        + "voucher " + testData.getExistingVoucherNo() + " — a concurrent process modified this "
                        + "document mid-test; this is expected to be benign as long as Delete still behaves "
                        + "correctly for whichever state it actually landed in.");
            }
            com.gsl.reports.StepSummaryBuffer.record("Immediate-before release_status='"
                    + immediateBeforeReleaseStatusRef[0] + "' for voucher " + testData.getExistingVoucherNo());
        });

        // ── Step 6: Unpost if Posted ──────────────────────────────────────────────
        // General Voucher has no standalone "Unpost" button — un-posting IS the Revert action
        // here, same as EditGeneralVoucher's own "Unpost if Posted" step — reuses the real,
        // outcome-returning revertSelectedDocument()/confirmRevert() machinery (unlike AR's
        // un-outcome-checked clickUnpost()), which is what lets the closed-period negative
        // scenario below assert a real, already-confirmed rejection. Branches on Step 5b's
        // immediate-before read, not the stale @DataSupplier-time testData.getReleaseStatus().
        StepExecutor.step("Unpost if Posted (release_status='P')", () -> {
            String resultingStatus;
            String actionTaken;
            if ("P".equals(immediateBeforeReleaseStatusRef[0])) {
                actionTaken = "UNPOST_ATTEMPTED";
                GeneralVoucherRevertOutcome outcome = listRef[0].revertSelectedDocument();
                unpostOutcomeRef[0] = outcome;
                if (outcome == GeneralVoucherRevertOutcome.CANNOT_REVERT_BRS_DONE) {
                    ReportLogger.warn("Voucher's line(s) are already bank-reconciled — Revert (un-post) was "
                            + "blocked; voucher remains Posted. Delete may behave differently against a "
                            + "still-Posted voucher.");
                    resultingStatus = "P";
                } else if (outcome == GeneralVoucherRevertOutcome.CANNOT_REVERT_MONTH_CLOSED) {
                    // The negative scenario's actual assertion point — see the early-return below.
                    ReportLogger.warn("Voucher's own accounting year/financial month is already closed — "
                            + "Revert (un-post) was blocked; voucher remains Posted.");
                    resultingStatus = "P";
                } else {
                    ReportLogger.business("Voucher unposted via Actions→Revert (was release_status='P'), outcome=" + outcome);
                    resultingStatus = "U";
                }
            } else {
                actionTaken = "NOT_REQUIRED";
                resultingStatus = immediateBeforeReleaseStatusRef[0];
                ReportLogger.business("Voucher status='" + immediateBeforeReleaseStatusRef[0] + "' — unpost not required");
            }
            // Blocked (still Posted despite an attempted unpost) is a genuinely negative outcome
            // for this step even though it doesn't itself fail the test (BRS/closed-period are
            // handled — either warned-and-continued, or asserted separately by the negative
            // scenario's own "Verify Negative Outcome" step right after this one) — cross, not
            // tick, so the summary visually flags it rather than reading as a routine success.
            boolean unpostOk = !("UNPOST_ATTEMPTED".equals(actionTaken) && "P".equals(resultingStatus));
            com.gsl.reports.StepSummaryBuffer.record("Unpost-if-Posted summary — before_status='"
                    + immediateBeforeReleaseStatusRef[0] + "', action=" + actionTaken
                    + ", outcome=" + (unpostOutcomeRef[0] == null ? "N/A" : unpostOutcomeRef[0])
                    + ", resulting_status='" + resultingStatus + "'", unpostOk);
        });

        // ── Step 6b: Negative-outcome branch (closed accounting period) ──────────────
        // A row deliberately claimed a candidate whose own accounting year/financial month is
        // already closed (GV_DELETE_NEGATIVE_MONTH_CLOSED) — Step 6 above is expected to hit that
        // exact rejection while trying to un-post it, and Delete is never attempted at all:
        // Steps 7-10 (document context, pre-delete snapshot, Delete click, audit validation) don't
        // apply. Mirrors EditGeneralVoucher's identical Step 4b.
        if (testData.getExpectedRevertOutcome() != null) {
            StepExecutor.step("Verify Negative Outcome (Unpost-if-Posted)", () -> {
                Assert.assertEquals(unpostOutcomeRef[0], testData.getExpectedRevertOutcome(),
                        "Expected \"Unpost if Posted\" outcome=" + testData.getExpectedRevertOutcome()
                        + " for voucher " + testData.getExistingVoucherNo()
                        + " (deliberately claimed to exercise that outcome), but got "
                        + unpostOutcomeRef[0] + " instead.");
                ReportLogger.pass("Negative outcome correctly triggered: " + unpostOutcomeRef[0]
                        + " for voucher: " + testData.getExistingVoucherNo());
            });
            return;
        }

        // ── Step 7: Resolve Document Context + Capture Pre-Delete State ──────────────
        // Captured while the row still exists — needed both to resolve the audit table's business
        // key and, per the framework's confirmed design decision, as the ground truth "old" state
        // AuditAssertion.assertAuditDeleted compares the audit trail's DELETE row against. Moved
        // ahead of the re-select step below (was previously between it and Step 8) — this is
        // DB-only and doesn't touch the UI, so reordering it is safe, and it closes the gap that
        // used to sit between confirming selection and opening Actions.
        StepExecutor.step("Resolve Document Context + Capture Pre-Delete State", () -> {
            contextRef[0] = new VoucherDocumentContextResolver().resolve(testData.getExistingVoucherNo(), VoucherDomain.GV);
            // Round 3 Item K: captures the FULL persistence-map snapshot (finvchmain/finvchdet/
            // finpost/fincosttag), not just finvchmain — needed so the new "Database Validation —
            // Full Table Deletion" step (after Delete) can confirm the related tables' rows are
            // ALSO actually gone, not just finvchmain's. finvchmain's own row is still pulled out
            // below for the existing Step 10 audit-trail comparison, unchanged.
            beforeSnapshotRef[0] = new SnapshotService(auditDao).capture(contextRef[0]);
            TableSnapshot finvchmainSnapshot = beforeSnapshotRef[0].getTable("finvchmain");
            if (finvchmainSnapshot == null || finvchmainSnapshot.getRows().isEmpty()) {
                throw new IllegalStateException("[DeleteGeneralVoucher] finvchmain row not found for scheme_docno="
                        + testData.getExistingVoucherNo() + " immediately before delete.");
            }
            beforeFinvchmainRowRef[0] = finvchmainSnapshot.getRows().get(0);
            ReportLogger.business("Captured pre-delete finvchmain state for audit comparison: "
                    + testData.getExistingVoucherNo());
            recordPreDeleteStateSummary(beforeFinvchmainRowRef[0]);
            // Item U: Delete previously had NO audit-state visibility at all — both sets are
            // already resolved on contextRef[0], rendered in the same dedicated shape used
            // elsewhere for this check, ahead of the state-changing Delete click.
            new com.gsl.validation.snapshot.finance.AuditStateSummary(
                    contextRef[0].getAuditActiveTables(), contextRef[0].getAuditTriggerDisabledTables())
                    .logTable("BEFORE Delete — Audit State");
        });

        // ── Step 8: Re-select Voucher + Click Actions + Delete + Confirm ──────────────────────
        // Re-select (checkbox selection is lost after Unpost/Revert — and, confirmed live in run
        // 20260903_100559's DOM captures, can ALSO be lost with no revert at all, apparently via
        // the exact-match filter's own asynchronous settle behavior silently clearing a
        // just-registered selection) is now folded into THIS step, immediately before the Actions
        // click, rather than a separate preceding step — Round 9 already closed the DB-only gap
        // that used to sit between them (see prior history), and Round 10 (RC-11) confirmed even a
        // zero-gap re-select-then-click sequence can still race the async reload above. So
        // clickDeleteForVoucher(documentNumber, strategy) below both re-selects immediately before
        // clicking Actions AND retries the whole select+click-Actions sequence once more if the
        // first attempt still finds Delete disabled — see VoucherMainGridBase.clickCheckbox's and
        // VoucherListPageBase.clickDeleteForVoucher(String, DocumentSearchStrategy)'s Javadoc for
        // the confirmed root cause this closes. selectDocumentFast's checkbox click is idempotent
        // (VoucherMainGridBase.clickCheckbox skips the click if already checked), so calling it
        // here is always safe — a no-op when Step 6 didn't touch the UI or the selection survived.
        StepExecutor.step("Re-select and Click Delete and Confirm", () -> {
            listRef[0]
                    .clickDeleteForVoucher(testData.getExistingVoucherNo(), DocumentSearchStrategy.EXACT_MATCH)
                    .confirmDelete();
            ReportLogger.pass("Deletion confirmed for voucher: " + testData.getExistingVoucherNo());
        });

        // ── Step 9: Logout (deferred — session may be reused by a later test on this thread;
        // real logout+driver-quit happens via RecoveryManager or end-of-block cleanup only) ────
        StepExecutor.step("Logout (deferred)", () ->
                ReportLogger.session("Logout deferred — session may be reused by a later test on this thread."));

        // ── Step 9b: Database Validation — Live Row Removed ──────────────────────────
        // The audit-trail check below only confirms aud2_finvchmain recorded a DELETE row
        // matching the pre-delete state — it does not independently confirm the live finvchmain
        // row is actually gone (previously assumed "gone by construction" rather than asserted).
        // Closes that gap explicitly: the UI's "Deleted Successfully" Result dialog is a claim,
        // not proof, that the live row was actually removed.
        StepExecutor.step("Database Validation — Live Row Removed", () -> {
            DbQueryBuffer.start();
            java.util.Optional<FinVchMain> stillPresent;
            try {
                stillPresent = DatabaseManager.getJdbi().onDemand(FinVchMainDao.class)
                        .findBySchemeDocno(testData.getExistingVoucherNo());
            } finally {
                List<String> queries = DbQueryBuffer.flush();
                queries.forEach(ReportLogger::db);
            }
            Assert.assertTrue(stillPresent.isEmpty(),
                    "[DeleteGeneralVoucher] finvchmain row for scheme_docno=" + testData.getExistingVoucherNo()
                    + " still exists after Delete was confirmed in the UI — the live row was not actually removed.");
            ReportLogger.business("Confirmed finvchmain row removed for voucher: " + testData.getExistingVoucherNo());
            com.gsl.reports.StepSummaryBuffer.record("Live row removal confirmed — finvchmain row for voucher "
                    + testData.getExistingVoucherNo() + " no longer present");
        });

        // ── Step 10: Database Validation — Audit Trail ─────────────────────────────
        // finvchmain's row is confirmed gone from the live table by Step 9b above — this
        // validates that aud2_finvchmain recorded a DELETE row whose captured state matches what
        // the record held immediately before deletion.
        StepExecutor.step("Database Validation — Audit Trail", () -> {
            TableRule finvchmainRule = contextRef[0].getPersistenceMap().findByTableName("finvchmain");
            AuditAssertion.assertAuditDeleted(contextRef[0], auditDao, finvchmainRule, beforeFinvchmainRowRef[0]);
            ReportLogger.pass("Audit trail validated — DELETE recorded for voucher: "
                    + testData.getExistingVoucherNo());
            // Point 9 (Delete's flagged weakest step): AuditAssertion itself logs nothing of its
            // own — enrich the closing line with a small table using only fields already in scope
            // (minimal fix — option (a) from the plan; option (b), routing through the full
            // VoucherSnapshotValidator/ExpectedChangeSet engine like Revert, is flagged as a
            // longer-term follow-up, not done here).
            recordAuditTrailSummary(finvchmainRule, beforeFinvchmainRowRef[0]);
        });

        // ── Step 11: Capture AFTER Snapshot ────────────────────────────────────────
        // Round 3 Item K. Step 9b already confirmed finvchmain itself is gone via a direct DAO
        // read; this captures the SAME full persistence-map shape Step 7 captured before Delete,
        // so Step 12 below can confirm finvchdet/finpost/fincosttag are ALSO actually gone — the
        // gap recordAuditTrailSummary's own Javadoc explicitly flagged as "option (b) ... a
        // longer-term follow-up, not done here," never implemented until now.
        StepExecutor.step("Capture AFTER Snapshot", () -> {
            afterSnapshotRef[0] = new SnapshotService(auditDao).capture(contextRef[0]);
            ReportLogger.business("AFTER snapshot captured for voucher: " + testData.getExistingVoucherNo());
            recordAfterSnapshotSummary(afterSnapshotRef[0]);
        });

        // ── Step 12: Database Validation — Full Table Deletion ────────────────────
        // Runs ALONGSIDE Step 9b/10 (not replacing them) — same "complementary coverage"
        // relationship GeneralVoucherCreateChangeSets' own Javadoc documents for Create's Level
        // 1-4 checks vs. its snapshot validation. Confirms finvchdet/finpost/fincosttag (using
        // "the same relation" every other GV changeset already scopes a document's own rows by —
        // vchcode/entcode/entcode, all resolved via VoucherDocumentContextResolver/SnapshotService's
        // fk-scoped capture) were ALSO actually removed, not just finvchmain.
        StepExecutor.step("Database Validation — Full Table Deletion (finvchdet/finpost/fincosttag)", () -> {
            ExpectedChangeSet changeSet = GeneralVoucherDeleteChangeSets.delete(contextRef[0]);

            VoucherSnapshotValidator validator = new VoucherSnapshotValidator();
            validator.validate("General Voucher Delete — Snapshot Validation — [" + testData.getExistingVoucherNo() + "]",
                    contextRef[0], beforeSnapshotRef[0], afterSnapshotRef[0], changeSet);
            validator.assertAll();

            ReportLogger.pass("DB validated — finvchdet/finpost/fincosttag rows confirmed removed for voucher: "
                    + testData.getExistingVoucherNo());
        });
    }

    /**
     * Point 9 summary helper — "Resolve Document Context + Capture Pre-Delete State": shows the
     * already-captured pre-delete {@code finvchmain} row (release_status/vchdt/nartext) instead of
     * leaving it invisible in the report despite being in scope. One line per field (not a
     * two-column table) — matches the report's established "Level 1 — finvchmain field checks"
     * house style for readability.
     */
    private static void recordPreDeleteStateSummary(Map<String, Object> beforeRow) {
        if (beforeRow == null) return;
        String[] fields = {"release_status", "vchdt", "nartext", "refno"};
        for (String field : fields) {
            Object value = com.gsl.validation.snapshot.DbValueNormalizer.getIgnoreCase(beforeRow, field);
            com.gsl.reports.StepSummaryBuffer.record("Pre-Delete State (finvchmain) — " + field + ": "
                    + com.gsl.validation.snapshot.DbValueNormalizer.displayString(value));
        }
    }

    /**
     * Point 9 summary helper — "Database Validation — Audit Trail": {@link AuditAssertion} itself
     * logs nothing to the report, so the step's only content used to be one generic closing line.
     * Builds the business key the same way {@code AuditAssertion} does internally (duplicated
     * here rather than exposing a new method on it — see the plan's "minimal fix" recommendation)
     * and reports it alongside the audit table/operation/result, since {@code assertAuditDeleted}
     * having returned without throwing IS the "Result" (a genuine failure would have already
     * failed this step before reaching this line). One line per field (not a table) — same
     * readability reason as {@link #recordPreDeleteStateSummary}.
     */
    private static void recordAuditTrailSummary(TableRule tableRule, Map<String, Object> beforeRow) {
        String auditTable = "aud2_" + tableRule.getTableName();
        List<String> keyColumns = tableRule.getBusinessKeyColumns();
        StringBuilder businessKey = new StringBuilder();
        if (keyColumns != null) {
            for (String col : keyColumns) {
                if (businessKey.length() > 0) businessKey.append(", ");
                Object raw = com.gsl.validation.snapshot.DbValueNormalizer.getIgnoreCase(beforeRow, col);
                businessKey.append(col).append('=')
                        .append(com.gsl.validation.snapshot.DbValueNormalizer.displayString(raw));
            }
        }
        com.gsl.reports.StepSummaryBuffer.record("Audit Table: " + auditTable);
        com.gsl.reports.StepSummaryBuffer.record("Business Key: " + businessKey);
        com.gsl.reports.StepSummaryBuffer.record("Operation: DELETE");
        com.gsl.reports.StepSummaryBuffer.record("Result: Confirmed — aud2 DELETE row matches pre-delete state");
    }

    /**
     * Round 3 Item K summary helper — mirrors {@code RevertGeneralVoucher.recordSnapshotTableSummary}'s
     * shape: one line per table naming its post-delete row count, so a reader can see at a glance
     * that finvchdet/finpost/fincosttag all dropped to 0 rows (not just finvchmain).
     */
    private static void recordAfterSnapshotSummary(VoucherSnapshot snapshot) {
        if (snapshot == null || snapshot.getTables() == null || snapshot.getTables().isEmpty()) {
            com.gsl.reports.StepSummaryBuffer.record("AFTER Snapshot: no tables captured");
            return;
        }
        for (var entry : snapshot.getTables().entrySet()) {
            com.gsl.reports.StepSummaryBuffer.record(
                    "AFTER Snapshot — " + entry.getKey() + ": " + entry.getValue().rowCount() + " row(s)");
        }
    }
}
