package com.tests.Finance.Operations.Vouchers;

import com.gsl.annotations.ApiFlow;
import com.gsl.annotations.FrameworkAnnotation;
import com.gsl.annotations.TestCaseId;
import com.gsl.network.pattern.FinanceApiPattern;
import com.gsl.enums.AppOperation;
import com.gsl.enums.TestAuthor;
import com.gsl.enums.VoucherDomain;
import com.gsl.database.DatabaseManager;
import com.gsl.database.dao.finance.FinVchDetDao;
import com.gsl.database.dao.finance.FinVchMainDao;
import com.gsl.flow.Finance.Operations.Common.DocumentAdjustmentFlow;
import com.gsl.flow.Finance.Operations.Common.VoucherEditFlowSteps;
import com.gsl.model.finance.FinVchDet;
import com.gsl.model.finance.FinVchMain;
import com.gsl.pages.enums.topmenucomponent.MainModuleType;
import com.gsl.pages.Finance.Operations.ArVoucherListPage;
import com.gsl.pages.HomePage;
import com.gsl.pages.enums.Finance.MainMenuType;
import com.gsl.pages.enums.Finance.Operations.OperationsMenuType;
import com.gsl.driver.DriverManager;
import com.gsl.reports.ReportLogger;
import com.gsl.reports.StepExecutor;
import com.gsl.testdata.UserCredential;
import com.gsl.testdata.dto.MergedFinanceArVoucherData;
import com.gsl.utils.Log;
import com.gsl.utils.NavigationUtils;
import com.gsl.utils.dataprovider.MergedFinArVoucherDataSupplier;
import com.gsl.utils.dataprovider.fixture.finance.DocumentSearchStrategy;
import org.testng.Assert;
import com.gsl.utils.dataprovider.source.finance.common.AccountingPeriodResolver;
import com.gsl.validation.snapshot.DocumentContext;
import com.gsl.validation.snapshot.SnapshotService;
import com.gsl.validation.snapshot.TableRule;
import com.gsl.validation.snapshot.TableSnapshot;
import com.gsl.validation.snapshot.audit.AuditAssertion;
import com.gsl.validation.snapshot.finance.Aud2SnapshotDao;
import com.gsl.validation.snapshot.finance.VoucherDocumentContextResolver;
import com.tests.WebTest;
import org.jdbi.v3.core.Jdbi;
import org.testng.annotations.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Deletes an existing AR Voucher from the Finance → Operations → AR Voucher list page.
 *
 * <h3>Pre-conditions</h3>
 * <ul>
 *   <li>{@code testdata.source=DB} in {@code config.properties}</li>
 *   <li>At least one row in {@code finvchmain} with
 *       {@code vchtype='R' AND release_status IN ('N','U','P')}</li>
 * </ul>
 *
 * <h3>P-status handling</h3>
 * When {@code releaseStatus='P'}, the test unposted the voucher first.
 * If the voucher has active tagging in {@code fintag}, Document Adjustment
 * must remove the tag before deleting — that step is marked TODO until the
 * Document Adjustment page object is available.
 */
@ApiFlow(FinanceApiPattern.AR_VOUCHER)
public class DeleteArVoucher extends WebTest {

