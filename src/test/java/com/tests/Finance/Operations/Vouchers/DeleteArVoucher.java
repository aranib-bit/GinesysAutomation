package com.tests.Finance.Operations.Vouchers;

import com.gsl.annotations.ApiFlow;
import com.gsl.annotations.FrameworkAnnotation;
import com.gsl.annotations.TestCaseId;
import com.gsl.network.pattern.FinanceApiPattern;
import com.gsl.enums.AppOperation;
import com.gsl.enums.TestAuthor;
import com.gsl.enums.VoucherDomain;
import com.gsl.pages.enums.topmenucomponent.MainModuleType;
import com.gsl.pages.Finance.Operations.ArVoucherListPage;
import com.gsl.pages.HomePage;
import com.gsl.pages.LoginPage;
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
import com.gsl.validation.snapshot.DocumentContext;
import com.gsl.validation.snapshot.SnapshotService;
import com.gsl.validation.snapshot.TableRule;
import com.gsl.validation.snapshot.TableSnapshot;
import com.gsl.validation.snapshot.audit.AuditAssertion;
import com.gsl.validation.snapshot.finance.Aud2SnapshotDao;
import com.gsl.validation.snapshot.finance.VoucherDocumentContextResolver;
import com.tests.WebTest;
import org.testng.annotations.Test;

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
            dataProvider = "dataSupplier", dataProviderClass = MergedFinArVoucherDataSupplier.class)
    public void deleteArVoucher(MergedFinanceArVoucherData data) {

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
        StepExecutor.step("Login to Application", () -> {
            UserCredential u = DriverManager.getCurrentUser();
            homeRef[0] = new LoginPage().loginToApplication(u.username(), u.password());
            ReportLogger.business("Logged in as: " + u.username());
        });

        // ── Step 2: Navigate ──────────────────────────────────────────────────────
        StepExecutor.step("Open Finance → AR Voucher List", () -> {
            listRef[0] = NavigationUtils.navigateToFinanceSideMenu(
                    homeRef[0],
                    MainMenuType.OPERATIONS,
                    OperationsMenuType.ARVOUCHER,
                    ArVoucherListPage.class);
            ReportLogger.business("AR Voucher list page opened");
        });

        // ── Step 3: Search ────────────────────────────────────────────────────────
        StepExecutor.step("Search AR Voucher by Document Number", () -> {
            listRef[0].searchByVoucherNo(testData.getExistingVoucherNo());
            ReportLogger.business("Searched for voucher: " + testData.getExistingVoucherNo());
        });

        // ── Step 4: Unpost + untag if posted ──────────────────────────────────────
        StepExecutor.step("Unpost and Untag if Posted (release_status='P')", () -> {
            if ("P".equals(testData.getReleaseStatus())) {
                listRef[0].clickUnpost();
                // TODO: query fintag for this voucher (SELECT COUNT(*) FROM fintag WHERE vchcode = ?)
                //   If count > 0 → navigate to Document Adjustment and remove the tagging.
                //   Until the Document Adjustment page object is available, this step will
                //   fail at runtime if the voucher is tagged. Add the fintag query and
                //   DocumentAdjustmentPage.removeTagForVoucher(voucherNo) call here.
                ReportLogger.business("Voucher unposted (was release_status='P'). Fintag check: TODO.");
            } else {
                ReportLogger.business("Voucher status='" + testData.getReleaseStatus()
                        + "' — unpost not required");
            }
        });

        // ── Step 4b: Resolve Document Context + Capture pre-delete finvchmain state ───────────
        // Captured while the row still exists — needed both to resolve the audit table's business
        // key and, per the framework's confirmed design decision, as the ground truth "old" state
        // AuditAssertion.assertAuditDeleted compares the audit trail's DELETE row against.
        StepExecutor.step("Resolve Document Context + Capture Pre-Delete State", () -> {
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
                    .clickDeleteForVoucher()
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
        StepExecutor.step("Database Validation — Audit Trail", () -> {
            TableRule finvchmainRule = contextRef[0].getPersistenceMap().findByTableName("finvchmain");
            AuditAssertion.assertAuditDeleted(contextRef[0], auditDao, finvchmainRule, beforeFinvchmainRowRef[0]);
            ReportLogger.pass("Audit trail validated — DELETE recorded for voucher: "
                    + testData.getExistingVoucherNo());
        });
    }
}