    @FrameworkAnnotation(value = TestAuthor.ARANI, module = MainModuleType.FINANCE, subModule = "FinanceARVoucher", operation = AppOperation.DELETE)
    @TestCaseId("FIN-AR-DELETE")
    @Test(description = "Delete an existing AR Voucher from the Finance UI",
            dataProvider = "FinanceArVoucherData", dataProviderClass = MergedFinArVoucherDataSupplier.class)
    public void deleteArVoucher(UserCredential user, MergedFinanceArVoucherData data) {

        Log.info("Delete AR Voucher: {}", data.getExistingVoucherNo());
        Log.info("Release status: {}", data.getReleaseStatus());

        final MergedFinanceArVoucherData testData = data;

        // Cross-step object holders
        final HomePage[]          homeRef    = new HomePage[1];
        final ArVoucherListPage[] listRef    = new ArVoucherListPage[1];
        final DocumentContext[]   contextRef = new DocumentContext[1];
        final Map<String, Object>[] beforeFinvchmainRowRef = new Map[1];

        Aud2SnapshotDao auditDao = new Aud2SnapshotDao();

        // ── Step 1: Login ─────────────────────────────────────────────────────────
        // Was new LoginPage().loginToApplication(...) — a session-unaware fresh login that only
        // works when the browser happens to already be sitting on the actual login page. Confirmed
        // via AP's identical DeleteApVoucher.java hitting this exact bug live (run 20260927_190439,
        // FIN-AP-DELETE-01/02, mirrored from this class): a previous test on the same thread had
        // already left the browser logged in elsewhere (no username field to fill there).
        // SessionManager.ensureLoggedIn(u) is the session-reuse-aware helper every OTHER voucher
        // test class (Revert/Edit/GV Delete) already uses.
        StepExecutor.step("Login to Application", () -> {
            UserCredential u = DriverManager.getCurrentUser();
            homeRef[0] = com.gsl.driver.SessionManager.ensureLoggedIn(u);
            ReportLogger.business("Logged in as: " + u.username());
        });

        // ── Step 2: Navigate ──────────────────────────────────────────────────────
        // Business log + "Navigated: ..." summary are now recorded inside
        // NavigationUtils.navigateToFinanceSideMenu itself.
        StepExecutor.step("Open Finance → AR Voucher List", () -> listRef[0] =
                NavigationUtils.navigateToFinanceSideMenu(
                        homeRef[0],
                        MainMenuType.OPERATIONS,
                        OperationsMenuType.ARVOUCHER,
                        ArVoucherListPage.class));

        // ── Step 3: Clear Filter + Select Document — was searchByVoucherNo(...), which is
        // exact-match-only with no clear-filter step and no pagination fallback (same exposure
        // Round 9/10 confirmed live and fixed for DeleteApVoucher — see that class's own
        // equivalent step for the evidence). Now uses the shared common function
        // (VoucherListPageBase.selectDocumentForAction) every other Revert/Delete/Edit test class
        // uses. Its own follow-on action-click step (clickDeleteForVoucher()/clickUnpost(), both
        // still no-arg/no-retry) is unchanged — a separate, already-flagged, already-deferred gap.
        StepExecutor.step("Select AR Voucher in List Page", () -> {
            listRef[0].selectDocumentForAction(testData.getExistingVoucherNo(), DocumentSearchStrategy.EXACT_MATCH);
            ReportLogger.business("Filter cleared, list refreshed, selected voucher: "
                    + testData.getExistingVoucherNo());
        });

        // ── Step 4: Unpost + untag if posted ──────────────────────────────────────
        // Untag replaces the previous // TODO: query fintag... stub — resolves vchcode/slcode/
        // actualAmount via a fresh FinVchMainDao/FinVchDetDao read keyed on
        // testData.getExistingVoucherNo(), then delegates the fintag-both-directions check +
        // single Document Adjustment API call to the shared, domain-agnostic
        // DocumentAdjustmentFlow.untagIfNeeded (same helper AP's RevertApVoucher/DeleteApVoucher/
        // EditApVoucher use). AR's existing UI-based unpost (clickUnpost()) is unchanged — only
        // this untag step is new.
        // Untag BEFORE unpost (a Posted+tagged document cannot be un-posted until its fintag rows
        // are cleared — the previous order here unposted first). Unpost is the Actions → Revert
        // UI action (AR has no standalone Unpost button; the old //TODO-unpost-button placeholder
        // never existed), via the dialogs AR shares with AP (ArApVoucherDialogs).
        // db.available=no: the pre-delete finvchmain capture and the audit-trail validation need the database, so they are skipped and the
        // voucher is instead confirmed gone through the API (rootview). The untag step uses the voucher the API data source created.
        final boolean noDb = !com.gsl.database.DbAvailability.isAvailable();
        VoucherEditFlowSteps.untagIfTagged(
                testData.getExistingVoucherNo(), testData.getReleaseStatus(), new java.util.ArrayList<>(), testData.getApiVoucherRef());
        StepExecutor.step("Unpost if Posted (release_status='P') — Actions → Revert", () -> {
            if ("P".equals(testData.getReleaseStatus())) {
                listRef[0].selectDocumentForAction(testData.getExistingVoucherNo(), DocumentSearchStrategy.EXACT_MATCH);
                com.gsl.pages.enums.Finance.Operations.Common.VoucherRevertOutcome outcome =
                        listRef[0].revertSelectedDocument();
                Assert.assertEquals(outcome,
                        com.gsl.pages.enums.Finance.Operations.Common.VoucherRevertOutcome.UNPOSTED,
                        "[Unpost] Expected the Posted AR voucher to be un-posted by Revert");
                ReportLogger.business("Voucher unposted via Revert (was release_status='P') for: "
                        + testData.getExistingVoucherNo());
            } else {
                ReportLogger.business("Voucher status=" + testData.getReleaseStatus() + " — unpost not required");
            }
        });

        // ── Step 4b: Resolve Document Context + Capture pre-delete finvchmain state ───────────
        // Captured while the row still exists — needed both to resolve the audit table's business
        // key and, per the framework's confirmed design decision, as the ground truth "old" state
        // AuditAssertion.assertAuditDeleted compares the audit trail's DELETE row against.
        if (!noDb) StepExecutor.step("Resolve Document Context + Capture Pre-Delete State", () -> {
            contextRef[0] = new VoucherDocumentContextResolver().resolve(testData.getExistingVoucherNo(), VoucherDomain.AR);
            TableSnapshot finvchmainSnapshot = new SnapshotService(auditDao).capture(contextRef[0]).getTable("finvchmain");
            if (finvchmainSnapshot == null || finvchmainSnapshot.getRows().isEmpty()) {
                throw new IllegalStateException("[DeleteArVoucher] finvchmain row not found for scheme_docno="
                        + testData.getExistingVoucherNo() + " immediately before delete.");
            }
            beforeFinvchmainRowRef[0] = finvchmainSnapshot.getRows().get(0);
            ReportLogger.business("Captured pre-delete finvchmain state for audit comparison: "
                    + testData.getExistingVoucherNo());
        });

        // ── Step 5: Delete and Confirm ────────────────────────────────────────────
        StepExecutor.step("Click Delete and Confirm", () -> {
            listRef[0]
                    .clickDeleteForVoucher(testData.getExistingVoucherNo(), DocumentSearchStrategy.EXACT_MATCH)
                    .confirmDelete();
            ReportLogger.pass("Deletion confirmed for voucher: " + testData.getExistingVoucherNo());
        });

        // ── Step 6: Logout (deferred — session may be reused by a later test on this thread;
        // real logout+driver-quit happens via RecoveryManager or end-of-block cleanup only) ────
        StepExecutor.step("Logout (deferred)", () ->
                ReportLogger.session("Logout deferred — session may be reused by a later test on this thread."));

        // ── Step 7: Database Validation ───────────────────────────────────────────
        // Real audit-trail assertion (replaces the old "TODO" stub): finvchmain's row is gone from
        // the live table by construction (the delete already happened above) — what this actually
        // validates is that aud2_finvchmain recorded a DELETE row whose captured state matches what
        // the record held immediately before deletion, excluding aud2_active_tab_col's housekeeping
        // columns for this table.
        if (noDb) {
            StepExecutor.step("API Post-Check — voucher no longer exists", () -> {
                com.gsl.testdata.dto.ApiVoucherRef ref = testData.getApiVoucherRef();
                Assert.assertNotNull(ref, "[API-PostCheck] no API voucher reference on the row; cannot confirm the delete without the DB.");
                String status = com.gsl.api.core.ApiSessionFactory.withWebApiSession(session ->
                        session.getArVoucherApiClient().fetchDocumentStatus(ref.getVchcode(), ref.getOuCode(), Long.parseLong(session.getUserId())));
                Assert.assertNull(status, "[API-PostCheck] voucher " + testData.getExistingVoucherNo()
                        + " is still readable through the API (status='" + status + "') after Delete.");
                ReportLogger.pass("API validated — voucher " + testData.getExistingVoucherNo() + " no longer exists.");
            });
            ReportLogger.business("Audit-trail validation skipped — db.available=no.");
            return;
        }
        StepExecutor.step("Database Validation — Audit Trail", () -> {
            TableRule finvchmainRule = contextRef[0].getPersistenceMap().findByTableName("finvchmain");
            AuditAssertion.assertAuditDeleted(contextRef[0], auditDao, finvchmainRule, beforeFinvchmainRowRef[0]);
            ReportLogger.pass("Audit trail validated — DELETE recorded for voucher: "
                    + testData.getExistingVoucherNo());
        });
    }
}
